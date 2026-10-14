package com.cbpr.camera

import com.cbpr.battle.ImpactFlags
import com.cbpr.battle.MoveProfile
import kotlin.math.exp
import kotlin.math.sin

class ShotPlan(
    val profile: MoveProfile,
    val attackerIsMon: Boolean,
    val reducedMotion: Boolean,
    val hitStopEnabled: Boolean,
    val critSpecials: Boolean,

    val attackerSlot: String? = null,
    val targetSlot: String? = null,
)

class ImpactEffects(val fovKick: Double, val shake: Double, val tiltDegrees: Double, val hitStop: Double)

object Shake {
    const val DECAY_PER_SECOND = 6.0

    fun decay(amplitude: Double, dt: Double, perSecond: Double = DECAY_PER_SECOND): Double = amplitude * exp(-perSecond * dt)

    fun yaw(seconds: Double, amplitude: Double): Double = amplitude * (sin(seconds * 41.0) + 0.5 * sin(seconds * 67.0 + 1.0))

    fun pitch(seconds: Double, amplitude: Double): Double = amplitude * (sin(seconds * 47.0 + 2.0) + 0.5 * sin(seconds * 71.0))
}

class ActionShot(val plan: ShotPlan) {
    enum class Stage { ANTICIPATE, APPROACH, IMPACT, RECOVER, DONE }

    var stage = Stage.ANTICIPATE
        private set
    private var stageSeconds = 0.0
    private var totalSeconds = 0.0

    private val tiltSign = if (plan.attackerIsMon) 1.0 else -1.0
    private var impactStrong = false

    private val speed = if (plan.reducedMotion) REDUCED_SPEED else 1.0
    private val profile get() = plan.profile

    val finished: Boolean get() = stage == Stage.DONE

    val wantsBars: Boolean get() = stage != Stage.RECOVER && stage != Stage.DONE

    fun step(dt: Double) {
        stageSeconds += dt
        totalSeconds += dt
        if (totalSeconds > MAX_SECONDS && stage != Stage.DONE) enter(Stage.DONE)
        when (stage) {
            Stage.ANTICIPATE -> if (stageSeconds >= profile.anticipation * speed) enter(Stage.APPROACH)
            Stage.APPROACH -> if (stageSeconds >= (profile.approach + profile.waitForImpact) * speed) enter(Stage.RECOVER)
            Stage.IMPACT -> if (stageSeconds >= profile.hold * speed) enter(Stage.RECOVER)
            Stage.RECOVER -> if (stageSeconds >= RECOVER_SECONDS * speed) enter(Stage.DONE)
            Stage.DONE -> Unit
        }
    }

    fun impact(flags: ImpactFlags): ImpactEffects? {
        if (stage == Stage.RECOVER || stage == Stage.DONE) return null

        val repeat = stage == Stage.IMPACT
        if (!repeat) enter(Stage.IMPACT)
        stageSeconds = 0.0
        impactStrong = flags.strong
        return effects(flags, if (repeat) REPEAT_SOFTEN else 1.0)
    }

    fun upgrade(flags: ImpactFlags): ImpactEffects? {
        if (stage != Stage.IMPACT || !flags.strong) return null
        impactStrong = true
        return effects(flags, 1.0)
    }

    private fun effects(flags: ImpactFlags, soften: Double): ImpactEffects {
        val strength = if (flags.strong) STRONG_BOOST else 1.0
        val motion = if (plan.reducedMotion) REDUCED_EFFECT else 1.0
        val tilt = if (plan.critSpecials && flags.strong) {
            maxOf(profile.tilt, if (flags.crit) CRIT_TILT else SUPER_TILT)
        } else {
            profile.tilt
        }
        return ImpactEffects(
            fovKick = profile.fovKick * strength * soften * motion,
            shake = if (plan.reducedMotion) 0.0 else profile.shake * strength * soften,
            tiltDegrees = if (plan.reducedMotion) 0.0 else tilt * tiltSign,
            hitStop = if (plan.hitStopEnabled && !plan.reducedMotion) profile.hitStop else 0.0,
        )
    }

    fun pose(attacker: Point, target: Point, seconds: Double): Pose? = when (stage) {
        Stage.ANTICIPATE -> Shots.command(attackerOf(attacker, target), otherOf(attacker, target), seconds, ANTICIPATE_PULL, 1.0)
        Stage.APPROACH -> Shots.command(attacker, target, seconds, profile.distanceScale, profile.heightScale)
        Stage.IMPACT -> {
            val closer = if (impactStrong) STRONG_CLOSER else 1.0
            Shots.impact(attacker, target, profile.distanceScale * closer, profile.heightScale, profile.swingDegrees * (if (impactStrong) STRONG_SWING else 1.0) * tiltSign)
        }
        Stage.RECOVER, Stage.DONE -> null
    }

    private fun attackerOf(attacker: Point, target: Point) = if (plan.attackerIsMon) attacker else target
    private fun otherOf(attacker: Point, target: Point) = if (plan.attackerIsMon) target else attacker

    private fun enter(next: Stage) {
        stage = next
        stageSeconds = 0.0
    }

    private companion object {
        const val RECOVER_SECONDS = 0.5
        const val MAX_SECONDS = 6.0
        const val REDUCED_SPEED = 0.6
        const val REDUCED_EFFECT = 0.5
        const val STRONG_BOOST = 1.6
        const val STRONG_CLOSER = 0.8
        const val STRONG_SWING = 1.4
        const val REPEAT_SOFTEN = 0.6
        const val CRIT_TILT = 6.0
        const val SUPER_TILT = 4.0
        const val ANTICIPATE_PULL = 1.15
    }
}
