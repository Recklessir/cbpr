package com.cbpr.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartyPreviewTest {
    private fun move(power: Double = 90.0, accuracy: Double = 100.0, pp: Int = 15, maxPp: Int = 15, color: Int = 0xFF8844) =
        MoveView("Flamethrower", color, "Special", power, accuracy, pp, maxPp)

    private fun row(move: MoveView) = PartyPreview.row(move, "Pow", "Acc.")

    @Test
    fun aMoveIsItsNameItsPpAndItsDetails() {
        val row = row(move())
        assertEquals("Flamethrower", row.name)
        assertEquals("15/15", row.pp)
        assertEquals("Special · Pow 90 · Acc. 100%", row.details[0])
    }

    @Test
    fun theDetailsComeInThreeWordingsFromFullToShort() {
        val details = row(move()).details
        assertEquals(3, details.size)
        assertTrue(details[0].length > details[1].length && details[1].length > details[2].length)
        assertEquals("Special · 90 · 100%", details[1])
        assertEquals("Special", details[2])
    }

    @Test
    fun theFullestWordingThatFitsIsUsedAndTheShortestIfNoneDoes() {
        val details = row(move()).details
        assertEquals(details[0], PartyPreview.fit(details, { it.length * 6 }, 400))
        assertEquals(details[1], PartyPreview.fit(details, { it.length * 6 }, details[1].length * 6))
        assertEquals(details[2], PartyPreview.fit(details, { it.length * 6 }, 10))
    }

    @Test
    fun ppIsFullLowOrEmpty() {
        assertEquals(PpState.FULL, PartyPreview.ppState(15, 15))
        assertEquals(PpState.FULL, PartyPreview.ppState(5, 15))
        assertEquals(PpState.LOW, PartyPreview.ppState(3, 15))
        assertEquals(PpState.LOW, PartyPreview.ppState(1, 40))
        assertEquals(PpState.EMPTY, PartyPreview.ppState(0, 15))
        assertEquals(PpState.EMPTY, PartyPreview.ppState(-1, 15))
        assertEquals(PpState.FULL, PartyPreview.ppState(1, 0), "an unknown maximum is not shown as low")
    }

    @Test
    fun aMoveThatDoesNoDamageOrCannotMissShowsDashes() {
        assertEquals("-", PartyPreview.powerText(0.0))
        assertEquals("-", PartyPreview.accuracyText(0.0))
        assertEquals("-", PartyPreview.accuracyText(101.0), "an accuracy above 100 is a move that never misses")
        assertEquals("90", PartyPreview.powerText(90.4))
        assertEquals("95%", PartyPreview.accuracyText(95.0))
        val status = row(move(power = 0.0, accuracy = 0.0))
        assertEquals("Special · Pow - · Acc. -", status.details[0])
    }

    @Test
    fun theRowCarriesTheMovesPpState() {
        assertEquals(PpState.EMPTY, row(move(pp = 0)).ppState)
        assertEquals(PpState.LOW, row(move(pp = 2)).ppState)
        assertEquals("2/15", row(move(pp = 2)).pp)
    }

    @Test
    fun aBrightTypeColourIsKeptAndMadeOpaque() {
        assertEquals(0xFFFF8844.toInt(), PartyPreview.legible(0xFF8844))
        assertEquals(0xFFFFFFFF.toInt(), PartyPreview.legible(0xFFFFFF))
    }

    @Test
    fun aDarkTypeColourIsLiftedUntilItReadsOnTheDarkPanel() {
        for (dark in listOf(0x000000, 0x1A1A2E, 0x443355, 0x705848)) {
            val lifted = PartyPreview.legible(dark)
            val r = (lifted shr 16) and 0xFF
            val g = (lifted shr 8) and 0xFF
            val b = lifted and 0xFF
            val brightness = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            assertTrue(brightness >= PartyPreview.MIN_BRIGHTNESS - 0.01, "$dark gives brightness $brightness")
        }
    }

    @Test
    fun liftingKeepsTheHueSoDifferentTypesStayDifferent() {
        val fire = PartyPreview.legible(0xC03018)
        val water = PartyPreview.legible(0x1858C0)
        assertTrue(((fire shr 16) and 0xFF) > (fire and 0xFF), "fire is still red")
        assertTrue((water and 0xFF) > ((water shr 16) and 0xFF), "water is still blue")
    }

    @Test
    fun theRowCarriesTheLegibleTypeColour() {
        assertEquals(PartyPreview.legible(0x101010), row(move(color = 0x101010)).color)
    }
}
