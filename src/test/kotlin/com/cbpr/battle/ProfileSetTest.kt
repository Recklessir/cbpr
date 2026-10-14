package com.cbpr.battle

import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileSetTest {
    private fun profile(id: String, moves: Set<String> = emptySet(), types: Set<String> = emptySet()) =
        MoveProfile(id, moves, types, 0.2, 0.4, 1.0, 0.5, 0.7, 0.9, 30.0, 5.0, 0.5, 0.0, 0.05, true, null)

    private val set = ProfileSet(
        listOf(
            profile("generic"), profile("rush"), profile("projectile"), profile("area"), profile("self"),
            profile("status"), profile("field"),
            profile("beam", moves = setOf("hyperbeam", "solarbeam")),
            profile("firey", types = setOf("fire")),
        ),
    )

    private fun move(id: String, category: String, type: String = "normal", target: String = "normal") =
        MoveInfo(id, category, type, target)

    @Test
    fun aMoveClaimedByNameWinsOverItsCategory() {
        assertEquals("beam", set.resolve(move("hyperbeam", "special")).id)
        assertEquals("beam", set.resolve(move("solarbeam", "special", "grass")).id)
    }

    @Test
    fun physicalMovesRushAndSpecialMovesFireProjectiles() {
        assertEquals("rush", set.resolve(move("tackle", "physical")).id)
        assertEquals("projectile", set.resolve(move("ember", "special", "fire")).id)
    }

    @Test
    fun spreadMovesGetTheAreaShot() {
        assertEquals("area", set.resolve(move("earthquake", "physical", target = "allAdjacent")).id)
        assertEquals("area", set.resolve(move("surf", "special", target = "allAdjacentFoes")).id)
    }

    @Test
    fun statusMovesSplitBySelfFieldAndFoe() {
        assertEquals("self", set.resolve(move("swordsdance", "status", target = "self")).id)
        assertEquals("self", set.resolve(move("reflect", "status", target = "allySide")).id)
        assertEquals("field", set.resolve(move("raindance", "status", target = "all")).id)
        assertEquals("field", set.resolve(move("spikes", "status", target = "foeSide")).id)
        assertEquals("status", set.resolve(move("growl", "status", target = "allAdjacentFoes")).id)
        assertEquals("status", set.resolve(move("smokescreen", "status", target = "normal")).id)
    }

    @Test
    fun anUnknownCategoryFallsToTypeThenGeneric() {
        assertEquals("firey", set.resolve(move("weird", "mystery", type = "fire")).id)
        assertEquals("generic", set.resolve(move("weird", "mystery", type = "water")).id)
    }

    @Test
    fun anUnknownModdedMoveStillGetsAProfile() {
        assertEquals("generic", set.resolve(MoveInfo("moddedmove", "", "", "")).id)
    }

    @Test
    fun anEmptySetStillResolvesToTheBuiltInGeneric() {
        assertEquals("generic", ProfileSet(emptyList()).resolve(move("tackle", "physical")).id)
    }

    @Test
    fun categoryAndTargetMatchingIsCaseInsensitive() {
        assertEquals("rush", MoveArchetypes.of(MoveInfo("x", "PHYSICAL", "normal", "NORMAL")))
        assertEquals("area", MoveArchetypes.of(MoveInfo("x", "Special", "water", "AllAdjacent")))
    }

    @Test
    fun resolutionIsDeterministicWhenTwoProfilesClaimTheSameMove() {
        val twice = ProfileSet(listOf(profile("zeta", moves = setOf("tackle")), profile("alpha", moves = setOf("tackle"))))
        assertEquals("alpha", twice.resolve(move("tackle", "physical")).id)
    }
}
