package com.cbpr.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpringTest {
    private fun underdamped() = Spring(0.0, 30.0, Spring.dampingFor(30.0, 0.5))

    @Test
    fun convergesToTarget() {
        val spring = underdamped()
        repeat(600) { spring.step(10.0, 1.0 / 60) }
        assertEquals(10.0, spring.value, 1e-3)
        assertEquals(0.0, spring.velocity, 1e-3)
    }

    @Test
    fun overshootsWhenUnderdamped() {
        val spring = underdamped()
        var peak = 0.0
        repeat(120) {
            spring.step(10.0, 1.0 / 60)
            peak = maxOf(peak, spring.value)
        }
        assertTrue(peak > 10.0, "peak was $peak")
    }

    @Test
    fun staysFiniteOnHugeFrameGap() {
        val spring = underdamped()
        spring.step(10.0, 5.0)
        assertTrue(spring.value.isFinite())
        assertEquals(10.0, spring.value, 1e-2)
    }

    @Test
    fun snapClearsVelocity() {
        val spring = underdamped()
        spring.step(10.0, 0.2)
        spring.snap(3.0)
        assertEquals(3.0, spring.value)
        assertEquals(0.0, spring.velocity)
    }
}
