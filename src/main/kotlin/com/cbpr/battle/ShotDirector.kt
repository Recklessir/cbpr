package com.cbpr.battle

import com.cbpr.audio.SoundSlot
import com.cbpr.camera.BattleCamera
import com.cbpr.camera.BeatPlan
import com.cbpr.camera.ShotPlan
import com.cbpr.camera.Shots
import com.cbpr.config.CbprConfig

class ShotDirector(
    private val camera: BattleCamera,
    private val profiles: () -> ProfileSet,
    private val settings: () -> CbprConfig,
    private val lookupMove: (String) -> MoveInfo?,

    private val mons: () -> MessageSubject.Roster,
    private val foes: () -> MessageSubject.Roster,
    private val presentation: ShotPresentation = ShotPresentation.None,

    private val sizeOf: (Boolean, String?) -> Double = { _, _ -> 1.0 },

    private val trainerPosition: () -> com.cbpr.camera.Point? = { null },
) {
    class MoveUsed(val moveId: String, val profile: MoveProfile)

    private val impacts = ImpactTracker()
    private var now = 0.0
    private var attackerIsMon: Boolean? = null
    private var targetSlot: String? = null
    private var moveSound: SoundSlot? = null

    private fun subject(text: String) = MessageSubject.resolve(text, mons(), foes())

    fun handle(events: List<BattleEvent>, dt: Double) {
        now += dt
        for (event in events) {
            if (event is Stamped) stamp(event)
            when (event) {
                is BattleEvent.MoveUsed -> start(event)
                is BattleEvent.Damage -> {
                    if (settings().damageNumbers && event.lost != 0.0) presentation.damageNumber(event.lost, event.onMonSide, event.pokemon)
                    if (event.lost > 0.0) {
                        camera.impact(impacts.damage(now))
                        moveSound?.let { presentation.sound(it, attackerIsMon?.not(), event.pokemon ?: targetSlot) }
                    }
                }
                is BattleEvent.Crit, is BattleEvent.SuperEffective -> {
                    impacts.modifier(event, now)?.let(camera::upgradeImpact)
                    presentation.sound(if (event is BattleEvent.Crit) SoundSlot.CRIT else SoundSlot.SUPER_EFFECTIVE, attackerIsMon?.not(), targetSlot)
                }
                is BattleEvent.NoEffect -> presentation.sound(SoundSlot.NOT_EFFECTIVE, attackerIsMon?.not(), targetSlot)
                is BattleEvent.Fainted -> {
                    presentation.sound(SoundSlot.FAINT, event.onMonSide, event.pokemon)
                    beat(settings().faintShot, BeatPlan.Kind.FAINT, event.onMonSide, darkened = false, subjectSlot = event.pokemon)
                }
                is BattleEvent.SentOut -> {

                    val kind = if (event.onMonSide) BeatPlan.Kind.SWITCH else BeatPlan.Kind.SEND_OUT
                    beat(settings().sendOutSwoop, kind, event.onMonSide, darkened = false, subjectSlot = event.pokemon)
                }
                is BattleEvent.Gimmick -> {
                    val (side, slot) = subject(event.pokemon)
                    presentation.sound(SoundSlot.SPECIAL_HIGHLIGHT, side, slot)
                    val config = settings()
                    if (side != null && config.specialShot && config.cinematicShots) {
                        camera.playBeat(BeatPlan(BeatPlan.Kind.GIMMICK, side, config.reducedMotion, darkened = false, style = event.style, subjectSlot = slot))
                    }
                }
                is BattleEvent.Transformation -> subject(event.pokemon).let { presentation.sound(SoundSlot.TRANSFORMATION, it.monSide, it.slot) }
                is BattleEvent.Dynamax -> {
                    val (side, slot) = subject(event.pokemon)
                    presentation.sound(SoundSlot.SPECIAL_HIGHLIGHT, side, slot)
                    val config = settings()
                    if (side != null && config.specialShot && config.cinematicShots) {
                        camera.playBeat(BeatPlan(BeatPlan.Kind.DYNAMAX, side, config.reducedMotion, darkened = false, subjectSize = sizeOf(side, slot), otherSize = sizeOf(!side, null), subjectSlot = slot))
                    }
                }
                is BattleEvent.StatChange -> if (settings().statusCues) subject(event.pokemon).takeIf { it.monSide != null }?.let {
                    presentation.cue(StatStages.label(event.stat, event.stages), StatStages.rgb(event.stages), it.monSide, it.slot)
                }
                is BattleEvent.Volatile -> if (settings().statusCues) subject(event.pokemon).takeIf { it.monSide != null }?.let {
                    presentation.volatile(event.id, it.monSide, it.slot)
                }
                is BattleEvent.Field -> field(event)
                is BattleEvent.Outcome -> finish(event)
            }
        }
    }

    fun reset() {
        impacts.clear()
        now = 0.0
        attackerIsMon = null
        targetSlot = null
        moveSound = null
    }

    private fun start(event: BattleEvent.MoveUsed) {
        val config = settings()

        val move = lookupMove(event.moveId) ?: MoveInfo(event.moveId, "", "", "")
        val profile = profiles().resolve(move)
        val attacker = subject(event.attacker)
        attackerIsMon = attacker.monSide
        targetSlot = event.target?.let { subject(it).slot }
        moveSound = SoundSlot.fromId(profile.sound)

        val monSide = attacker.monSide ?: return
        if (config.callouts && profile.callout) presentation.callout(MoveUsed(event.moveId, profile))
        if (!config.cinematicShots) return
        camera.play(ShotPlan(profile, monSide, config.reducedMotion, config.hitStop, config.critSpecials, attacker.slot, targetSlot))
    }

    private fun field(event: BattleEvent.Field) {
        val config = settings()
        if (!config.weatherWash) return
        presentation.field(event.id, event.started)
        event.message?.let { presentation.stamp(StampKind.FIELD, it) }
        if (event.started && config.cinematicShots && camera.beatKind == null) {
            camera.playBeat(BeatPlan(BeatPlan.Kind.WEATHER, subjectIsMon = true, reducedMotion = config.reducedMotion))
        }
    }

    private fun stamp(event: Stamped) {
        val message = event.message ?: return
        if (settings().impactStamps) presentation.stamp(event.stamp, message)
    }

    private fun beat(enabled: Boolean, kind: BeatPlan.Kind, subjectIsMon: Boolean, darkened: Boolean, subjectSlot: String? = null) {
        val config = settings()
        if (!enabled || !config.cinematicShots) return
        camera.playBeat(BeatPlan(kind, subjectIsMon, config.reducedMotion, darkened, trainer = trainerPosition(), preferRight = Shots.SWITCH_PREFER_RIGHT, subjectSlot = subjectSlot))
    }

    private fun finish(event: BattleEvent.Outcome) {
        val config = settings()
        val won = subject(event.winner).monSide ?: return
        if (config.callouts) presentation.result(won, event.winner)
        presentation.sound(if (won) SoundSlot.VICTORY else SoundSlot.DEFEAT, null)
        beat(config.victoryOrbit, BeatPlan.Kind.VICTORY, subjectIsMon = won, darkened = !won)
    }
}
