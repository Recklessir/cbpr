package com.cbpr.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenQuadTest {
    private val square = ScreenQuad(doubleArrayOf(1.0, -1.0, -1.0, 1.0), doubleArrayOf(1.0, 1.0, -1.0, -1.0))
    private val reversed = ScreenQuad(doubleArrayOf(1.0, 1.0, -1.0, -1.0), doubleArrayOf(1.0, -1.0, -1.0, 1.0))

    @Test
    fun insideAndOutsideForEitherWinding() {
        for (quad in listOf(square, reversed)) {
            assertTrue(quad.contains(0.0, 0.0))
            assertTrue(quad.contains(0.9, -0.9))
            assertFalse(quad.contains(1.2, 0.0))
            assertFalse(quad.contains(0.0, -1.2))
        }
    }

    @Test
    fun growingGivesRoomAtTheEdgeAboutTheMiddle() {
        assertFalse(square.contains(1.05, 0.0))
        assertTrue(square.contains(1.05, 0.0, grow = 1.1))
        assertFalse(square.contains(1.2, 0.0, grow = 1.1))
    }

    @Test
    fun aSlantedShapeIsHandledToo() {
        val slanted = ScreenQuad(doubleArrayOf(1.5, -0.5, -1.5, 0.5), doubleArrayOf(1.0, 1.0, -1.0, -1.0))
        assertTrue(slanted.contains(0.0, 0.0))
        assertTrue(slanted.contains(1.0, 0.8))
        assertFalse(slanted.contains(1.4, -0.9))
    }
}
