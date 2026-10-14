package com.cbpr.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RingMotionTest {
    private val frame = 1.0 / 60

    private fun run(ring: RingMotion, seconds: Double, engaged: Boolean, reduced: Boolean = false) {
        repeat((seconds / frame).toInt()) { ring.step(frame, engaged, reduced) }
    }

    @Test
    fun theRingRestsDimAndAtFullSize() {
        val ring = RingMotion()
        assertEquals(RingMotion.BASE_ALPHA, ring.alpha, 1e-9)
        assertEquals(1.0, ring.radiusScale, 1e-9)
    }

    @Test
    fun anOpenSubMenuBrightensAndTightensTheRingThenItReturns() {
        val ring = RingMotion()
        run(ring, 1.5, engaged = true)
        assertEquals(RingMotion.ENGAGED_ALPHA, ring.alpha, 0.02)
        assertTrue(ring.radiusScale < 1.0 - RingMotion.SHRINK / 2 + RingMotion.BREATH + 0.001, "scale ${ring.radiusScale}")
        run(ring, 2.0, engaged = false)
        assertEquals(RingMotion.BASE_ALPHA, ring.alpha, 0.02)
    }

    @Test
    fun engagingBouncesPastTheFinalValueOnce() {
        val ring = RingMotion()
        var peak = 0.0
        repeat(120) {
            ring.step(frame, true, false)
            peak = maxOf(peak, ring.engaged)
        }
        assertTrue(peak > 1.0 && peak < 1.4, "peak $peak")
    }

    @Test
    fun alphaNeverLeavesZeroToOne() {
        val ring = RingMotion()
        repeat(240) {
            ring.step(frame, it % 40 < 20, false)
            assertTrue(ring.alpha in 0.0..1.0)
        }
    }

    @Test
    fun theArcsTurnFasterWhileEngaged() {
        val calm = RingMotion()
        val busy = RingMotion()
        run(calm, 1.0, engaged = false)
        run(busy, 1.0, engaged = true)
        assertTrue(busy.spin > calm.spin, "calm ${calm.spin} busy ${busy.spin}")
        assertTrue(calm.spin > 0.0 && busy.spin < 360.0)
    }

    @Test
    fun reducedMotionKeepsTheRingStillButStillBrightens() {
        val ring = RingMotion()
        run(ring, 1.5, engaged = true, reduced = true)
        assertEquals(0.0, ring.spin)
        assertEquals(RingMotion.ENGAGED_ALPHA, ring.alpha, 0.02)
    }

    @Test
    fun resetPutsTheRingBackAtRest() {
        val ring = RingMotion()
        run(ring, 1.0, engaged = true)
        ring.reset()
        assertEquals(0.0, ring.spin)
        assertEquals(RingMotion.BASE_ALPHA, ring.alpha, 1e-9)
    }
}
