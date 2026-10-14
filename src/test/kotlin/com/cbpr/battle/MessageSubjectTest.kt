package com.cbpr.battle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageSubjectTest {
    private fun roster(owner: String, vararg members: Pair<String, String>) =
        MessageSubject.Roster(owner, members.map { MessageSubject.Named(it.first, it.second) })

    private val mons = roster("Reckshin", "p1a" to "Charmander", "p1b" to "Bulbasaur")
    private val foes = roster("Wild", "p2a" to "Mew", "p2b" to "Mewtwo")

    private fun resolve(text: String) = MessageSubject.resolve(text, mons, foes)

    @Test
    fun theNameInTheTextPicksTheSlot() {
        assertEquals(MessageSubject(true, "p1b"), resolve("Reckshin's Bulbasaur"))
        assertEquals(MessageSubject(true, "p1a"), resolve("Reckshin's Charmander"))
    }

    @Test
    fun matchingIgnoresCaseAndTakesWholeWordsOnly() {
        assertEquals(MessageSubject(false, "p2b"), resolve("Wild MEWTWO"))
        assertEquals(MessageSubject(false, "p2a"), resolve("Wild Mew"))
    }

    @Test
    fun theSideIsKnownWhenTheSlotIsNot() {
        assertEquals(MessageSubject(true, null), resolve("Reckshin's Nicknamed Thing"))
    }

    @Test
    fun twoPokemonNamedTheSameCannotBeToldApart() {
        assertEquals(MessageSubject(false, null), MessageSubject.resolve("Wild Rattata", mons, roster("Wild", "p2a" to "Rattata", "p2b" to "Rattata")))
    }

    @Test
    fun aSideWithOneActivePokemonIsThatOne() {
        assertEquals(MessageSubject(true, "p1a"), MessageSubject.resolve("Reckshin's Thing", roster("Reckshin", "p1a" to "Charmander"), foes))
    }

    @Test
    fun anEmptySideStillHasItsOwner() {
        assertEquals(MessageSubject(false, null), MessageSubject.resolve("Wild Absol", mons, roster("Wild")))
        assertNull(MessageSubject.resolve("Nobody", mons, roster("")).monSide)
    }
}
