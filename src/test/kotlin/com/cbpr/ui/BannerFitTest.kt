package com.cbpr.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BannerFitTest {
    private fun fit(minX: Double, maxX: Double, minY: Double, maxY: Double, allowScale: Boolean = true) =
        BannerFit.compute(BannerFit.Bounds(minX, maxX, minY, maxY), allowScale)

    @Test
    fun aGroupInsideTheScreenIsLeftAlone() {
        assertTrue(fit(-0.5, 0.2, -0.4, 0.6).unchanged)
    }

    @Test
    fun aGroupOffTheBottomIsShiftedUpNotShrunk() {
        val f = fit(-0.4, 0.3, -1.6, -0.4)
        assertEquals(1.0, f.scale)
        assertEquals(0.0, f.shiftX)

        assertEquals(-BannerFit.LIMIT_BOTTOM - -1.6, f.shiftY, 1e-9)
    }

    @Test
    fun aGroupOffTheRightIsShiftedLeft() {
        val f = fit(0.3, 1.4, -0.2, 0.2)
        assertEquals(BannerFit.LIMIT_X - 1.4, f.shiftX, 1e-9)
        assertEquals(1.0, f.scale)
    }

    @Test
    fun aGroupTallerThanTheScreenAllowsIsShrunkToFit() {

        val f = fit(-0.3, 0.3, -1.5, 1.5)
        assertEquals((BannerFit.LIMIT_TOP + BannerFit.LIMIT_BOTTOM) / 3.0, f.scale, 1e-9)

        assertTrue(f.shiftY > 0.0)
    }

    @Test
    fun afterTheFitTheGroupIsInsideTheLimits() {
        val groups = listOf(
            listOf(-2.0, 2.0, -2.0, 2.0), listOf(0.5, 3.0, -3.0, -0.5), listOf(-3.0, -0.2, 0.1, 2.5), listOf(-0.1, 0.1, -0.1, 0.1),
        )
        for (g in groups) {
            val (minX, maxX, minY, maxY) = g
            val f = fit(minX, maxX, minY, maxY)
            val cx = (minX + maxX) / 2 + f.shiftX
            val cy = (minY + maxY) / 2 + f.shiftY
            val hw = (maxX - minX) * f.scale / 2
            val hh = (maxY - minY) * f.scale / 2
            assertTrue(cx - hw >= -BannerFit.LIMIT_X - 1e-9 && cx + hw <= BannerFit.LIMIT_X + 1e-9, "x $cx +- $hw")
            assertTrue(cy - hh >= -BannerFit.LIMIT_BOTTOM - 1e-9 && cy + hh <= BannerFit.LIMIT_TOP + 1e-9, "y $cy +- $hh")
        }
    }

    @Test
    fun withoutScalingABigGroupIsOnlyMoved() {
        val f = fit(-2.0, 2.0, -2.0, 2.0, allowScale = false)
        assertEquals(1.0, f.scale)
    }

    @Test
    fun boundsOfNothingIsNull() {
        assertNull(BannerFit.boundsOf(emptyList()))
    }

    @Test
    fun boundsOfPointsAreTheirExtremes() {
        val b = BannerFit.boundsOf(listOf(0.1 to -0.4, -0.3 to 0.2, 0.5 to 0.0))!!
        assertEquals(-0.3, b.minX)
        assertEquals(0.5, b.maxX)
        assertEquals(-0.4, b.minY)
        assertEquals(0.2, b.maxY)
    }
}
