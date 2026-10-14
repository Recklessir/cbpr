package com.cbpr.battle

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BattleSessionTest {
    private class FakeView(
        override val id: UUID = UUID.randomUUID(),
        var supported: Boolean = true,
        var available: Boolean = true,
        var mine: String? = "mine",
        var foe: String? = "foe",

        var secondMine: String? = null,
        var secondFoe: String? = null,
        val world: MutableSet<String> = mutableSetOf("mine", "foe"),
    ) : BattleView<String, String> {
        override fun supported() = supported
        override fun available() = available
        override fun mons() = listOfNotNull(mine?.let { BattleMember("p1a", it) }, secondMine?.let { BattleMember("p1b", it) })
        override fun foes() = listOfNotNull(foe?.let { BattleMember("p2a", it) }, secondFoe?.let { BattleMember("p2b", it) })
        override fun entity(battler: String, current: String?) = battler.takeIf { it in world }
    }

    private fun session() = BattleSession<String, String>()

    @Test
    fun anUnsupportedBattleIsIgnoredAndStaysIgnored() {
        val session = session()
        val view = FakeView(supported = false)
        assertEquals(BattleSession.Admit.REFUSED, session.admit(view))
        view.supported = true
        assertEquals(BattleSession.Admit.REFUSED, session.admit(view))
        assertEquals(view.id, session.ignored)
        assertNull(session.tracked)
    }

    @Test
    fun aSupportedBattleIsNewOnceThenTracked() {
        val session = session()
        val view = FakeView()
        assertEquals(BattleSession.Admit.NEW, session.admit(view))
        assertEquals(BattleSession.Admit.TRACKED, session.admit(view))
        assertEquals(view.id, session.tracked)
    }

    @Test
    fun findsBothPokemonInTheWorld() {
        val session = session()
        val view = FakeView()
        assertTrue(session.findSubjects(view))
        assertEquals("mine", session.mon)
        assertEquals("foe", session.foe)
        assertEquals("mine", session.monBattler)
        assertEquals("foe", session.foeBattler)
    }

    @Test
    fun aPokemonNotYetInTheWorldIsNullUntilItAppears() {
        val session = session()
        val view = FakeView(world = mutableSetOf("mine"))
        session.findSubjects(view)
        assertEquals("mine", session.mon)
        assertNull(session.foe)
        assertEquals("foe", session.foeBattler)
        view.world += "foe"
        session.findSubjects(view)
        assertEquals("foe", session.foe)
    }

    @Test
    fun aDoublesRosterHoldsBothPokemonOfEachSideInSlotOrder() {
        val session = session()
        val view = FakeView(secondMine = "mine2", secondFoe = "foe2", world = mutableSetOf("mine", "mine2", "foe", "foe2"))
        session.findSubjects(view)
        assertEquals(listOf("p1a" to "mine", "p1b" to "mine2"), session.mons.map { it.slot to it.entity })
        assertEquals(listOf("p2a" to "foe", "p2b" to "foe2"), session.foes.map { it.slot to it.entity })
        assertEquals("mine", session.mon)
        assertEquals("foe", session.foe)
    }

    @Test
    fun offersACombatantBySlotOnEitherSide() {
        val session = session()
        session.findSubjects(FakeView(secondMine = "mine2", secondFoe = "foe2", world = mutableSetOf("mine", "mine2", "foe")))
        assertEquals("mine2", session.combatant("p1b")?.entity)
        assertEquals("foe", session.combatant("p2a")?.entity)
        assertNull(session.combatant("p2b")?.entity, "in the roster but not in the world yet")
        assertEquals("foe2", session.combatant("p2b")?.battler)
        assertNull(session.combatant("p1c"))
    }

    @Test
    fun aPokemonNotYetInTheWorldStaysInTheRosterWithNoEntity() {
        val session = session()
        val view = FakeView(secondMine = "mine2", world = mutableSetOf("mine", "foe"))
        session.findSubjects(view)
        assertEquals(listOf("mine", null), session.mons.map { it.entity })
        assertEquals(listOf("mine", "mine2"), session.mons.map { it.battler })
        view.world += "mine2"
        session.findSubjects(view)
        assertEquals(listOf("mine", "mine2"), session.mons.map { it.entity })
    }

    @Test
    fun theFirstPokemonInTheWorldIsThePrimaryOne() {
        val session = session()
        val view = FakeView(secondMine = "mine2", world = mutableSetOf("mine2", "foe"))
        session.findSubjects(view)
        assertEquals("mine2", session.mon)
        assertEquals("mine2", session.monBattler)
    }

    @Test
    fun anEmptiedSlotLeavesTheRoster() {
        val session = session()
        val view = FakeView(secondMine = "mine2", world = mutableSetOf("mine", "mine2", "foe"))
        session.findSubjects(view)
        view.secondMine = null
        session.findSubjects(view)
        assertEquals(listOf("p1a"), session.mons.map { it.slot })
    }

    @Test
    fun anUnreadableViewLeavesThePokemonAsTheyWere() {
        val session = session()
        val view = FakeView()
        session.findSubjects(view)
        view.available = false
        view.mine = null
        assertFalse(session.findSubjects(view))
        assertEquals("mine", session.mon)
        assertEquals("mine", session.monBattler)
    }

    @Test
    fun givesUpWaitingAfterThreeSecondsAndIgnoresTheBattle() {
        val session = session()
        val view = FakeView()
        session.admit(view)
        repeat(60) { assertFalse(session.waitedTooLong(0.05)) }
        assertTrue(session.waitedTooLong(0.05))
        assertEquals(view.id, session.ignored)
        assertEquals(BattleSession.Admit.REFUSED, session.admit(view))
    }

    @Test
    fun aPokemonLostAfterFramingIsWaitedForNotGivenUpOn() {
        val session = session()
        val view = FakeView()
        session.admit(view)
        session.framed = true
        repeat(600) { assertFalse(session.waitedTooLong(0.05)) }
        assertNull(session.ignored)
        assertEquals(BattleSession.Admit.TRACKED, session.admit(view))
    }

    @Test
    fun aBattleAdmittedAfterAnotherStartsUnframed() {
        val session = session()
        session.admit(FakeView())
        session.framed = true
        session.resuming = true
        assertEquals(BattleSession.Admit.NEW, session.admit(FakeView()))
        assertFalse(session.framed)
        assertFalse(session.resuming)
    }

    @Test
    fun aNewBattleHasNotBeenFramed() {
        val session = session()
        session.admit(FakeView())
        session.framed = true
        session.battleEnded(keepSubjects = false)
        assertFalse(session.framed)
    }

    @Test
    fun aNewBattleStartsItsWaitAgain() {
        val session = session()
        session.admit(FakeView())
        repeat(50) { session.waitedTooLong(0.05) }
        session.battleEnded(keepSubjects = false)
        session.admit(FakeView())
        repeat(50) { assertFalse(session.waitedTooLong(0.05)) }
    }

    @Test
    fun aBattleEndingKeepsThePokemonOfAShotStillPlaying() {
        val session = session()
        session.admit(FakeView())
        session.findSubjects(FakeView())
        session.field.apply("raindance", true)
        session.resuming = true
        session.battleEnded(keepSubjects = true)
        assertEquals("mine", session.mon)
        assertEquals("foe", session.foe)
        assertNull(session.tracked)
        assertNull(session.ignored)
        assertNull(session.field.current)
        assertFalse(session.resuming)
    }

    @Test
    fun aBattleEndingWithNoShotClearsThePokemon() {
        val session = session()
        session.findSubjects(FakeView())
        session.battleEnded(keepSubjects = false)
        assertNull(session.mon)
        assertNull(session.foe)
        assertNull(session.monBattler)
        assertNull(session.foeBattler)
    }

    @Test
    fun aFailureIgnoresTheBattleButKeepsTheFieldAndResumeUntilTheBattleEnds() {
        val session = session()
        val view = FakeView()
        session.admit(view)
        session.field.apply("raindance", true)
        session.resuming = true
        session.fail()
        assertNull(session.tracked)
        assertEquals(view.id, session.ignored)
        assertEquals("raindance", session.field.current)
        assertTrue(session.resuming)
        session.battleEnded(keepSubjects = false)
        assertNull(session.ignored)
        assertNull(session.field.current)
    }

    @Test
    fun changingLevelClearsEverything() {
        val session = session()
        session.admit(FakeView())
        session.findSubjects(FakeView())
        session.field.apply("hail", true)
        session.levelChanged()
        assertNull(session.tracked)
        assertNull(session.mon)
        assertNull(session.field.current)
    }
}
