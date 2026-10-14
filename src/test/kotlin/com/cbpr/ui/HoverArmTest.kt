package com.cbpr.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HoverArmTest {
    @Test
    fun aCursorThatHasNotMovedSinceTheMenuOpenedIsNotAHover() {
        val arm = HoverArm()
        arm.begin(500.0, 400.0)
        assertFalse(arm.update(500.0, 400.0))
        assertFalse(arm.update(503.0, 402.0), "jitter of a few pixels")
    }

    @Test
    fun movingTheCursorArmsItAndItStaysArmed() {
        val arm = HoverArm()
        arm.begin(500.0, 400.0)
        assertTrue(arm.update(520.0, 400.0))
        assertTrue(arm.update(500.0, 400.0), "back where it started, still armed")
    }

    @Test
    fun aNewMenuOpeningStartsAgain() {
        val arm = HoverArm()
        arm.begin(0.0, 0.0)
        arm.update(50.0, 0.0)
        arm.begin(300.0, 300.0)
        assertFalse(arm.update(300.0, 300.0))
    }

    @Test
    fun leavingTheMoveMenuDisarms() {
        val arm = HoverArm()
        arm.begin(0.0, 0.0)
        arm.update(50.0, 0.0)
        arm.disarm()
        assertFalse(arm.armed)
    }
}
