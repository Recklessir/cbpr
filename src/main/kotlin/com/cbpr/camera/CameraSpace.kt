package com.cbpr.camera

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

fun interface Clearance {
    fun free(from: Point, to: Point): Double
}

class CameraSpace(private val clearance: Clearance?) {

    var monSize = REFERENCE_SIZE
    var foeSize = REFERENCE_SIZE

    var scale = 1.0
        private set
    private var goal = 1.0
    private var sinceCheck = CHECK_INTERVAL

    fun update(dt: Double, mon: Point, foe: Point): Double {
        sinceCheck += dt
        if (sinceCheck >= CHECK_INTERVAL) {
            sinceCheck = 0.0
            goal = sizeScale(monSize, foeSize) * (clearance?.let { tightScale(it, mon, foe, sizeScale(monSize, foeSize)) } ?: 1.0)
        }
        val step = RATE * dt
        scale += (goal - scale).coerceIn(-step, step)
        return scale
    }

    fun reset() {
        scale = 1.0
        goal = 1.0
        sinceCheck = CHECK_INTERVAL
    }

    companion object {
        const val REFERENCE_SIZE = 1.5
        const val MIN_SIZE_SCALE = 1.0
        const val MAX_SIZE_SCALE = 1.6
        const val MIN_TIGHT_SCALE = 0.5
        const val MIN_FADE_ALPHA = 0.25
        const val UP_RAY = 2.5
        const val SIDE_RAY = 3.0
        const val CHECK_INTERVAL = 0.1

        const val RATE = 1.0

        const val RAYS = 3

        fun pivot(shot: Pose, candidates: List<Point>): Point {
            val yaw = Math.toRadians(shot.yaw)
            val pitch = Math.toRadians(shot.pitch)
            val forward = Point(-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch))
            val eye = Point(shot.x, shot.y, shot.z)
            return candidates.minBy { point ->
                val toPoint = point - eye
                val along = max(0.0, toPoint dot forward)
                (toPoint - forward * along).length()
            }
        }

        fun fit(shot: Pose, scale: Double, candidates: List<Point>, lowestY: Double): Pose {
            val pivot = pivot(shot, candidates)
            val scaled = CameraAvoidance.apply(CameraAvoidance.Offset(scale, 0.0, 0.0), shot, pivot)
            val floor = min(shot.y, lowestY)
            if (scaled.y >= floor) return scaled
            val yaw = Math.toRadians(shot.yaw)
            val pitch = Math.toRadians(shot.pitch)
            val forward = Point(-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch))
            val eye = Point(shot.x, shot.y, shot.z)
            val seen = eye + forward * max(0.0, (pivot - eye) dot forward)
            val aim = pivot + (seen - pivot) * scale
            return Shots.lookAt(Point(scaled.x, floor, scaled.z), aim).copy(fov = shot.fov, roll = shot.roll)
        }

        fun fadeAlpha(distance: Double, size: Double): Double {
            val solidAt = size * 1.2 + 1.2
            val fadedAt = size * 0.6 + 0.4
            val t = ((distance - fadedAt) / (solidAt - fadedAt)).coerceIn(0.0, 1.0)
            return MIN_FADE_ALPHA + (1.0 - MIN_FADE_ALPHA) * t
        }

        fun sizeScale(monSize: Double, foeSize: Double): Double =

            sqrt(max(monSize, foeSize) / REFERENCE_SIZE).coerceIn(MIN_SIZE_SCALE, MAX_SIZE_SCALE)

        fun tightScale(clearance: Clearance, mon: Point, foe: Point, size: Double): Double {
            val focus = CameraAvoidance.focus(mon, foe)
            val dx = foe.x - mon.x
            val dz = foe.z - mon.z
            val length = hypot(dx, dz)
            val (sideX, sideZ) = if (length < 1e-6) 1.0 to 0.0 else -dz / length to dx / length
            val up = clearance.free(focus, focus + Point(0.0, UP_RAY * size, 0.0))
            val left = clearance.free(focus, focus + Point(sideX, 0.0, sideZ) * (SIDE_RAY * size))
            val right = clearance.free(focus, focus - Point(sideX, 0.0, sideZ) * (SIDE_RAY * size))
            val open = minOf(up, left, right).coerceIn(0.0, 1.0)
            return MIN_TIGHT_SCALE + (1.0 - MIN_TIGHT_SCALE) * open
        }
    }
}
