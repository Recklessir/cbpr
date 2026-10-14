package com.cbpr.camera

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class BeatPlan(
    val kind: Kind,

    val subjectIsMon: Boolean,
    val reducedMotion: Boolean,

    val darkened: Boolean = false,

    val subjectSize: Double = 1.0,
    val otherSize: Double = 1.0,

    val style: GimmickStyle = GimmickStyle.MEGA,

    val trainer: Point? = null,
    val preferRight: Boolean = true,

    val subjectSlot: String? = null,
) {
    enum class Kind { FAINT, SEND_OUT, VICTORY, GIMMICK, DYNAMAX, SWITCH, CAPTURE, WEATHER }
}

data class SpringTuning(val stiffness: Double, val dampingRatio: Double)

class BeatShot(val plan: BeatPlan) {
    private val speed = if (plan.reducedMotion) REDUCED_SPEED else 1.0

    private var mainSeconds = when (plan.kind) {
        BeatPlan.Kind.FAINT -> FAINT_SECONDS
        BeatPlan.Kind.SEND_OUT, BeatPlan.Kind.SWITCH -> SEND_OUT_SECONDS
        BeatPlan.Kind.VICTORY -> VICTORY_SECONDS
        BeatPlan.Kind.GIMMICK -> plan.style.seconds
        BeatPlan.Kind.DYNAMAX -> DYNAMAX_SECONDS
        BeatPlan.Kind.CAPTURE -> CAPTURE_MAX_SECONDS
        BeatPlan.Kind.WEATHER -> WEATHER_SECONDS
    } * (if (plan.kind == BeatPlan.Kind.CAPTURE) 1.0 else speed)

    private val recoverSeconds = if (plan.kind == BeatPlan.Kind.VICTORY || plan.kind == BeatPlan.Kind.CAPTURE) 0.0 else RECOVER_SECONDS * speed
    private var seconds = 0.0
    private var kickGiven = false
    private var liveSubjectSize = 0.0
    private var liveOtherSize = 0.0
    private var secondKickGiven = false

    private var switchRight: Boolean? = null

    val finished: Boolean get() = seconds >= mainSeconds + recoverSeconds

    val wantsBars: Boolean get() = seconds < mainSeconds

    fun liveSizes(subject: Double, other: Double) {
        liveSubjectSize = subject
        liveOtherSize = other
    }

    val elapsed: Double get() = seconds

    val holdsEnd: Boolean get() = (plan.kind == BeatPlan.Kind.VICTORY || plan.kind == BeatPlan.Kind.CAPTURE) && !finished

    fun conclude(): Boolean {
        if (plan.kind != BeatPlan.Kind.CAPTURE) return false
        mainSeconds = minOf(mainSeconds, seconds + CAPTURE_TAIL_SECONDS)
        return true
    }

    val leadsToAttack: Boolean get() = plan.kind == BeatPlan.Kind.GIMMICK || plan.kind == BeatPlan.Kind.DYNAMAX
    val barHoldSeconds: Double get() = if (plan.kind == BeatPlan.Kind.DYNAMAX) DYNAMAX_BAR_HOLD_SECONDS else plan.style.barHoldSeconds

    val fitsToSpace: Boolean get() = plan.kind != BeatPlan.Kind.DYNAMAX

    val tuning: SpringTuning
        get() = when (plan.kind) {
            BeatPlan.Kind.FAINT -> FAINT_TUNING
            BeatPlan.Kind.SEND_OUT, BeatPlan.Kind.SWITCH -> SEND_OUT_TUNING
            BeatPlan.Kind.VICTORY -> VICTORY_TUNING
            BeatPlan.Kind.GIMMICK -> if (seconds > GIMMICK_WHIP_SECONDS) GIMMICK_TUNING else GIMMICK_WHIP_TUNING
            BeatPlan.Kind.DYNAMAX -> DYNAMAX_TUNING
            BeatPlan.Kind.CAPTURE -> CAPTURE_TUNING
            BeatPlan.Kind.WEATHER -> WEATHER_TUNING
        }

    val returnTuning: SpringTuning? get() = if (plan.kind == BeatPlan.Kind.VICTORY) VICTORY_RETURN_TUNING else null

    val tint: Double
        get() = when {
            seconds >= mainSeconds -> 0.0
            plan.kind == BeatPlan.Kind.FAINT -> 1.0
            plan.kind == BeatPlan.Kind.VICTORY && plan.darkened -> 1.0
            else -> 0.0
        }

    private val progressRaw: Double get() = (seconds / mainSeconds).coerceIn(0.0, 1.0)

    private val changeAt: Double
        get() = when (plan.kind) {
            BeatPlan.Kind.GIMMICK -> plan.style.changeAt
            BeatPlan.Kind.DYNAMAX -> Shots.DYNAMAX_ARRIVE_AT
            else -> -1.0
        }

    val flash: Double
        get() {
            if (changeAt < 0.0 || plan.reducedMotion || seconds >= mainSeconds) return 0.0
            val width = if (plan.kind == BeatPlan.Kind.DYNAMAX) DYNAMAX_FLASH_WIDTH else FLASH_WIDTH
            val near = (1.0 - abs(progressRaw - changeAt) / width).coerceIn(0.0, 1.0)
            return near * near
        }

    val flashRgb: Int get() = if (plan.kind == BeatPlan.Kind.GIMMICK) plan.style.flashRgb else DYNAMAX_FLASH_RGB

