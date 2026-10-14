package com.cbpr.ui

import com.cbpr.camera.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MenuMotionTest {
    private val frame = 1.0 / 60
    private val anchor = Point(1.0, 2.0, 3.0)

    private fun run(motion: MenuMotion, seconds: Double, at: Point = anchor) =
        repeat((seconds / frame).toInt()) { motion.step(frame, at) }

    @Test
    fun firstAnchorIsPlacedWithoutSliding() {
        val motion = MenuMotion()
        motion.step(frame, anchor)
        assertEquals(anchor.x, motion.position.x, 1e-9)
        assertEquals(anchor.y, motion.position.y, 1e-9)
    }

    @Test
    fun followingAnchorLagsThenArrives() {
        val motion = MenuMotion()
        motion.step(frame, anchor)
        val moved = Point(anchor.x + 4.0, anchor.y, anchor.z)
        motion.step(frame, moved)
        assertTrue(motion.position.x < moved.x - 3.0, "should lag right after the jump")
        run(motion, 5.0, moved)
        assertEquals(moved.x, motion.position.x, 0.01)
    }

    @Test
    fun resetPlacesTheNextAnchorWithoutSliding() {
        val motion = MenuMotion()
        motion.step(frame, anchor)
        motion.reset()
        val far = Point(50.0, 0.0, 0.0)
        motion.step(frame, far)
        assertEquals(far.x, motion.position.x, 1e-9)
    }

    @Test
    fun tiltIsClampedAndFollowsSidewaysSpeed() {
        val motion = MenuMotion()
        motion.step(frame, anchor)
        motion.step(frame, Point(anchor.x + 50.0, anchor.y, anchor.z))
        assertEquals(8.0, motion.tiltDegrees(1.0, 0.0, 0.0), 1e-9)
        assertEquals(-8.0, motion.tiltDegrees(-1.0, 0.0, 0.0), 1e-9)
        assertEquals(0.0, motion.tiltDegrees(0.0, 0.0, 0.0), 1e-9)
    }
}
