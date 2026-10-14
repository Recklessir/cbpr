package com.cbpr.battle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VolatileKeysTest {
    @Test
    fun afflictionKeysGiveTheirIdAndOtherStartKeysNone() {
        assertEquals("confusion", VolatileKeys.of("cobblemon.battle.start.confusion"))
        assertEquals("leechseed", VolatileKeys.of("cobblemon.battle.start.leechSeed"))
        assertNull(VolatileKeys.of("cobblemon.battle.start.substitute"))
        assertNull(VolatileKeys.of("cobblemon.battle.start.perish"))
        assertNull(VolatileKeys.of("cobblemon.battle.start.dynamax"))
        assertNull(VolatileKeys.of("cobblemon.battle.end.confusion"))
        assertEquals(MessageKind.VOLATILE, MessageKind.of("cobblemon.battle.start.taunt"))
        assertEquals(MessageKind.DYNAMAX, MessageKind.of("cobblemon.battle.start.dynamax"))
    }

    @Test
    fun theGhostCurseAfflictsItsSecondArgument() {
        assertEquals(1, VolatileKeys.afflictedArgument("curse", 2))
        assertEquals(0, VolatileKeys.afflictedArgument("curse", 1))
        assertEquals(0, VolatileKeys.afflictedArgument("confusion", 2))
    }

    @Test
    fun everyConditionHasAWordInTheLanguageFile() {
        val text = assertNotNullStream().reader().readText()
        val words = com.google.gson.JsonParser.parseString(text).asJsonObject
        for (id in VolatileKeys.CONDITIONS) assertEquals(true, words.has("cbpr.volatile.$id"), id)
    }

    private fun assertNotNullStream() = checkNotNull(javaClass.getResourceAsStream("/assets/cbpr/lang/en_us.json"))
}
