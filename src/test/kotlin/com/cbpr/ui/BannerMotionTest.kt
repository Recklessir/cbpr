package com.cbpr.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BannerMotionTest {
    private val frame = 1.0 / 60

    private fun run(motion: BannerMotion<Int>, seconds: Double, hovered: Int = -1): Double {
        var peak = 0.0
        repeat((seconds / frame).toInt()) {
            motion.step(frame, hovered)
            peak = maxOf(peak, motion.popScale(0))
        }
        return peak
    }

    @Test
    fun startsHidden() {
        val motion = BannerMotion<Int>()
        assertTrue((0 until 4).all { motion.hidden(it) })
        assertEquals(0.0, motion.scale(0))
    }

    @Test
    fun popOvershootsAboutEightPercentThenSettles() {
        val motion = BannerMotion<Int>()
        motion.setVisible(0, true)
        val peak = run(motion, 2.0)
        assertTrue(peak in 1.04..1.14, "peak $peak")
        assertEquals(1.0, motion.scale(0), 0.01)
    }

    @Test
    fun laterBannersPopInAfterEarlierOnes() {
        val motion = BannerMotion<Int>()
        (0 until 4).forEach { motion.setVisible(it, true, it) }
        run(motion, 0.15)
        assertTrue(motion.popScale(0) > motion.popScale(3), "first ${motion.popScale(0)} last ${motion.popScale(3)}")
        assertEquals(0.0, motion.popScale(3), 1e-9)
        run(motion, 2.0)
        assertEquals(1.0, motion.popScale(3), 0.01)
    }

    @Test
    fun slotsGivenTheSameOrderPopInTogetherAndLeaveTogether() {
        val motion = BannerMotion<Int>()
        motion.setVisible(6, true, order = 4)
        motion.setVisible(16, true, order = 4)
        repeat(30) {
            motion.step(frame, -1)
            assertEquals(motion.popScale(6), motion.popScale(16), 1e-12, "same entrance at step $it")
        }
        motion.setVisible(6, false)
        motion.setVisible(16, false)
        repeat(30) {
            motion.step(frame, -1)
            assertEquals(motion.popScale(6), motion.popScale(16), 1e-12, "same exit at step $it")
        }
    }

    @Test
    fun exitIsFasterThanEntranceAndNeverNegative() {
        val entering = BannerMotion<Int>()
        entering.setVisible(0, true)
        var elapsed = 0.0
        var enterTime = 0.0
        repeat(180) {
            entering.step(frame, -1)
            elapsed += frame
            if (abs(entering.popScale(0) - 1.0) >= 0.02) enterTime = elapsed
        }
        val leaving = BannerMotion<Int>()
        leaving.setVisible(0, true)
        run(leaving, 2.0)
        leaving.setVisible(0, false)
        var exitTime = 0.0
        while (!leaving.hidden(0) && exitTime < 5.0) {
            leaving.step(frame, -1)
            assertTrue(leaving.popScale(0) >= 0.0)
            exitTime += frame
        }
        assertTrue(exitTime < enterTime, "exit $exitTime enter $enterTime")
    }

    @Test
    fun hoverGrowsTheBannerWithABounce() {
        val motion = BannerMotion<Int>()
        motion.setVisible(0, true)
        run(motion, 2.0)
        var peak = 0.0
        repeat(60) {
            motion.step(frame, 0)
            peak = maxOf(peak, motion.scale(0))
        }
        assertTrue(peak > 1.07, "should overshoot the hover size, peak $peak")
        repeat(240) { motion.step(frame, 0) }
        assertEquals(1.07, motion.scale(0), 0.01)
    }

    @Test
    fun leavingTheHoverReturnsToNormalSize() {
        val motion = BannerMotion<Int>()
        motion.setVisible(0, true)
        run(motion, 1.0, hovered = 0)
        run(motion, 2.0, hovered = -1)
        assertEquals(1.0, motion.scale(0), 0.01)
    }

    @Test
    fun onlyTheHoveredBannerGrows() {
        val motion = BannerMotion<Int>()
        motion.setVisible(0, true)
        motion.setVisible(1, true)
        run(motion, 2.0, hovered = 1)
        assertEquals(1.0, motion.scale(0), 0.01)
        assertTrue(motion.scale(1) > 1.05)
    }

    @Test
    fun aHiddenBannerNeverShowsAHoverScale() {
        val motion = BannerMotion<Int>()
        run(motion, 1.0, hovered = 0)
        assertEquals(0.0, motion.scale(0), 1e-9)
    }

    @Test
    fun reenteringAfterLeavingRevives() {
        val motion = BannerMotion<Int>()
        motion.setVisible(0, true)
        run(motion, 1.0)
        motion.setVisible(0, false)
        motion.setVisible(0, true)
        assertFalse(motion.hidden(0))
        run(motion, 2.0)
        assertEquals(1.0, motion.popScale(0), 0.01)
    }

    @Test
    fun resetHidesEverythingAtOnce() {
        val motion = BannerMotion<Int>()
        (0 until 3).forEach { motion.setVisible(it, true, it) }
        run(motion, 2.0)
        motion.reset()
        assertTrue((0 until 3).all { motion.hidden(it) })
        assertEquals(0.0, motion.scale(1))
    }
}
