package com.cbpr.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CinematicMotionTest {
    private val frame = 1.0 / 60

    private fun run(letterbox: LetterboxMotion, seconds: Double, wanted: Boolean): Double {
        var peak = 0.0
        repeat((seconds / frame).toInt()) {
            letterbox.step(frame, wanted)
            peak = maxOf(peak, letterbox.fraction)
        }
        return peak
    }

    @Test
    fun barsStartHidden() {
        val bars = LetterboxMotion()
        assertTrue(bars.hidden)
        assertEquals(0.0, bars.fraction)
    }

    @Test
    fun barsSlideInSettleAtFullAndNeverExceedTheirMaximum() {
        val bars = LetterboxMotion()
        val peak = run(bars, 2.0, wanted = true)
        assertTrue(peak > 1.0, "should overshoot a little, peak $peak")
        assertTrue(peak <= 1.05)
        assertEquals(1.0, bars.fraction, 0.01)
        assertFalse(bars.hidden)
    }

    @Test
    fun barsLeaveFasterThanTheyArriveAndNeverGoNegative() {
        val arriving = LetterboxMotion()
        var arriveTime = 0.0
        var t = 0.0
        repeat(180) {
            arriving.step(frame, true)
            t += frame
            if (kotlin.math.abs(arriving.fraction - 1.0) >= 0.02) arriveTime = t
        }
        val leaving = LetterboxMotion()
        run(leaving, 2.0, wanted = true)
        var leaveTime = 0.0
        while (!leaving.hidden && leaveTime < 5.0) {
            leaving.step(frame, false)
            assertTrue(leaving.fraction >= 0.0)
            leaveTime += frame
        }
        assertTrue(leaveTime < arriveTime, "leave $leaveTime arrive $arriveTime")
    }

    @Test
    fun resetHidesTheBarsAtOnce() {
        val bars = LetterboxMotion()
        run(bars, 2.0, wanted = true)
        bars.reset()
        assertEquals(0.0, bars.fraction)
    }

    @Test
    fun aCalloutIsInactiveUntilShown() {
        val callout = CalloutMotion()
        assertFalse(callout.active)
        assertEquals(0.0, callout.scale)
    }

    @Test
    fun aCalloutPopsWithOvershootThenSettlesHoldsAndFadesOut() {
        val callout = CalloutMotion()
        callout.show()
        var peak = 0.0
        repeat(40) {
            callout.step(frame)
            peak = maxOf(peak, callout.scale)
        }
        assertTrue(peak in 1.04..1.14, "peak $peak")
        assertEquals(1.0, callout.alpha)

        repeat(20) { callout.step(frame) }
        assertEquals(1.0, callout.scale, 0.02)
        assertEquals(1.0, callout.alpha)
        assertTrue(callout.active)

        repeat(60) { callout.step(frame) }
        assertFalse(callout.active)
        assertEquals(0.0, callout.alpha)
    }

    @Test
    fun theFadeIsGradualNotAnInstantCut() {
        val callout = CalloutMotion()
        callout.show()
        repeat(70) { callout.step(frame) }
        repeat(8) { callout.step(frame) }
        assertTrue(callout.alpha in 0.0..1.0)
        assertTrue(callout.alpha < 1.0 && callout.alpha > 0.0, "alpha ${callout.alpha}")
    }

    @Test
    fun aNewCalloutReplacesTheOldOneAndPopsAgainFromNothing() {
        val callout = CalloutMotion()
        callout.show()
        repeat(60) { callout.step(frame) }
        callout.show()
        assertEquals(0.0, callout.scale)
        assertEquals(1.0, callout.alpha)
        assertTrue(callout.active)
    }

    @Test
    fun resetEndsAnyCalloutAtOnce() {
        val callout = CalloutMotion()
        callout.show()
        callout.step(frame)
        callout.reset()
        assertFalse(callout.active)
        assertEquals(0.0, callout.scale)
    }

    @Test
    fun aCalloutCanBeAskedToHoldLongerForTheResultText() {
        val result = CalloutMotion()
        result.show(3.2)
        repeat(60 * 3) { result.step(frame) }
        assertTrue(result.active)
        assertEquals(1.0, result.alpha)
        repeat(60) { result.step(frame) }
        assertFalse(result.active)
    }

    @Test
    fun aNewCalloutGoesBackToTheDefaultHoldUnlessToldOtherwise() {
        val callout = CalloutMotion()
        callout.show(3.2)
        callout.show()
        repeat(60 * 2) { callout.step(frame) }
        assertFalse(callout.active)
    }

    @Test
    fun theTintStartsClearAndEasesInWithoutOvershoot() {
        val tint = TintMotion()
        assertFalse(tint.visible)
        var peak = 0.0
        repeat(180) {
            tint.step(frame, 1.0)
            peak = maxOf(peak, tint.amount)
        }
        assertTrue(peak <= 1.0)
        assertEquals(1.0, tint.amount, 0.02)
        assertTrue(tint.visible)
    }

    @Test
    fun theTintEasesBackOutAndBecomesInvisible() {
        val tint = TintMotion()
        repeat(180) { tint.step(frame, 1.0) }
        repeat(300) { tint.step(frame, 0.0) }
        assertFalse(tint.visible)
        assertEquals(0.0, tint.amount, 0.01)
    }

    @Test
    fun theTintTargetIsClampedAndResetClearsIt() {
        val tint = TintMotion()
        repeat(300) { tint.step(frame, 5.0) }
        assertTrue(tint.amount <= 1.0)
        tint.reset()
        assertEquals(0.0, tint.amount)
        assertFalse(tint.visible)
    }
}
