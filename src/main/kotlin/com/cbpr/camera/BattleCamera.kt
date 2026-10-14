package com.cbpr.camera

import com.cbpr.battle.ImpactFlags
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BattleCamera {
    enum class Phase { OFF, INTRO, COMMAND, RETURNING }

    var phase = Phase.OFF
        private set

    private val x = Spring(0.0, INTRO_STIFFNESS, Spring.dampingFor(INTRO_STIFFNESS, INTRO_DAMPING_RATIO))
    private val y = Spring(0.0, INTRO_STIFFNESS, Spring.dampingFor(INTRO_STIFFNESS, INTRO_DAMPING_RATIO))
    private val z = Spring(0.0, INTRO_STIFFNESS, Spring.dampingFor(INTRO_STIFFNESS, INTRO_DAMPING_RATIO))
    private val yaw = Spring(0.0, INTRO_STIFFNESS, Spring.dampingFor(INTRO_STIFFNESS, INTRO_DAMPING_RATIO))
    private val pitch = Spring(0.0, INTRO_STIFFNESS, Spring.dampingFor(INTRO_STIFFNESS, INTRO_DAMPING_RATIO))
    private val springs = listOf(x, y, z, yaw, pitch)

    private val fov = Spring(0.0, FOV_STIFFNESS, Spring.dampingFor(FOV_STIFFNESS, FOV_DAMPING_RATIO))
    private val roll = Spring(0.0, ROLL_STIFFNESS, Spring.dampingFor(ROLL_STIFFNESS, ROLL_DAMPING_RATIO))
    private var rollTarget = 0.0
    private var shakeAmplitude = 0.0
    private var shakeDecay = Shake.DECAY_PER_SECOND
    private var hitStopLeft = 0.0

    private var motionSeconds = 0.0

    private var action: ActionShot? = null
    private var appliedStage: ActionShot.Stage? = null
    private var beat: BeatShot? = null
    private var appliedTuning: SpringTuning? = null
    private var endRequested = false
    private var barHold = 0.0
    private var barHoldGiven = false

    var reducedMotion = false
    private val introScale: Double get() = if (reducedMotion) REDUCED_INTRO_SCALE else 1.0

    var menuView = false

    var hoverView = false

    private var menuViewSeconds = 0.0
    private val hoverViewAmount = Spring(0.0, HOVER_VIEW_STIFFNESS, Spring.dampingFor(HOVER_VIEW_STIFFNESS, 1.0))

    var parallax = false
    var cursorX = 0.0
    var cursorY = 0.0
    private val driftYaw = Spring(0.0, DRIFT_STIFFNESS, Spring.dampingFor(DRIFT_STIFFNESS, 1.0))
    private val driftPitch = Spring(0.0, DRIFT_STIFFNESS, Spring.dampingFor(DRIFT_STIFFNESS, 1.0))
    private var avoidance: CameraAvoidance? = null
    private var space: CameraSpace? = null

    private var battle = BattleSnapshot()
    private var clearance: Clearance? = null
    private var sight: Sight? = null

    private var phaseSeconds = 0.0
    private var totalSeconds = 0.0
    private var lostSeconds = 0.0
    private var lastTarget: Pose? = null

    val wantsBars: Boolean
        get() = phase == Phase.INTRO || (phase == Phase.COMMAND && (action?.wantsBars == true || beat?.wantsBars == true || barHold > 0.0))

    var released = false
        private set

    val beatKind: BeatPlan.Kind? get() = beat?.plan?.kind

    val monHidden: Boolean get() = avoidance?.monHidden == true
    val foeHidden: Boolean get() = avoidance?.foeHidden == true

    fun useSpace(next: Clearance?, enabled: Boolean) {
        if (enabled == (space != null) && next === clearance) return
        clearance = next
        space = if (enabled) CameraSpace(next) else null
    }

    fun useBattle(snapshot: BattleSnapshot) {
        battle = snapshot
    }

    fun useSight(next: Sight?) {
        if (next === sight) return
        sight = next
        avoidance = next?.let(::CameraAvoidance)
    }

    val busy: Boolean get() = beat != null

    val acting: Boolean get() = action != null || beat != null

    val tint: Double get() = beat?.tint ?: 0.0

    val flash: Double get() = beat?.flash ?: 0.0
    val flashRgb: Int get() = beat?.flashRgb ?: 0xFFFFFF

    fun begin(vanilla: Pose) {
        if (phase == Phase.OFF) {
            x.snap(vanilla.x)
            y.snap(vanilla.y)
            z.snap(vanilla.z)
            yaw.snap(vanilla.yaw)
            pitch.snap(vanilla.pitch)
        }
        clearEffects()
        totalSeconds = 0.0
        released = false
        avoidance?.reset()
        lostSeconds = 0.0
        lastTarget = null
        enter(Phase.INTRO)
    }

    fun beginCapture(vanilla: Pose, plan: BeatPlan) {
        begin(vanilla)
        enter(Phase.COMMAND)
        playBeat(plan)
    }

    fun concludeCapture() {
        if (beat?.conclude() == true) end()
    }

    fun end() {
        if (phase == Phase.INTRO || phase == Phase.COMMAND) {
            if (beat?.holdsEnd == true) {
                endRequested = true
                return
            }
            action = null
            beat = null
            rollTarget = 0.0
            enter(Phase.RETURNING)
        }
    }

    fun abort() {
        phase = Phase.OFF
        driftYaw.snap(0.0)
        driftPitch.snap(0.0)
        avoidance?.reset()
        lastTarget = null
        clearEffects()
    }

    fun play(plan: ShotPlan) {
        if (phase != Phase.COMMAND) return

        if (beat != null) {
            if (beat?.leadsToAttack != true) return
            beat = null
            setStiffness(COMMAND_STIFFNESS, COMMAND_DAMPING_RATIO)
        }

        barHold = 0.0
        barHoldGiven = false
        action = ActionShot(plan)
        appliedStage = null
    }

    fun playBeat(plan: BeatPlan) {
        if (phase != Phase.COMMAND) return
        action = null
        appliedStage = null
        rollTarget = 0.0
        barHold = 0.0
        barHoldGiven = false
        val shot = BeatShot(plan)
        beat = shot
        appliedTuning = shot.tuning
        setStiffness(shot.tuning.stiffness, shot.tuning.dampingRatio)
    }

    fun impact(flags: ImpactFlags) {
        action?.impact(flags)?.let(::apply)
    }

    fun upgradeImpact(flags: ImpactFlags) {
        action?.upgrade(flags)?.let(::apply)
    }

    fun update(dt: Double, vanilla: Pose, mon: Point?, foe: Point?): Pose? {
        if (phase == Phase.OFF) return null
        phaseSeconds += dt
        totalSeconds += dt
        barHold = max(0.0, barHold - dt)
        val usable = menuView && phase == Phase.COMMAND && action == null && beat == null

        if (!usable && hoverViewAmount.value > HOVER_FOLD_MIN) foldHoverOffset(mon, foe)
        menuViewSeconds = if (menuView) menuViewSeconds + dt else 0.0
        val settled = menuViewSeconds >= HOVER_ARM_SECONDS
        hoverViewAmount.step(if (hoverView && usable && settled) 1.0 else 0.0, dt)

        val held = hitStopLeft > 0.0
        val motionDt = if (held) 0.0 else dt
        motionSeconds += motionDt
        if (held) hitStopLeft -= dt
        tickAction(motionDt)
        tickBeat(motionDt)

        val target = when (phase) {
            Phase.RETURNING -> vanilla
            else -> shotTarget(mon, foe, dt)
        }

        step(target, motionDt)
        shakeAmplitude = Shake.decay(shakeAmplitude, motionDt, shakeDecay)
        val idle = parallax && phase == Phase.COMMAND && action == null && beat == null && !battle.targetStep
        driftYaw.step(if (idle) cursorX * DRIFT_YAW_DEGREES else 0.0, dt)
        driftPitch.step(if (idle) cursorY * DRIFT_PITCH_DEGREES else 0.0, dt)
        if (phase == Phase.INTRO && phaseSeconds >= (INTRO_WIDE_SECONDS + INTRO_SETTLE_SECONDS) * introScale) enter(Phase.COMMAND)
        if (phase == Phase.RETURNING && returned(vanilla)) {
            abort()
            return null
        }
        val hover = hoverOffset(mon, foe)
        val pose = Pose(
            x.value + hover.x, y.value + hover.y, z.value + hover.z,
            yaw.value + Shake.yaw(motionSeconds, shakeAmplitude) + driftYaw.value + hover.yaw,
            pitch.value + Shake.pitch(motionSeconds, shakeAmplitude) + driftPitch.value + hover.pitch,
            fov.value, roll.value,
        )

        return if (phase == Phase.COMMAND) limitZoomOut(pose, listOfNotNull(mon, foe) + memberPoints()) else pose
    }

    private fun limitZoomOut(pose: Pose, pokemon: List<Point>): Pose {
        val eye = Point(pose.x, pose.y, pose.z)
        val nearest = pokemon.minByOrNull { (it - eye).length() } ?: return pose
        val limit = if (beat?.fitsToSpace == false) DYNAMAX_MAX_ZOOM_OUT else MAX_ZOOM_OUT * max(1.0, space?.scale ?: 1.0)
        val distance = (eye - nearest).length()
        if (distance <= limit) return pose
        val held = nearest + (eye - nearest) * (limit / distance)
        return pose.copy(x = held.x, y = held.y, z = held.z)
    }

    private fun foldHoverOffset(mon: Point?, foe: Point?) {
        val offset = hoverOffset(mon, foe)
        x.value += offset.x
        y.value += offset.y
        z.value += offset.z
        yaw.value += offset.yaw
        pitch.value += offset.pitch
        hoverViewAmount.snap(0.0)
    }

    private fun hoverOffset(mon: Point?, foe: Point?): Pose {
        val amount = hoverViewAmount.value.coerceIn(0.0, 1.0)
        if (amount < 1e-4 || mon == null || foe == null) return NO_OFFSET
        val owner = battle.point(battle.commandedSlot, mon)
        val size = battle.size(battle.commandedSlot, battle.monSize)
        val usual = Shots.menuView(owner, foe, totalSeconds, size)
        val closer = Shots.menuView(owner, foe, totalSeconds, size, 1.0)
        val scale = space?.scale ?: 1.0
        var yaw = (closer.yaw - usual.yaw) % 360.0
        if (abs(yaw) > 180.0) yaw -= Math.copySign(360.0, yaw)
        return Pose(
            (closer.x - usual.x) * scale * amount, (closer.y - usual.y) * scale * amount, (closer.z - usual.z) * scale * amount,
            yaw * amount, (closer.pitch - usual.pitch) * amount,
        )
    }

    private fun tickAction(dt: Double) {
        val shot = action ?: return
        shot.step(dt)
        if (shot.stage != appliedStage) {
            appliedStage = shot.stage
            setStiffness(stageStiffness(shot.stage), stageDamping(shot.stage))
        }

        if (shot.stage != ActionShot.Stage.IMPACT) rollTarget = 0.0
        if (shot.finished) action = null
    }

    private fun tickBeat(dt: Double) {
        val shot = beat ?: return
        shot.step(dt)
        val kick = shot.takeKick()
        if (kick > 0.0) fov.snap(max(fov.value, kick))
        shakeAmplitude = max(shakeAmplitude, shot.rumble)
        if (shot.tuning != appliedTuning) {
            appliedTuning = shot.tuning
            setStiffness(shot.tuning.stiffness, shot.tuning.dampingRatio)
        }

        if (!shot.wantsBars && shot.leadsToAttack && barHold <= 0.0 && !barHoldGiven) {
            barHoldGiven = true
            barHold = shot.barHoldSeconds
        }
        if (!shot.finished) return
        beat = null
        barHoldGiven = false
        setStiffness(COMMAND_STIFFNESS, COMMAND_DAMPING_RATIO)
        if (endRequested) {
            endRequested = false
            enter(Phase.RETURNING)

            shot.returnTuning?.let { setStiffness(it.stiffness, it.dampingRatio) }
        }
    }

    private fun apply(effects: ImpactEffects) {
        if (effects.fovKick > 0.0) fov.snap(max(fov.value, effects.fovKick))
        shakeDecay = Shake.DECAY_PER_SECOND
        shakeAmplitude = max(shakeAmplitude, effects.shake)
        rollTarget = effects.tiltDegrees
        hitStopLeft = effects.hitStop
    }

    private fun shotTarget(mon: Point?, foe: Point?, dt: Double): Pose {
        if (mon == null || foe == null) {
            lostSeconds += dt
            val held = lastTarget
            if (held == null || lostSeconds > LOST_SUBJECT_TIMEOUT) {
                action = null
                beat = null
                endRequested = false
                rollTarget = 0.0
                enter(Phase.RETURNING)
                return held ?: Pose(x.value, y.value, z.value, yaw.value, pitch.value)
            }
            return held
        }
        lostSeconds = 0.0
        val shot = action
        val playingBeat = beat
        playingBeat?.liveSizes(battle.size(playingBeat.plan.subjectSlot, if (playingBeat.plan.subjectIsMon) battle.monSize else battle.foeSize), if (playingBeat.plan.subjectIsMon) battle.foeSize else battle.monSize)

        val faced = foe
        val ownerAt = battle.point(battle.commandedSlot, mon)
        val wideNow = phase == Phase.INTRO && phaseSeconds < INTRO_WIDE_SECONDS * introScale
        val target = if (wideNow) {
            Shots.wide(mon, foe)
        } else if (playingBeat != null) {
            val points = ShotSubjects.beat(playingBeat.plan, mon, foe, battle)
            playingBeat.pose(points.first, points.second) ?: Shots.command(mon, foe, totalSeconds)
        } else if (shot != null) {
            val points = ShotSubjects.action(shot.plan, mon, foe, battle)
            shot.pose(points.first, points.second, totalSeconds) ?: Shots.command(mon, foe, totalSeconds)
        } else if (battle.targetStep && phase == Phase.COMMAND) {
            Shots.overview(mon, foe, max(battle.monGroupSize, battle.foeGroupSize))
        } else if (menuView && phase == Phase.COMMAND) {
            Shots.menuView(ownerAt, faced, totalSeconds, battle.size(battle.commandedSlot, battle.monSize))
        } else {
            Shots.command(ownerAt, faced, totalSeconds)
        }
        val fitted = space?.takeIf { phase != Phase.RETURNING && beat?.fitsToSpace != false }?.let {

            val sizes = spaceSizes(wideNow, battle.commanded != null)
            it.monSize = sizes.first
            it.foeSize = sizes.second
            CameraSpace.fit(target, it.update(dt, ownerAt, faced), listOf(mon, foe, CameraAvoidance.focus(mon, foe), ownerAt, faced), min(mon.y, foe.y))
        } ?: target
        val seen = avoidance?.takeIf { phase != Phase.RETURNING }
        val adjusted = seen?.adjust(fitted, mon, foe, dt, sightPoints(battle.mons, mon), sightPoints(battle.foes, foe)) ?: fitted

        val limited = if (phase == Phase.RETURNING) adjusted else limitZoomOut(adjusted, listOf(mon, foe) + memberPoints())
        lastTarget = limited
        if (seen?.released == true) {
            released = true
            action = null
            beat = null
            endRequested = false
            rollTarget = 0.0
            enter(Phase.RETURNING)
        }
        return limited
    }

    private fun spaceSizes(wide: Boolean, commanding: Boolean): kotlin.Pair<Double, Double> {
        val shot = action
        val playingBeat = beat
        return when {
            wide -> battle.monGroupSize to battle.foeGroupSize
            playingBeat != null -> {
                val plan = playingBeat.plan
                val subject = battle.size(plan.subjectSlot, if (plan.subjectIsMon) battle.monGroupSize else battle.foeGroupSize)
                val other = if (plan.subjectIsMon) battle.foeGroupSize else battle.monGroupSize
                if (plan.subjectIsMon) subject to other else other to subject
            }
            shot != null -> {
                val plan = shot.plan
                val attacker = battle.size(plan.attackerSlot, if (plan.attackerIsMon) battle.monGroupSize else battle.foeGroupSize)
                val victim = battle.size(plan.targetSlot, if (plan.attackerIsMon) battle.foeGroupSize else battle.monGroupSize)
                if (plan.attackerIsMon) attacker to victim else victim to attacker
            }

            battle.targetStep -> battle.monSize to battle.foeSize
            commanding -> battle.size(battle.commandedSlot, battle.monSize) to battle.foeSize
            else -> battle.monGroupSize to battle.foeGroupSize
        }
    }

    private fun memberPoints(): List<Point> = (battle.mons + battle.foes).map { it.point }

    private fun sightPoints(members: List<BattleSnapshot.Member>, centre: Point): List<Point> = if (members.size > 1) members.map { it.point } else listOf(centre)

    private fun step(target: Pose, dt: Double) {
        x.step(target.x, dt)
        y.step(target.y, dt)
        z.step(target.z, dt)

        yaw.step(nearest(yaw.value, target.yaw), dt)
        pitch.step(target.pitch, dt)
        fov.step(0.0, dt)
        roll.step(if (phase == Phase.RETURNING) 0.0 else rollTarget + (beat?.roll ?: 0.0), dt)
    }

    private fun returned(vanilla: Pose): Boolean {
        if (phaseSeconds >= RETURN_TIMEOUT) return true
        return x.isSettled(vanilla.x, 0.03, 0.05) && y.isSettled(vanilla.y, 0.03, 0.05) &&
            z.isSettled(vanilla.z, 0.03, 0.05) &&
            yaw.isSettled(nearest(yaw.value, vanilla.yaw), 0.4, 1.0) &&
            pitch.isSettled(vanilla.pitch, 0.4, 1.0) &&
            fov.isSettled(0.0, 0.1, 0.5) && roll.isSettled(0.0, 0.1, 0.5)
    }

    private fun clearEffects() {
        action = null
        beat = null
        endRequested = false
        appliedStage = null
        fov.snap(0.0)
        roll.snap(0.0)
        rollTarget = 0.0
        shakeAmplitude = 0.0
        shakeDecay = Shake.DECAY_PER_SECOND
        hitStopLeft = 0.0
        barHold = 0.0
        barHoldGiven = false
    }

    private fun enter(next: Phase) {
        phase = next
        phaseSeconds = 0.0
        val stiffness = if (next == Phase.COMMAND) COMMAND_STIFFNESS else if (next == Phase.RETURNING) RETURN_STIFFNESS else INTRO_STIFFNESS
        val ratio = if (next == Phase.COMMAND) COMMAND_DAMPING_RATIO else INTRO_DAMPING_RATIO
        setStiffness(stiffness, ratio)
    }

    private fun setStiffness(stiffness: Double, ratio: Double) {
        val tuned = if (reducedMotion) stiffness * REDUCED_STIFFNESS else stiffness

        springs.forEachIndexed { index, spring ->
            spring.stiffness = tuned
            spring.damping = Spring.dampingFor(tuned, if (index < POSITION_SPRINGS) 1.0 else ratio)
        }
    }

    private fun stageStiffness(stage: ActionShot.Stage): Double = when (stage) {
        ActionShot.Stage.ANTICIPATE -> ANTICIPATE_STIFFNESS
        ActionShot.Stage.APPROACH -> APPROACH_STIFFNESS
        ActionShot.Stage.IMPACT -> IMPACT_STIFFNESS
        ActionShot.Stage.RECOVER, ActionShot.Stage.DONE -> COMMAND_STIFFNESS
    }

    private fun stageDamping(stage: ActionShot.Stage): Double = when (stage) {
        ActionShot.Stage.ANTICIPATE -> COMMAND_DAMPING_RATIO
        ActionShot.Stage.APPROACH -> APPROACH_DAMPING_RATIO
        ActionShot.Stage.IMPACT -> IMPACT_DAMPING_RATIO
        ActionShot.Stage.RECOVER, ActionShot.Stage.DONE -> COMMAND_DAMPING_RATIO
    }

    companion object {
        private const val POSITION_SPRINGS = 3
        private const val HOVER_FOLD_MIN = 1e-4
        private const val REDUCED_STIFFNESS = 1.6
        private const val REDUCED_INTRO_SCALE = 0.6

        const val HOVER_ARM_SECONDS = 0.5

        private const val HOVER_VIEW_STIFFNESS = 2500.0
        private val NO_OFFSET = Pose(0.0, 0.0, 0.0, 0.0, 0.0)

        const val INTRO_WIDE_SECONDS = 1.3
        const val INTRO_SETTLE_SECONDS = 1.2

        const val LOST_SUBJECT_TIMEOUT = 5.0
        const val RETURN_TIMEOUT = 1.5

        const val MAX_ZOOM_OUT = 10.0
        const val DYNAMAX_MAX_ZOOM_OUT = 24.0
        const val DRIFT_YAW_DEGREES = 1.5
        const val DRIFT_PITCH_DEGREES = 1.0

        private const val INTRO_STIFFNESS = 14.0
        private const val DRIFT_STIFFNESS = 20.0
        private const val INTRO_DAMPING_RATIO = 0.7
        private const val COMMAND_STIFFNESS = 30.0
        private const val COMMAND_DAMPING_RATIO = 0.75
        private const val RETURN_STIFFNESS = 45.0
        private const val ANTICIPATE_STIFFNESS = 18.0
        private const val APPROACH_STIFFNESS = 55.0
        private const val APPROACH_DAMPING_RATIO = 0.8
        private const val IMPACT_STIFFNESS = 90.0
        private const val IMPACT_DAMPING_RATIO = 0.7
        private const val FOV_STIFFNESS = 140.0
        private const val FOV_DAMPING_RATIO = 0.45
        private const val ROLL_STIFFNESS = 50.0
        private const val ROLL_DAMPING_RATIO = 0.6

        fun nearest(reference: Double, angle: Double): Double {
            var diff = (angle - reference) % 360.0
            if (abs(diff) > 180.0) diff -= Math.copySign(360.0, diff)
            return reference + diff
        }
    }
}
