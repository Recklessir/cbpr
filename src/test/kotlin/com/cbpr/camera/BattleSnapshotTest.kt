package com.cbpr.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BattleSnapshotTest {
    private val monA = Point(0.0, 1.0, 0.0)
    private val monB = Point(4.0, 1.0, 0.0)
    private val foeA = Point(0.0, 1.0, 9.0)
    private val doubles = BattleSnapshot(
        listOf(BattleSnapshot.Member("p1a", monA, 1.0), BattleSnapshot.Member("p1b", monB, 3.0)),
        listOf(BattleSnapshot.Member("p2a", foeA, 2.0)),
        commandedSlot = "p1b",
    )

    @Test
    fun aSidesGroupIsItsMiddleAndTheRoomItTakes() {
        assertEquals(Point(2.0, 1.0, 0.0), doubles.monCentre)
        assertEquals(7.0, doubles.monGroupSize)
        assertEquals(foeA, doubles.foeCentre)
        assertEquals(2.0, doubles.foeGroupSize)
    }

    @Test
    fun aSidesSizeIsItsFirstPokemon() {
        assertEquals(1.0, doubles.monSize)
        assertEquals(2.0, doubles.foeSize)
    }

    @Test
    fun aSlotInTheWorldGivesItsOwnPointAndSize() {
        assertEquals(monB, doubles.point("p1b", monA))
        assertEquals(3.0, doubles.size("p1b", 7.0))
    }

    @Test
    fun anUnknownOrMissingSlotGivesTheSideForPointsAndSizesAlike() {
        assertEquals(foeA, doubles.point("p2c", foeA))
        assertEquals(foeA, doubles.point(null, foeA))
        assertEquals(7.0, doubles.size("p1c", 7.0))
        assertEquals(7.0, doubles.size(null, 7.0))
    }

    @Test
    fun theCommandedMonIsOneOfTheMonsInTheWorld() {
        assertEquals("p1b", doubles.commanded?.slot)
        assertNull(BattleSnapshot(doubles.mons, doubles.foes, commandedSlot = "p1c").commanded)
        assertNull(BattleSnapshot(doubles.mons, doubles.foes, commandedSlot = "p2a").commanded)
    }

    @Test
    fun aSideWithNobodyInTheWorldHasNoCentreAndAnOrdinarySize() {
        val empty = BattleSnapshot()
        assertNull(empty.monCentre)
        assertEquals(BattleSnapshot.ORDINARY_SIZE, empty.monGroupSize)
        assertEquals(BattleSnapshot.ORDINARY_SIZE, empty.foeSize)
    }
}
