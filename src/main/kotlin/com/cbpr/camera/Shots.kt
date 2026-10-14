package com.cbpr.camera

import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class Point(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Point) = Point(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Point) = Point(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Point(x * s, y * s, z * s)
    infix fun dot(o: Point) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Point) = Point(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(x * x + y * y + z * z)
}

data class Pose(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Double,
    val pitch: Double,
    val fov: Double = 0.0,
    val roll: Double = 0.0,
)

object Shots {

    const val SWITCH_FRONT_ANGLE_DEGREES = 60.0
    const val SWITCH_DISTANCE = 3.4
    const val SWITCH_HEIGHT = 1.0

    const val SWITCH_LOOK_TOWARD_OTHER = 0.35

    const val SWITCH_SPAN_FRAMING = 0.7

    const val SWITCH_PREFER_RIGHT = true

    const val SWITCH_START_DISTANCE_SCALE = 1.25

    const val SWITCH_PLAYER_CLEAR = 2.5

    const val DYNAMAX_ARRIVE_AT = 0.45
    private const val COMMAND_BACK = 3.2
    private const val COMMAND_UP = 1.3
    private const val COMMAND_SHOULDER = 0.9
    private const val MENU_BACK = 0.8
    private const val MENU_BACK_PER_SIZE = 0.2
    private const val MENU_SIDE = 1.0
    private const val MENU_SIDE_PER_SIZE = 0.9
    private const val MENU_UP = 0.8
    private const val MENU_UP_PER_SIZE = 0.25
    private const val MENU_LOOK_TOWARD_OTHER = 0.7
    private const val MENU_LOOK_UP = 0.25

    const val MENU_HOVER_CLOSER = 0.28
    const val MENU_HOVER_LOOK_TOWARD_OTHER = 0.82
    private const val SWAY_YAW_DEGREES = 0.6
    private const val SWAY_PITCH_DEGREES = 0.4
    private const val WIDE_MIN_BACK = 4.0
    private const val WIDE_UP = 3.0
    private const val IMPACT_MIN_BACK = 2.2
    private const val IMPACT_SPAN_FACTOR = 0.5
    private const val IMPACT_UP = 1.0
    private const val IMPACT_LOOK_TOWARD_ATTACKER = 0.25
    private const val PULL_START_DISTANCE = 2.4
    private const val PULL_END_DISTANCE = 5.2
    private const val PULL_START_HEIGHT = 0.8
    private const val PULL_END_HEIGHT = 2.4
    private const val PULL_OFFSET_DEGREES = 20.0
    private const val SWOOP_START_DEGREES = 70.0
    private const val SWOOP_START_DISTANCE = 1.8
    private const val SWOOP_END_DISTANCE = 3.4
    private const val SWOOP_START_HEIGHT = 0.3
    private const val SWOOP_END_HEIGHT = 1.5
    private const val SWOOP_LOOK_UP = 0.3
    private const val ORBIT_START_DEGREES = 20.0
    private const val ORBIT_DISTANCE = 3.4
    private const val ORBIT_START_HEIGHT = 1.0
    private const val ORBIT_END_HEIGHT = 1.8
    private const val ORBIT_LOOK_UP = 0.1
    private const val CAPTURE_DEGREES = 50.0
    private const val CAPTURE_START_DISTANCE = 3.2
    private const val CAPTURE_END_DISTANCE = 2.3
    private const val CAPTURE_HEIGHT = 0.5
    private const val DYNAMAX_FRAMING = 1.4
    private const val DYNAMAX_MIN_GIANT = 2.0
    private const val DYNAMAX_MIN_TOP = 16.0
    private const val DYNAMAX_BACK_MARGIN = 3.0
    private const val DYNAMAX_MAX_BACK = 16.0
    private const val DYNAMAX_HOLD_TOP = 0.2
    private const val DYNAMAX_ARRIVE = DYNAMAX_ARRIVE_AT
    private const val DYNAMAX_PUSH = 1.5
    private const val DYNAMAX_SPIN_DEGREES = 40.0

    const val DYNAMAX_ORBIT_DEGREES = 360.0

    const val DYNAMAX_ORBIT_RADIUS_SCALE = 1.1

    fun switchSpot(subject: Point, other: Point, rightSide: Boolean, distance: Double): Point {
        val (fx, fz) = horizontalDirection(subject, other)
        val turn = Math.toRadians(SWITCH_FRONT_ANGLE_DEGREES) * if (rightSide) 1.0 else -1.0

        val cx = fx * cos(turn) + (-fz) * sin(turn)
        val cz = fz * cos(turn) + fx * sin(turn)
        return Point(subject.x + cx * distance, subject.y + SWITCH_HEIGHT, subject.z + cz * distance)
    }

