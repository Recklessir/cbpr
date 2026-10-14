package com.cbpr.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OcclusionBlocksTest {

    private val tunnel = { x: Int, y: Int, z: Int -> !(x in 0..1 && y in 0..1 && z >= 0) }
    private val frame = 1.0 / 60

    @Test
    fun keysAreUniqueIncludingNegativeCoordinates() {
        val keys = HashSet<Long>()
        for (x in -3..3) for (y in -3..3) for (z in -3..3) keys += OcclusionSelector.key(x, y, z)
        assertEquals(7 * 7 * 7, keys.size)
    }

    @Test
    fun aCameraInsideRockChoosesItsOwnBlockAndNearbyWalls() {
        val camera = Point(0.5, 0.5, -4.5)
        val picks = OcclusionSelector.select(camera, emptyList(), 0) { _, _, _ -> true }
        val own = picks.getValue(OcclusionSelector.key(0, 0, -5))
        assertTrue(own.near)
        assertTrue(picks.values.all { it.near })
        assertFalse(picks.containsKey(OcclusionSelector.key(0, 0, 5)), "far blocks are left alone")
    }

    @Test
    fun theLineOfSightFadesBlocksButStopsShortOfThePokemon() {

        val camera = Point(0.5, 1.2, -6.5)
        val target = OcclusionSelector.Target(Point(0.5, 1.2, 4.0), 1.5)
        val picks = OcclusionSelector.select(camera, listOf(target), 0, tunnel)
        assertTrue(picks.containsKey(OcclusionSelector.key(0, 1, -4)), "rock on the line is chosen")
        assertFalse(picks.getValue(OcclusionSelector.key(0, 1, -4)).near)
        assertTrue(picks.keys.none { OcclusionSelector.run { it == key(0, 1, 2) } }, "open tunnel air is never chosen")
        assertTrue(picks.size <= OcclusionSelector.MAX_BLOCKS + 27)
    }

    @Test
    fun theGroundUnderThePokemonIsNeverChosenByTheLine() {
        val camera = Point(0.5, 1.2, -6.5)
        val target = OcclusionSelector.Target(Point(0.5, 1.2, 4.0), 1.5)
        val picks = OcclusionSelector.select(camera, listOf(target), 1) { _, _, _ -> true }
        assertTrue(picks.values.filter { !it.near }.all { it.y >= 1 })
    }

    @Test
    fun aChosenBlockIsHiddenAtOnceThenFadesAfterTheMeshHasHadTimeToRebuild() {
        val dirty = ArrayList<Triple<Int, Int, Int>>()
        val blocks = OcclusionBlocks { x, y, z -> dirty += Triple(x, y, z) }
        blocks.choose(mapOf(OcclusionSelector.key(3, 70, -20) to OcclusionPick(3, 70, -20, false)))
        assertTrue(blocks.hides(3, 70, -20))
        assertEquals(listOf(Triple(0, 4, -2)), dirty)
        val entry = blocks.entries.single()
        repeat(10) { blocks.step(frame) }
        assertEquals(1.0, entry.alpha.value, "still solid while the mesh rebuilds")
        repeat(60) {
            blocks.choose(mapOf(OcclusionSelector.key(3, 70, -20) to OcclusionPick(3, 70, -20, false)))
            blocks.step(frame)
        }
        assertEquals(OcclusionBlocks.FADED_ALPHA, entry.alpha.value, 0.02)
    }

    @Test
    fun aBlockNoLongerWantedFadesBackThenIsRestoredToTheMeshThenForgotten() {
        val dirty = ArrayList<Triple<Int, Int, Int>>()
        val blocks = OcclusionBlocks { x, y, z -> dirty += Triple(x, y, z) }
        val pick = mapOf(OcclusionSelector.key(0, 0, 0) to OcclusionPick(0, 0, 0, true))
        blocks.choose(pick)
        repeat(120) {
            blocks.choose(pick)
            blocks.step(frame)
        }
        assertEquals(0.0, blocks.entries.single().alpha.value, 0.02)
        dirty.clear()
        blocks.releaseAll()
        var guard = 0
        while (blocks.hides(0, 0, 0) && guard++ < 600) blocks.step(frame)
        assertFalse(blocks.hides(0, 0, 0), "the real block is back in the mesh")
        assertTrue(blocks.active, "but the see-through copy lingers so the swap is not seen")
        assertEquals(1, dirty.size)
        repeat(60) { blocks.step(frame) }
        assertFalse(blocks.active)
    }

    @Test
    fun chosenAgainWhileLingeringTakesTheBlockBackOutOfTheMesh() {
        val blocks = OcclusionBlocks { _, _, _ -> }
        val pick = mapOf(OcclusionSelector.key(0, 0, 0) to OcclusionPick(0, 0, 0, false))
        blocks.choose(pick)
        blocks.releaseAll()
        repeat(240) { if (blocks.entries.any { it.phase != OcclusionBlocks.Phase.LINGER } || !blocks.active) blocks.step(frame) }
        blocks.step(frame)
        blocks.choose(pick)
        assertTrue(blocks.hides(0, 0, 0))
    }

    @Test
    fun clearRestoresEverythingAtOnce() {
        val dirty = ArrayList<Triple<Int, Int, Int>>()
        val blocks = OcclusionBlocks { x, y, z -> dirty += Triple(x, y, z) }
        blocks.choose(mapOf(OcclusionSelector.key(0, 0, 0) to OcclusionPick(0, 0, 0, false), OcclusionSelector.key(40, 0, 0) to OcclusionPick(40, 0, 0, false)))
        dirty.clear()
        blocks.clear()
        assertFalse(blocks.active)
        assertFalse(blocks.hides(0, 0, 0))
        assertEquals(2, dirty.size)
    }

    @Test
    fun aCameraBelowTheGroundClearsTheEarthBetweenItAndThePokemonAsWell() {
        val camera = Point(0.5, -4.5, 0.5)
        val target = OcclusionSelector.Target(Point(0.5, 1.2, 6.0), 1.0, 1)
        val under = OcclusionSelector.select(camera, listOf(target), 0) { _, y, _ -> y < 1 }
        assertTrue(under.values.any { !it.near && it.y < 0 }, "earth below the ground is cleared")
        val above = OcclusionSelector.select(Point(0.5, 3.5, 0.5), listOf(target), 0) { _, y, _ -> y < 1 }
        assertTrue(above.values.none { it.y < 0 }, "but not while the camera is above it")
    }

    @Test
    fun theClearedViewIsWiderAtABigPokemonThanAtTheCamera() {
        val camera = Point(0.5, 5.5, -10.5)
        fun width(farRadius: Int, z: Int): Int {
            val picks = OcclusionSelector.select(camera, listOf(OcclusionSelector.Target(Point(0.5, 5.5, 8.0), 1.0, farRadius)), 0) { _, _, _ -> true }
            return picks.values.filter { it.z == z && !it.near }.map { it.x }.distinct().size
        }
        assertTrue(width(3, 6) > width(3, -9), "wider near the Pokemon")
        assertTrue(width(3, 6) > width(1, 6), "and wider for a bigger Pokemon")
    }

    @Test
    fun theQuickNoOutsideTheBoxNeverHidesAnythingAndInsideItOnlyTheChosenBlocks() {
        val blocks = OcclusionBlocks { _, _, _ -> }
        assertFalse(blocks.hides(0, 0, 0), "nothing is hidden at first")
        val picks = OcclusionSelector.select(Point(0.5, 0.5, -4.5), emptyList(), 0) { _, _, _ -> true }
        blocks.choose(picks)
        assertTrue(blocks.hides(0, 0, -5))
        assertFalse(blocks.hides(1000, 0, -5), "far outside the box")
        assertFalse(blocks.hides(0, 500, -5))
        assertFalse(blocks.hides(0, 0, 900))
        val chosen = picks.values.map { Triple(it.x, it.y, it.z) }.toSet()
        for (x in -3..3) for (y in -3..3) for (z in -9..-1) assertEquals((x to y to z).let { Triple(x, y, z) in chosen }, blocks.hides(x, y, z), "$x $y $z")
        blocks.clear()
        assertFalse(blocks.hides(0, 0, -5), "cleared")
    }
}
