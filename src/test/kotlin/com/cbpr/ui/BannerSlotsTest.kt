package com.cbpr.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BannerSlotsTest {
    private val real = Rect(0.0, 0.0, 10.0, 10.0)
    private val guiWidth = 400.0

    private fun slots(widgets: Int, screen: BattleScreen = BattleScreen.MOVES) = BannerSlots().also { it.begin(guiWidth, widgets, screen) }

    @Test
    fun handlesCountWithinTheirKindInDrawOrder() {
        val intake = slots(6)
        val handles = listOf(BannerKind.MOVE, BannerKind.MOVE, BannerKind.BACK, BannerKind.MOVE, BannerKind.GIMMICK)
            .map { intake.register(it, real)!!.handle }
        assertEquals(
            listOf(SlotHandle(BannerKind.MOVE, 0), SlotHandle(BannerKind.MOVE, 1), SlotHandle(BannerKind.BACK, 0), SlotHandle(BannerKind.MOVE, 2), SlotHandle(BannerKind.GIMMICK, 0)),
            handles,
        )
    }

    @Test
    fun aNewFrameStartsCountingAgain() {
        val intake = slots(2)
        intake.register(BannerKind.OPTION, real)
        intake.begin(guiWidth, 2, BattleScreen.MOVES)
        assertEquals(0, intake.register(BannerKind.OPTION, real)!!.handle.number)
    }

    @Test
    fun theArtOfEveryWidgetLiesInsideTheStripAndNoTwoOverlap() {
        val widgets = 20
        val intake = slots(widgets)
        val art = (0 until widgets).map { intake.register(BannerKind.SWITCH, real)!!.art } + intake.register(BannerKind.PREVIEW, real)!!.art
        for (rect in art) {
            assertTrue(rect.x >= guiWidth, "left of the strip")
            assertTrue(rect.x + rect.width <= guiWidth + intake.stripWidth, "right of the strip: $rect")
        }
        for (i in art.indices) for (j in i + 1 until art.size) {
            val a = art[i]
            val b = art[j]
            val apart = a.x + a.width <= b.x || b.x + b.width <= a.x || a.y + a.height <= b.y || b.y + b.height <= a.y
            assertTrue(apart, "$i and $j overlap")
        }
    }

    @Test
    fun theStripGrowsWithTheCount() {
        assertTrue(slots(40).stripWidth > slots(4).stripWidth)
    }

    @Test
    fun theWidgetsOverTheCeilingAreNotHostedAndTheRestAre() {
        val intake = slots(BannerSlots.MAX_HOSTED + 10)
        val results = List(BannerSlots.MAX_HOSTED + 10) { intake.register(BannerKind.ADDON, real) }
        assertEquals(BannerSlots.MAX_HOSTED, results.count { it != null })
        assertTrue(results.take(BannerSlots.MAX_HOSTED).all { it != null })
        assertTrue(results.drop(BannerSlots.MAX_HOSTED).all { it == null })
        assertTrue(intake.overCeiling)
    }

    @Test
    fun theCeilingCostsTheStripNothingBeyondItsSize() {
        assertEquals(slots(BannerSlots.MAX_HOSTED).stripWidth, slots(BannerSlots.MAX_HOSTED * 5).stripWidth)
        assertFalse(slots(BannerSlots.MAX_HOSTED).overCeiling)
    }

    @Test
    fun aWidgetThatRegistersLateAfterTheCountIsFullIsNotHosted() {
        val intake = slots(2)
        assertNotNull(intake.register(BannerKind.MOVE, real))
        assertNotNull(intake.register(BannerKind.MOVE, real))
        assertNull(intake.register(BannerKind.MOVE, real))
    }

    @Test
    fun theDescriptionPanelIsNeverTurnedAwayAndTakesNoWidgetRoom() {
        val intake = slots(1)
        assertNotNull(intake.register(BannerKind.PREVIEW, real))
        assertNotNull(intake.register(BannerKind.MOVE, real))
    }

    @Test
    fun anAddonWidgetContinuesTheStackOnTheMainMenuAndSitsInTheExtrasRowOnTheOtherScreens() {
        for ((screen, role) in listOf(BattleScreen.GENERAL to SlotRole.TILE, BattleScreen.MOVES to SlotRole.EXTRA, BattleScreen.PARTY to SlotRole.EXTRA)) {
            assertEquals(role, slots(1, screen).register(BannerKind.ADDON, real)!!.role, "$screen")
        }
        val intake = slots(4)
        assertEquals(
            listOf(SlotRole.TILE, SlotRole.EXTRA, SlotRole.EXTRA, SlotRole.PANEL),
            listOf(BannerKind.MOVE, BannerKind.BACK, BannerKind.GIMMICK, BannerKind.PREVIEW).map { intake.register(it, real)!!.role },
        )
    }

    @Test
    fun theTriplesShiftButtonIsAnExtraBesideBack() {
        assertEquals(SlotRole.EXTRA, slots(1, BattleScreen.MOVES).register(BannerKind.SHIFT, real)!!.role)
    }

    @Test
    fun aWidgetCountedShortTakesThePlaceOfTheLastOne() {

        val intake = slots(3, BattleScreen.GENERAL)
        assertNotNull(intake.register(BannerKind.BACK, real))
        repeat(2) { assertNotNull(intake.register(BannerKind.OPTION, real)) }
        assertNull(intake.register(BannerKind.OPTION, real))
    }

    @Test
    fun anAddonWidgetWithNoSizeOrBiggerThanItsPlaceIsNotHosted() {
        val intake = slots(3)
        assertNull(intake.register(BannerKind.ADDON, Rect(0.0, 0.0, 0.0, 20.0)))
        assertNull(intake.register(BannerKind.ADDON, Rect(0.0, 0.0, 200.0, 20.0)))
        assertNull(intake.register(BannerKind.ADDON, Rect(0.0, 0.0, 40.0, 40.0)))
        assertNotNull(intake.register(BannerKind.ADDON, Rect(0.0, 0.0, 90.0, 26.0)))
    }

    @Test
    fun aDroppedWidgetLeavesTheOthersWhereTheyWereAndItsPlaceEmpty() {
        val intake = slots(3)
        val first = intake.register(BannerKind.ADDON, real)!!
        val second = intake.register(BannerKind.ADDON, real)!!
        intake.drop(second)
        val third = intake.register(BannerKind.ADDON, real)!!
        assertEquals(listOf(first, third), intake.slots)
        assertEquals(SlotHandle(BannerKind.ADDON, 2), third.handle, "its number is not reused")
        assertTrue(third.art != second.art, "nor its place")
    }
}