    fun switchDistance(subject: Point, other: Point): Double =
        max(SWITCH_DISTANCE, SWITCH_SPAN_FRAMING * hypot(other.x - subject.x, other.z - subject.z))

    fun switchSide(preferRight: Boolean, subject: Point, other: Point, trainer: Point?): Boolean {
        if (trainer == null) return preferRight
        fun gap(rightSide: Boolean): Double {
            val spot = switchSpot(subject, other, rightSide, switchDistance(subject, other))
            return hypot(spot.x - trainer.x, spot.z - trainer.z)
        }
        val preferred = gap(preferRight)
        return if (preferred < SWITCH_PLAYER_CLEAR && gap(!preferRight) > preferred) !preferRight else preferRight
    }

    fun switchFront(subject: Point, other: Point, rightSide: Boolean, progress: Double): Pose {
        val eased = smoothStep(progress)
        val distance = switchDistance(subject, other) * lerp(SWITCH_START_DISTANCE_SCALE, 1.0, eased)
        val from = switchSpot(subject, other, rightSide, distance)
        val look = Point(
            subject.x + (other.x - subject.x) * SWITCH_LOOK_TOWARD_OTHER,
            subject.y + (other.y - subject.y) * SWITCH_LOOK_TOWARD_OTHER,
            subject.z + (other.z - subject.z) * SWITCH_LOOK_TOWARD_OTHER,
        )
        return lookAt(from, look)
    }

    fun lookAt(from: Point, to: Point): Pose {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val yaw = Math.toDegrees(atan2(-dx, dz))
        val pitch = -Math.toDegrees(atan2(dy, hypot(dx, dz)))
        return Pose(from.x, from.y, from.z, yaw, pitch)
    }

    fun wide(mon: Point, foe: Point): Pose {
        val mid = Point((mon.x + foe.x) / 2, (mon.y + foe.y) / 2, (mon.z + foe.z) / 2)
        val (dirX, dirZ) = horizontalDirection(mon, foe)
        val span = hypot(foe.x - mon.x, foe.z - mon.z)
        val side = WIDE_MIN_BACK + span * 0.45
        val from = Point(mid.x - dirZ * side, mid.y + WIDE_UP + span * 0.25, mid.z + dirX * side)
        return lookAt(from, mid)
    }

    fun overview(mon: Point, foe: Point, spread: Double): Pose {
        val mid = Point((mon.x + foe.x) / 2, (mon.y + foe.y) / 2, (mon.z + foe.z) / 2)
        val (dirX, dirZ) = horizontalDirection(mon, foe)
        val reach = hypot(foe.x - mon.x, foe.z - mon.z) + spread
        val back = OVERVIEW_BACK + reach * OVERVIEW_BACK_PER_REACH
        val from = Point(mid.x - dirX * back, mid.y + OVERVIEW_UP + reach * OVERVIEW_UP_PER_REACH, mid.z - dirZ * back)
        return lookAt(from, mid)
    }

    private const val OVERVIEW_BACK = 2.5
    private const val OVERVIEW_BACK_PER_REACH = 0.4
    private const val OVERVIEW_UP = 3.5
    private const val OVERVIEW_UP_PER_REACH = 0.45

    fun command(owner: Point, other: Point, seconds: Double, distanceScale: Double = 1.0, heightScale: Double = 1.0): Pose {
        val (dirX, dirZ) = horizontalDirection(owner, other)
        val back = COMMAND_BACK * distanceScale
        val shoulder = COMMAND_SHOULDER * distanceScale
        val from = Point(
            owner.x - dirX * back - dirZ * shoulder,
            owner.y + COMMAND_UP * heightScale,
            owner.z - dirZ * back + dirX * shoulder,
        )
        val base = lookAt(from, other)
        return base.copy(
            yaw = base.yaw + SWAY_YAW_DEGREES * sin(seconds * 0.9),
            pitch = base.pitch + SWAY_PITCH_DEGREES * sin(seconds * 1.3 + 1.0),
        )
    }

