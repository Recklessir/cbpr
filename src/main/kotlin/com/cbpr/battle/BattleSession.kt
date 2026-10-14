package com.cbpr.battle

import com.cbpr.ui.MenuHold
import java.util.UUID

interface BattleView<E : Any, B : Any> {
    val id: UUID

    fun supported(): Boolean

    fun available(): Boolean

    fun mons(): List<BattleMember<B>>

    fun foes(): List<BattleMember<B>>

    fun monOwner(): String = ""

    fun foeOwner(): String = ""

    fun entity(battler: B, current: E?): E?
}

object SupportedFormats {
    fun supports(name: String, actorsPerSide: Int, slotsPerActor: Int, doublesEnabled: Boolean, triplesEnabled: Boolean): Boolean = actorsPerSide == 1 && when (name) {
        "singles" -> slotsPerActor == 1
        "doubles" -> slotsPerActor == 2 && doublesEnabled
        "triples" -> slotsPerActor == 3 && triplesEnabled
        else -> false
    }
}

class BattleMember<B : Any>(val slot: String, val battler: B)

class Combatant<E : Any, B : Any>(val slot: String, val battler: B, val entity: E?)

class BattleSession<E : Any, B : Any> {
    enum class Admit { REFUSED, NEW, TRACKED }

    var tracked: UUID? = null
        private set
    var ignored: UUID? = null
        private set

    var mons: List<Combatant<E, B>> = emptyList()
        private set
    var foes: List<Combatant<E, B>> = emptyList()
        private set
    var monOwner = ""
        private set
    var foeOwner = ""
        private set

    private val primaryMon get() = primary(mons)
    private val primaryFoe get() = primary(foes)
    val mon: E? get() = primaryMon?.entity
    val foe: E? get() = primaryFoe?.entity
    val monBattler: B? get() = primaryMon?.battler
    val foeBattler: B? get() = primaryFoe?.battler

    val field = FieldState()
    val handback = ControlHandback()
    val menuHold = MenuHold()
    var resuming = false

    var framed = false

    private var waitingSeconds = 0.0

    fun admit(view: BattleView<E, B>): Admit {
        if (view.id == ignored) return Admit.REFUSED
        if (view.id == tracked) return Admit.TRACKED
        if (!view.supported()) {
            ignored = view.id
            return Admit.REFUSED
        }
        tracked = view.id
        waitingSeconds = 0.0
        framed = false
        resuming = false
        return Admit.NEW
    }

    fun findSubjects(view: BattleView<E, B>): Boolean {
        if (!view.available()) return false
        mons = roster(view.mons(), mons, view)
        foes = roster(view.foes(), foes, view)
        monOwner = view.monOwner()
        foeOwner = view.foeOwner()
        return true
    }

    private fun roster(members: List<BattleMember<B>>, before: List<Combatant<E, B>>, view: BattleView<E, B>): List<Combatant<E, B>> =
        members.map { member -> Combatant(member.slot, member.battler, view.entity(member.battler, before.firstOrNull { it.slot == member.slot }?.entity)) }

    fun combatant(slot: String): Combatant<E, B>? = (mons + foes).firstOrNull { it.slot == slot }

    private fun primary(side: List<Combatant<E, B>>): Combatant<E, B>? = side.firstOrNull { it.entity != null } ?: side.firstOrNull()

    fun waitedTooLong(seconds: Double): Boolean {
        if (framed) return false
        waitingSeconds += seconds
        if (waitingSeconds <= SUBJECT_WAIT_SECONDS) return false
        ignoreTracked()
        return true
    }

    fun ignoreTracked() {
        ignored = tracked
    }

    fun fail() {
        ignoreTracked()
        tracked = null
    }

    fun battleEnded(keepSubjects: Boolean) {
        field.clear()
        tracked = null
        ignored = null
        handback.reset()
        menuHold.reset()
        resuming = false
        framed = false
        if (!keepSubjects) clearSubjects()
    }

    fun levelChanged() = battleEnded(keepSubjects = false)

    fun clearSubjects() {
        mons = emptyList()
        foes = emptyList()
        monOwner = ""
        foeOwner = ""
    }

    private companion object {
        const val SUBJECT_WAIT_SECONDS = 3.0
    }
}
