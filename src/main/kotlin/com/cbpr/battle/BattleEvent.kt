package com.cbpr.battle

sealed interface BattleEvent {

    class MoveUsed(val attacker: String, val moveId: String, val target: String? = null) : BattleEvent

    class Damage(val lost: Double, val onMonSide: Boolean? = null, val pokemon: String? = null) : BattleEvent

    class Crit(override val message: MessageText? = null) : BattleEvent, Stamped {
        override val stamp get() = StampKind.CRIT
    }

    class SuperEffective(override val message: MessageText? = null) : BattleEvent, Stamped {
        override val stamp get() = StampKind.SUPER_EFFECTIVE
    }

    class Fainted(val onMonSide: Boolean, val pokemon: String? = null) : BattleEvent

    class SentOut(val onMonSide: Boolean, val pokemon: String? = null) : BattleEvent

    class Outcome(val winner: String) : BattleEvent

    class NoEffect(override val message: MessageText? = null) : BattleEvent, Stamped {
        override val stamp get() = StampKind.NO_EFFECT
    }

    class Gimmick(val pokemon: String, val style: com.cbpr.camera.GimmickStyle) : BattleEvent

    class Transformation(val pokemon: String) : BattleEvent

    class Dynamax(val pokemon: String) : BattleEvent

    class Field(val id: String, val started: Boolean, val message: MessageText?) : BattleEvent

    class Volatile(val pokemon: String, val id: String) : BattleEvent

    class StatChange(val pokemon: String, val stat: String, val stages: Int) : BattleEvent
}

class MessageText(val key: String, val args: List<String> = emptyList())

enum class StampKind(val rgb: Int) {
    CRIT(0xFFD25A), SUPER_EFFECTIVE(0xFF8A3D), NO_EFFECT(0xB4C0D4), FIELD(0xCFE3FF),
}

interface Stamped {
    val stamp: StampKind
    val message: MessageText?
}

object HpShare {
    fun of(flat: Boolean, hp: Float, max: Float): Double? = when {
        !flat -> hp.toDouble()
        max > 0f -> (hp / max).toDouble()
        else -> null
    }
}

class ImpactFlags(val crit: Boolean, val superEffective: Boolean) {
    val strong: Boolean get() = crit || superEffective
}

class ImpactTracker {
    private var crit = Double.NEGATIVE_INFINITY
    private var superEffective = Double.NEGATIVE_INFINITY
    private var lastDamage = Double.NEGATIVE_INFINITY

    fun damage(now: Double): ImpactFlags {
        lastDamage = now
        val flags = ImpactFlags(now - crit <= BEFORE_WINDOW, now - superEffective <= BEFORE_WINDOW)
        if (flags.crit) crit = Double.NEGATIVE_INFINITY
        if (flags.superEffective) superEffective = Double.NEGATIVE_INFINITY
        return flags
    }

    fun modifier(event: BattleEvent, now: Double): ImpactFlags? {
        if (event is BattleEvent.Crit) crit = now
        if (event is BattleEvent.SuperEffective) superEffective = now
        if (now - lastDamage > AFTER_WINDOW) return null
        val flags = ImpactFlags(now - crit <= AFTER_WINDOW, now - superEffective <= AFTER_WINDOW)
        if (flags.crit) crit = Double.NEGATIVE_INFINITY
        if (flags.superEffective) superEffective = Double.NEGATIVE_INFINITY
        return flags
    }

    fun clear() {
        crit = Double.NEGATIVE_INFINITY
        superEffective = Double.NEGATIVE_INFINITY
        lastDamage = Double.NEGATIVE_INFINITY
    }

    private companion object {
        const val BEFORE_WINDOW = 1.5
        const val AFTER_WINDOW = 0.5
    }
}

class BattleEventQueue(private val capacity: Int = 16) {
    private val events = ArrayDeque<BattleEvent>()

    @Synchronized
    fun post(event: BattleEvent) {
        if (events.lastOrNull()?.let { sameAs(it, event) } == true) return
        if (events.size >= capacity) events.removeFirst()
        events.addLast(event)
    }

    @Synchronized
    fun drain(): List<BattleEvent> {
        val out = events.toList()
        events.clear()
        return out
    }

    @Synchronized
    fun clear() = events.clear()

    private fun sameAs(a: BattleEvent, b: BattleEvent): Boolean = when {
        a is BattleEvent.MoveUsed && b is BattleEvent.MoveUsed -> a.attacker == b.attacker && a.moveId == b.moveId && a.target == b.target
        a is BattleEvent.Crit && b is BattleEvent.Crit -> true
        a is BattleEvent.SuperEffective && b is BattleEvent.SuperEffective -> true
        a is BattleEvent.NoEffect && b is BattleEvent.NoEffect -> true
        a is BattleEvent.Gimmick && b is BattleEvent.Gimmick -> a.pokemon == b.pokemon && a.style == b.style
        a is BattleEvent.Transformation && b is BattleEvent.Transformation -> a.pokemon == b.pokemon
        a is BattleEvent.Dynamax && b is BattleEvent.Dynamax -> a.pokemon == b.pokemon
        a is BattleEvent.Volatile && b is BattleEvent.Volatile -> a.pokemon == b.pokemon && a.id == b.id
        a is BattleEvent.Field && b is BattleEvent.Field -> a.id == b.id && a.started == b.started
        a is BattleEvent.StatChange && b is BattleEvent.StatChange -> a.pokemon == b.pokemon && a.stat == b.stat && a.stages == b.stages
        a is BattleEvent.Fainted && b is BattleEvent.Fainted -> a.onMonSide == b.onMonSide && a.pokemon == b.pokemon
        a is BattleEvent.SentOut && b is BattleEvent.SentOut -> a.onMonSide == b.onMonSide && a.pokemon == b.pokemon
        a is BattleEvent.Outcome && b is BattleEvent.Outcome -> a.winner == b.winner
        else -> false
    }
}
