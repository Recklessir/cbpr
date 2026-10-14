package com.cbpr.battle

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupportedFormatsTest {
    private fun supports(name: String, actors: Int, slots: Int, doubles: Boolean = true, triples: Boolean = true) = SupportedFormats.supports(name, actors, slots, doubles, triples)

    @Test
    fun singlesIsSupportedWhateverTheDoublesSwitchSays() {
        assertTrue(supports("singles", 1, 1))
        assertTrue(supports("singles", 1, 1, doubles = false))
    }

    @Test
    fun doublesIsSupportedOnlyWhileItsSwitchIsOn() {
        assertTrue(supports("doubles", 1, 2))
        assertFalse(supports("doubles", 1, 2, doubles = false))
    }

    @Test
    fun theCountsMustMatchTheName() {
        assertFalse(supports("singles", 1, 2))
        assertFalse(supports("doubles", 1, 1))
    }

    @Test
    fun freeForAllSharesTheSinglesCountsButIsNotSupported() {
        assertFalse(supports("freeforall", 1, 1))
    }

    @Test
    fun triplesIsSupportedOnlyWhileItsSwitchIsOn() {
        assertTrue(supports("triples", 1, 3))
        assertFalse(supports("triples", 1, 3, triples = false))
        assertTrue(supports("doubles", 1, 2, triples = false), "the doubles switch is separate")
    }

    @Test
    fun triplesNeedsThreeSlots() {
        assertFalse(supports("triples", 1, 2))
        assertFalse(supports("doubles", 1, 3))
    }

    @Test
    fun multiIsNotSupported() {
        assertFalse(supports("multi", 2, 1))
    }

    @Test
    fun aTeamOnASideIsNeverSupported() {
        assertFalse(supports("doubles", 2, 2))
        assertFalse(supports("singles", 2, 1))
    }
}
