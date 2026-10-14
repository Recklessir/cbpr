package com.cbpr.battle

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpectatorSideTest {
    private val id = UUID.randomUUID()

    @Test
    fun theNearerSideIsTheMons() {
        assertTrue(SpectatorSide().firstIsMon(id) { 3.0 to 8.0 }!!)
        assertFalse(SpectatorSide().firstIsMon(id) { 8.0 to 3.0 }!!)
    }

    @Test
    fun aTieGoesToTheFirstSide() {
        assertTrue(SpectatorSide().firstIsMon(id) { 5.0 to 5.0 }!!)
    }

    @Test
    fun undecidedWhileASideIsNotInTheWorld() {
        val side = SpectatorSide()
        assertNull(side.firstIsMon(id) { 3.0 to null })
        assertNull(side.firstIsMon(id) { null to 3.0 })
        assertTrue(side.firstIsMon(id) { 3.0 to 4.0 }!!)
    }

    @Test
    fun theChoiceIsKeptAndTheDistancesAreNotAskedAgain() {
        val side = SpectatorSide()
        side.firstIsMon(id) { 3.0 to 8.0 }
        assertTrue(side.firstIsMon(id) { error("asked again") }!!)
    }

    @Test
    fun aNewBattleChoosesAgain() {
        val side = SpectatorSide()
        side.firstIsMon(id) { 3.0 to 8.0 }
        assertEquals(false, side.firstIsMon(UUID.randomUUID()) { 8.0 to 3.0 })
    }
}
