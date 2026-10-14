package com.cbpr.ui

import com.cbpr.battle.HpShare
import com.cbpr.camera.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DamagePopupsTest {
    private val anchor = Point(0.0, 2.0, 0.0)

    @Test
    fun damageIsNegativeAndHealingPositiveInWholePercent() {
        assertEquals(-25, DamageText.percent(0.25))
        assertEquals(5, DamageText.percent(-0.05))
        assertEquals("-25%", DamageText.label(-25))
        assertEquals("+5%", DamageText.label(5))
    }

    @Test
    fun aChangeTooSmallToRoundStillShowsOnePercent() {
        assertEquals(-1, DamageText.percent(0.004))
        assertEquals(1, DamageText.percent(-0.004))
        assertEquals(-1, DamageText.percent(0.01))
    }

    @Test
    fun healingIsGreenAndBiggerHitsAreRedder() {
        assertEquals(DamageText.HEAL_RGB, DamageText.rgb(5))
        val small = DamageText.rgb(-1)
        val big = DamageText.rgb(-50)
        assertEquals(DamageText.LIGHT_RGB, DamageText.rgb(0))
        assertEquals(DamageText.SEVERE_RGB, big)
        assertEquals(big, DamageText.rgb(-90), "past the top of the scale it stays at the top colour")
        assertTrue(((DamageText.rgb(-25) shr 8) and 0xFF) < ((small shr 8) and 0xFF))
    }

    @Test
    fun scaleShrinksWithDistanceWithinLimits() {
        assertEquals(DamageText.MAX_SCALE, DamageText.scaleAt(0.5))
        assertEquals(DamageText.MIN_SCALE, DamageText.scaleAt(80.0))
        assertTrue(DamageText.scaleAt(8.0) < DamageText.scaleAt(5.0))
    }

    @Test
    fun aPopupPopsRisesAndFades() {
        val popups = DamagePopups()
        popups.add(0.3, anchor, reduced = false)
        val popup = popups.active.single()
        assertEquals("-30%", popup.label)
        assertEquals(0.0, popup.rise)
        assertEquals(1.0, popup.alpha)
        repeat(30) { popups.step(0.01) }
        assertTrue(popup.rise > 0.0 && popup.scale > 0.5, "rise ${popup.rise} scale ${popup.scale}")
        repeat(100) { popups.step(0.01) }
        assertTrue(popups.active.isEmpty(), "gone after its life")
    }

    @Test
    fun alphaFallsToZeroAtTheEndOfItsLife() {
        val popup = DamagePopup.damage(-10, anchor, 0, reduced = false)
        popup.step(DamagePopup.FADE_START)
        assertEquals(1.0, popup.alpha)
        popup.step((DamagePopup.LIFE_SECONDS - DamagePopup.FADE_START) / 2)
        assertTrue(popup.alpha in 0.4..0.6, "alpha ${popup.alpha}")
        popup.step(1.0)
        assertEquals(0.0, popup.alpha)
        assertTrue(popup.done)
    }

    @Test
    fun reducedMotionKeepsTheNumberStillAtFullSize() {
        val popup = DamagePopup.damage(-10, anchor, 0, reduced = true)
        popup.step(0.2)
        assertEquals(0.0, popup.rise)
        assertEquals(1.0, popup.scale)
    }

    @Test
    fun theListIsBoundedAndDropsTheOldestFirst() {
        val popups = DamagePopups()
        repeat(DamagePopups.MAX_POPUPS + 3) { popups.add(0.01 * (it + 1), anchor, reduced = false) }
        assertEquals(DamagePopups.MAX_POPUPS, popups.active.size)
        assertEquals("-4%", popups.active.first().label)
    }

    @Test
    fun quickHitsGoToDifferentSlots() {
        val popups = DamagePopups()
        repeat(3) { popups.add(0.1, anchor, reduced = false) }
        assertEquals(setOf(0, 1, 2), popups.active.map { it.slot }.toSet())
    }

    @Test
    fun noChangeShowsNothing() {
        val popups = DamagePopups()
        popups.add(0.0, anchor, reduced = false)
        assertTrue(popups.active.isEmpty())
    }

    @Test
    fun aChangedMaximumIsNotDamageOrHealing() {

        assertEquals(HpShare.of(true, 100f, 100f), HpShare.of(true, 200f, 200f))
        assertEquals(0.5, HpShare.of(true, 50f, 100f))
        assertEquals(0.4, HpShare.of(false, 0.4f, 0f)!!, 1e-6)
        assertNull(HpShare.of(true, 10f, 0f))
    }
}
