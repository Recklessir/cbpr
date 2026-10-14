package com.cbpr.battle

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShippedProfilesTest {
    private val names = listOf("generic", "rush", "projectile", "beam", "area", "self", "status", "multihit", "charge", "field")

    private val problems = mutableListOf<String>()
    private val loaded: Map<String, MoveProfile> = names.associateWith { name ->
        val stream = checkNotNull(javaClass.getResourceAsStream("/assets/cbpr/move_profiles/$name.json")) { "missing $name.json" }
        val json = stream.use { JsonParser.parseReader(it.reader()).asJsonObject }
        checkNotNull(MoveProfile.parse(json) { problems += "$name: $it" }) { "$name.json has no usable id" }
    }

    @Test
    fun everyShippedProfileParsesWithoutAProblem() {
        assertTrue(problems.isEmpty(), problems.toString())
    }

    @Test
    fun eachFileIdMatchesItsFileName() {
        loaded.forEach { (name, profile) -> assertEquals(name, profile.id) }
    }

    @Test
    fun thereIsOneProfileForEveryArchetypeTheClassifierCanReturn() {
        val archetypes = setOf("rush", "projectile", "area", "self", "status", "field")
        archetypes.forEach { assertNotNull(loaded[it], "no profile for archetype $it") }
    }

    @Test
    fun noMoveIsClaimedByTwoProfiles() {
        val owners = HashMap<String, String>()
        loaded.values.forEach { profile ->
            profile.moves.forEach { move ->
                val previous = owners.put(move, profile.id)
                assertTrue(previous == null, "$move is claimed by both $previous and ${profile.id}")
            }
        }
    }

    @Test
    fun theNamedMovesLandOnTheirProfiles() {
        val set = ProfileSet(loaded.values)
        assertEquals("beam", set.resolve(MoveInfo("hyperbeam", "special", "normal", "normal")).id)
        assertEquals("charge", set.resolve(MoveInfo("solarbeam", "special", "grass", "normal")).id)
        assertEquals("multihit", set.resolve(MoveInfo("furyswipes", "physical", "normal", "normal")).id)
    }

    @Test
    fun theCommonMovesLandOnTheRightArchetypes() {
        val set = ProfileSet(loaded.values)
        assertEquals("rush", set.resolve(MoveInfo("scratch", "physical", "normal", "normal")).id)
        assertEquals("projectile", set.resolve(MoveInfo("ember", "special", "fire", "normal")).id)
        assertEquals("status", set.resolve(MoveInfo("smokescreen", "status", "normal", "normal")).id)
        assertEquals("status", set.resolve(MoveInfo("growl", "status", "normal", "allAdjacentFoes")).id)
        assertEquals("area", set.resolve(MoveInfo("earthquake", "physical", "ground", "allAdjacent")).id)
        assertEquals("self", set.resolve(MoveInfo("swordsdance", "status", "normal", "self")).id)
        assertEquals("field", set.resolve(MoveInfo("raindance", "status", "water", "all")).id)
    }

    @Test
    fun theAnticipationNeverExceedsWhatTheTimelineCanHold() {
        loaded.values.forEach { profile ->
            val longest = profile.anticipation + profile.approach + profile.waitForImpact
            assertTrue(longest < 6.0, "${profile.id} would hit the six second cap before it could finish: $longest")
            assertTrue(profile.hitStop <= 0.15, "${profile.id} hit-stop is too long to be harmless")
        }
    }
}
