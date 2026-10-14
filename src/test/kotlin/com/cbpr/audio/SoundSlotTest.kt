package com.cbpr.audio

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SoundSlotTest {
    @Test
    fun slotIdsAreUniqueAndLowercase() {
        val ids = SoundSlot.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(ids, ids.map { it.lowercase() })
    }

    @Test
    fun theShippedSoundsJsonNamesExactlyTheSlots() {
        val text = assertNotNull(javaClass.getResourceAsStream("/assets/cbpr/sounds.json")).reader().readText()
        val events = JsonParser.parseString(text).asJsonObject
        assertEquals(SoundSlot.entries.map { it.id }.toSet(), events.keySet())

        for (slot in SoundSlot.entries) {
            val entry = events.getAsJsonObject(slot.id).getAsJsonArray("sounds")[0]
            val name = if (entry.isJsonObject) entry.asJsonObject.get("name").asString else entry.asString
            assertEquals("cbpr:${slot.id}", name)
        }
    }

    @Test
    fun theJarShipsAFileForEverySlotExceptTheSilentOnes() {
        val silent = setOf(
            "turn_start", "status_applied", "transformation", "flee",
        )
        val shipped = SoundSlot.entries.map { it.id }.filter { javaClass.getResource("/assets/cbpr/sounds/$it.ogg") != null }
        assertEquals(SoundSlot.entries.map { it.id }.toSet() - silent, shipped.toSet())
    }

    @Test
    fun fromIdFindsASlotAndIgnoresUnknownOrMissingNames() {
        assertEquals(SoundSlot.CRIT, SoundSlot.fromId("crit"))
        assertNull(SoundSlot.fromId("nope"))
        assertNull(SoundSlot.fromId(null))
    }

    @Test
    fun everySlotNamedByAShippedMoveProfileExists() {
        for (name in listOf("generic", "rush", "projectile", "beam", "area", "self", "status", "multihit", "charge", "field")) {
            val text = javaClass.getResourceAsStream("/assets/cbpr/move_profiles/$name.json")!!.reader().readText()
            val sound = JsonParser.parseString(text).asJsonObject.get("sound")?.asString
            if (sound != null) assertNotNull(SoundSlot.fromId(sound), "$name names unknown slot $sound")
        }
    }
}
