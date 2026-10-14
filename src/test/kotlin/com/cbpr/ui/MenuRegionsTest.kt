package com.cbpr.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MenuRegionsTest {
    @Test
    fun theForfeitBoxIsInTheMiddleOfTheScreenWithCobblemonsOwnRounding() {

        val region = MenuRegions.forfeit(427.0, 240.0)
        val boxLeft = 427 / 2 - 56
        val boxTop = 240 / 2 - 22
        assertTrue(region.contains(boxLeft.toDouble(), boxTop.toDouble()))
        assertTrue(region.contains((boxLeft + 112).toDouble(), (boxTop + 44).toDouble()))
        assertEquals(113.0 + 12.0, region.width)
        assertEquals(45.0 + 12.0, region.height)
    }

    @Test
    fun anOddWidthRoundsDownLikeCobblemon() {
        val odd = MenuRegions.forfeit(427.0, 240.0)
        val even = MenuRegions.forfeit(426.0, 240.0)
        assertEquals(odd.x, even.x)
    }

    @Test
    fun theMenuPlaneIsNotBlownUpWhenTheCameraIsClose() {

        val near = MenuLayout.scaleFor(2.0) / 2.0
        val far = MenuLayout.scaleFor(6.0) / 6.0
        assertEquals(far, near, 1e-9)
    }
}