    val roll: Double
        get() {
            if (plan.reducedMotion || seconds >= mainSeconds) return 0.0
            val p = progressRaw
            return when {
                plan.kind == BeatPlan.Kind.DYNAMAX -> DYNAMAX_ROLL * sin(p * PI * 2.0)
                plan.kind == BeatPlan.Kind.GIMMICK && plan.style == GimmickStyle.TERA -> TERA_ROLL * sin(p * PI * 2.0)
                plan.kind == BeatPlan.Kind.GIMMICK && plan.style == GimmickStyle.ULTRA -> ULTRA_ROLL * smooth(p)
                plan.kind == BeatPlan.Kind.GIMMICK && plan.style == GimmickStyle.ZPOWER -> ZPOWER_ROLL * smooth(p)
                else -> 0.0
            }
        }

    val rumble: Double
        get() {
            if (plan.reducedMotion || seconds >= mainSeconds) return 0.0
            val p = progressRaw
            return when {
                plan.kind == BeatPlan.Kind.DYNAMAX && p in 0.45..0.9 -> DYNAMAX_RUMBLE
                plan.kind == BeatPlan.Kind.GIMMICK && plan.style == GimmickStyle.ULTRA && p in 0.7..0.9 -> ULTRA_RUMBLE
                else -> 0.0
            }
        }

    fun takeKick(): Double {
        if (plan.kind != BeatPlan.Kind.GIMMICK && plan.kind != BeatPlan.Kind.DYNAMAX) return 0.0
        if (!kickGiven) {
            kickGiven = true
            return START_KICK
        }
        if (!secondKickGiven && progressRaw >= changeAt) {
            secondKickGiven = true
            return if (plan.kind == BeatPlan.Kind.DYNAMAX) DYNAMAX_KICK else CHANGE_KICK
        }
        return 0.0
    }

    fun step(dt: Double) {
        seconds += dt
    }

    fun pose(mon: Point, foe: Point): Pose? {
        if (seconds >= mainSeconds) return null
        val subject = if (plan.subjectIsMon) mon else foe
        val other = if (plan.subjectIsMon) foe else mon
        val progress = smooth(seconds / mainSeconds)
        return when (plan.kind) {
            BeatPlan.Kind.FAINT -> Shots.pullBack(subject, other, progress)
            BeatPlan.Kind.SEND_OUT -> Shots.swoop(subject, other, progress)
            BeatPlan.Kind.SWITCH -> Shots.switchFront(subject, other, switchRight ?: Shots.switchSide(plan.preferRight, subject, other, plan.trainer).also { switchRight = it }, progress)
            BeatPlan.Kind.GIMMICK -> Shots.gimmick(subject, other, progressRaw, plan.style)

            BeatPlan.Kind.WEATHER -> Shots.wide(mon, foe)
            BeatPlan.Kind.CAPTURE -> Shots.capture(subject, other, if (plan.reducedMotion) 0.5 else smooth(seconds / CAPTURE_PUSH_SECONDS))
            BeatPlan.Kind.DYNAMAX -> Shots.dynamax(subject, other, maxOf(plan.subjectSize, liveSubjectSize), maxOf(plan.otherSize, liveOtherSize), progressRaw, if (plan.reducedMotion) 0.0 else Shots.DYNAMAX_ORBIT_DEGREES)

            BeatPlan.Kind.VICTORY -> Shots.orbit(subject, other, progress, if (plan.reducedMotion) REDUCED_SWEEP else FULL_SWEEP)
        }
    }

    private fun smooth(p: Double): Double {
        val t = p.coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    companion object {

        const val DYNAMAX_BAR_HOLD_SECONDS = 16.0
        private const val BEAT_DAMPING_RATIO = 0.85
        private const val GIMMICK_DAMPING_RATIO = 0.7
        private const val GIMMICK_WHIP_SECONDS = 0.35
        private val FAINT_TUNING = SpringTuning(25.0, BEAT_DAMPING_RATIO)
        private val SEND_OUT_TUNING = SpringTuning(40.0, BEAT_DAMPING_RATIO)
        private val VICTORY_TUNING = SpringTuning(30.0, BEAT_DAMPING_RATIO)
        private val VICTORY_RETURN_TUNING = SpringTuning(90.0, BEAT_DAMPING_RATIO)
        private val GIMMICK_WHIP_TUNING = SpringTuning(75.0, GIMMICK_DAMPING_RATIO)
        private val GIMMICK_TUNING = SpringTuning(40.0, GIMMICK_DAMPING_RATIO)
        private val DYNAMAX_TUNING = SpringTuning(22.0, BEAT_DAMPING_RATIO)
        private val CAPTURE_TUNING = SpringTuning(20.0, BEAT_DAMPING_RATIO)
        private val WEATHER_TUNING = SpringTuning(25.0, BEAT_DAMPING_RATIO)

        const val WEATHER_SECONDS = 1.6
        const val CAPTURE_MAX_SECONDS = 15.0
        const val CAPTURE_TAIL_SECONDS = 1.0
        const val CAPTURE_PUSH_SECONDS = 6.0
        const val FAINT_SECONDS = 1.8
        const val SEND_OUT_SECONDS = 1.1
        const val DYNAMAX_SECONDS = 7.0

        const val VICTORY_SECONDS = 1.1
        const val RECOVER_SECONDS = 0.5
        const val REDUCED_SPEED = 0.6
        const val FULL_SWEEP = 70.0
        const val REDUCED_SWEEP = 20.0
        const val FLASH_WIDTH = 0.1
        const val DYNAMAX_FLASH_WIDTH = 0.14
        const val DYNAMAX_FLASH_RGB = 0xFF6A5A
        const val START_KICK = 9.0
        const val CHANGE_KICK = 11.0
        const val DYNAMAX_KICK = 13.0
        const val TERA_ROLL = 6.0
        const val ULTRA_ROLL = 5.0
        const val ZPOWER_ROLL = 3.0
        const val DYNAMAX_ROLL = 7.0
        const val DYNAMAX_RUMBLE = 0.35
        const val ULTRA_RUMBLE = 0.5
    }
}