    fun menuView(owner: Point, other: Point, seconds: Double, ownerSize: Double = 1.0, hover: Double = 0.0): Pose {
        val (dirX, dirZ) = horizontalDirection(owner, other)
        val closer = 1.0 - MENU_HOVER_CLOSER * hover
        val side = (MENU_SIDE + MENU_SIDE_PER_SIZE * ownerSize) * closer
        val back = (MENU_BACK + MENU_BACK_PER_SIZE * ownerSize) * closer
        val from = Point(
            owner.x - dirX * back - dirZ * side,
            owner.y + (MENU_UP + MENU_UP_PER_SIZE * ownerSize) * closer,
            owner.z - dirZ * back + dirX * side,
        )
        val toward = lerp(MENU_LOOK_TOWARD_OTHER, MENU_HOVER_LOOK_TOWARD_OTHER, hover)
        val look = Point(
            owner.x + (other.x - owner.x) * toward,
            owner.y + MENU_LOOK_UP + (other.y - owner.y) * toward,
            owner.z + (other.z - owner.z) * toward,
        )
        val base = lookAt(from, look)
        return base.copy(
            yaw = base.yaw + SWAY_YAW_DEGREES * sin(seconds * 0.9),
            pitch = base.pitch + SWAY_PITCH_DEGREES * sin(seconds * 1.3 + 1.0),
        )
    }

    fun impact(attacker: Point, target: Point, distanceScale: Double, heightScale: Double, swingDegrees: Double): Pose {
        val (dirX, dirZ) = horizontalDirection(target, attacker)
        val swing = Math.toRadians(swingDegrees)
        val swungX = dirX * cos(swing) - dirZ * sin(swing)
        val swungZ = dirX * sin(swing) + dirZ * cos(swing)
        val span = hypot(attacker.x - target.x, attacker.z - target.z)
        val distance = (IMPACT_MIN_BACK + span * IMPACT_SPAN_FACTOR) * distanceScale
        val from = Point(target.x + swungX * distance, target.y + IMPACT_UP * heightScale, target.z + swungZ * distance)
        val look = Point(
            target.x + (attacker.x - target.x) * IMPACT_LOOK_TOWARD_ATTACKER,
            target.y + (attacker.y - target.y) * IMPACT_LOOK_TOWARD_ATTACKER,
            target.z + (attacker.z - target.z) * IMPACT_LOOK_TOWARD_ATTACKER,
        )
        return lookAt(from, look)
    }

    fun pullBack(subject: Point, other: Point, progress: Double): Pose {
        val distance = lerp(PULL_START_DISTANCE, PULL_END_DISTANCE, progress)
        val height = lerp(PULL_START_HEIGHT, PULL_END_HEIGHT, progress)
        return aroundSubject(subject, other, PULL_OFFSET_DEGREES, distance, height, 0.0)
    }

    fun swoop(subject: Point, other: Point, progress: Double): Pose {
        val angle = lerp(SWOOP_START_DEGREES, 0.0, progress)
        val distance = lerp(SWOOP_START_DISTANCE, SWOOP_END_DISTANCE, progress)
        val height = lerp(SWOOP_START_HEIGHT, SWOOP_END_HEIGHT, progress)
        return aroundSubject(subject, other, angle, distance, height, SWOOP_LOOK_UP)
    }

    fun capture(subject: Point, other: Point, progress: Double): Pose =
        aroundSubject(subject, other, CAPTURE_DEGREES, lerp(CAPTURE_START_DISTANCE, CAPTURE_END_DISTANCE, progress.coerceIn(0.0, 1.0)), CAPTURE_HEIGHT, 0.0)

    fun gimmick(subject: Point, other: Point, progress: Double, style: GimmickStyle = GimmickStyle.MEGA): Pose {
        val p = progress.coerceIn(0.0, 1.0)
        val eased = smoothStep(p)
        return when (style) {
            GimmickStyle.MEGA -> aroundSubject(subject, other, lerp(70.0, -55.0, eased), lerp(2.5, 1.7, eased), lerp(0.2, 1.1, eased), 0.35)
            GimmickStyle.TERA -> {
                val whip = 1.0 - (1.0 - (p / 0.4).coerceAtMost(1.0)).let { it * it * it }
                aroundSubject(subject, other, lerp(95.0, 20.0, whip), lerp(1.2, 2.1, p), lerp(0.4, 0.7, p), 0.5)
            }
            GimmickStyle.ZPOWER -> aroundSubject(subject, other, lerp(12.0, 22.0, p), lerp(2.3, 2.0, p), lerp(0.0, 2.4, eased), lerp(0.1, 0.9, eased))
            GimmickStyle.ULTRA -> aroundSubject(subject, other, lerp(0.0, 20.0, p), lerp(2.8, 1.8, eased), lerp(7.0, 1.0, p * p), 0.4)
        }
    }

    fun dynamax(subject: Point, other: Point, subjectSize: Double, otherSize: Double, progress: Double, orbitDegrees: Double = 0.0): Pose {
        val (dirX, dirZ) = horizontalDirection(other, subject)
        val middle = Point((subject.x + other.x) / 2, (subject.y + other.y) / 2, (subject.z + other.z) / 2)
        val span = hypot(subject.x - other.x, subject.z - other.z)

        val giant = max(subjectSize * DYNAMAX_FRAMING, DYNAMAX_MIN_GIANT)
        val topHeight = max(DYNAMAX_MIN_TOP, span * 0.9 + giant * 1.5)
        val overhead = lookAt(Point(middle.x - dirX * 1.0, middle.y + topHeight, middle.z - dirZ * 1.0), middle)

        val top = overhead.copy(yaw = overhead.yaw + DYNAMAX_SPIN_DEGREES * progress)
        val back = (otherSize * 0.7 + DYNAMAX_BACK_MARGIN + giant * 0.8).coerceAtMost(DYNAMAX_MAX_BACK)
        val height = max(otherSize * 1.05, giant * 0.45)
        val farFrom = Point(other.x - dirX * back, other.y + height, other.z - dirZ * back)
        val far = lookAt(farFrom, Point(subject.x, subject.y + giant * 0.3, subject.z))
        val settle = smoothStep((progress - DYNAMAX_HOLD_TOP) / (DYNAMAX_ARRIVE - DYNAMAX_HOLD_TOP))
        val from = blend(top, far, settle)
        val after = smoothStep((progress - DYNAMAX_ARRIVE) / (1.0 - DYNAMAX_ARRIVE))
        if (orbitDegrees != 0.0 && progress > DYNAMAX_ARRIVE) {

            val aim = Point(subject.x, subject.y + giant * 0.3, subject.z)

            val scale = 1.0 + (DYNAMAX_ORBIT_RADIUS_SCALE - 1.0) * after
            val relX = (farFrom.x - subject.x) * scale
            val relZ = (farFrom.z - subject.z) * scale
            val angle = Math.toRadians(orbitDegrees * after)
            val x = subject.x + relX * cos(angle) + relZ * sin(angle)
            val z = subject.z - relX * sin(angle) + relZ * cos(angle)
            return lookAt(Point(x, farFrom.y, z), aim)
        }

        val push = after * DYNAMAX_PUSH
        val toward = Point(subject.x - from.x, 0.0, subject.z - from.z)
        val length = max(toward.length(), 1e-6)
        return from.copy(x = from.x + toward.x / length * push, z = from.z + toward.z / length * push)
    }

    private fun smoothStep(p: Double): Double {
        val t = p.coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    private fun blend(a: Pose, b: Pose, t: Double): Pose {
        var dyaw = (b.yaw - a.yaw) % 360.0
        if (abs(dyaw) > 180.0) dyaw -= Math.copySign(360.0, dyaw)
        return Pose(
            lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.z, b.z, t),
            a.yaw + dyaw * t, lerp(a.pitch, b.pitch, t),
        )
    }

    fun orbit(subject: Point, other: Point, progress: Double, sweepDegrees: Double): Pose {
        val angle = ORBIT_START_DEGREES + sweepDegrees * progress
        return aroundSubject(subject, other, angle, ORBIT_DISTANCE, lerp(ORBIT_START_HEIGHT, ORBIT_END_HEIGHT, progress), ORBIT_LOOK_UP)
    }

    private fun aroundSubject(subject: Point, other: Point, degrees: Double, distance: Double, height: Double, lookUp: Double): Pose {
        val (backX, backZ) = horizontalDirection(other, subject)
        val angle = Math.toRadians(degrees)
        val x = backX * cos(angle) - backZ * sin(angle)
        val z = backX * sin(angle) + backZ * cos(angle)
        val from = Point(subject.x + x * distance, subject.y + height, subject.z + z * distance)
        return lookAt(from, Point(subject.x, subject.y + lookUp, subject.z))
    }

    private fun lerp(from: Double, to: Double, progress: Double) = from + (to - from) * progress

    private fun horizontalDirection(from: Point, to: Point): Pair<Double, Double> {
        val dx = to.x - from.x
        val dz = to.z - from.z
        val length = sqrt(dx * dx + dz * dz)
        return if (length < 1e-6) 0.0 to 1.0 else dx / length to dz / length
    }
}

object CaptureSpot {
    fun nearest(candidates: List<Point>, near: Point, radius: Double): Int? {
        val best = candidates.indices.minByOrNull { (candidates[it] - near).length() } ?: return null
        return best.takeIf { (candidates[it] - near).length() <= radius }
    }
}
