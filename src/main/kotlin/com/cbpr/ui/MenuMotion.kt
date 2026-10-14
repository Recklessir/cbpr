package com.cbpr.ui

import com.cbpr.camera.Point
import com.cbpr.camera.Spring

class MenuMotion {
    private val x = Spring(0.0, FOLLOW_STIFFNESS, Spring.dampingFor(FOLLOW_STIFFNESS, FOLLOW_DAMPING_RATIO))
    private val y = Spring(0.0, FOLLOW_STIFFNESS, Spring.dampingFor(FOLLOW_STIFFNESS, FOLLOW_DAMPING_RATIO))
    private val z = Spring(0.0, FOLLOW_STIFFNESS, Spring.dampingFor(FOLLOW_STIFFNESS, FOLLOW_DAMPING_RATIO))
    private var placed = false

    val position: Point get() = Point(x.value, y.value, z.value)

    fun step(dt: Double, anchor: Point) {
        if (!placed) {
            x.snap(anchor.x)
            y.snap(anchor.y)
            z.snap(anchor.z)
            placed = true
        }
        x.step(anchor.x, dt)
        y.step(anchor.y, dt)
        z.step(anchor.z, dt)
    }

    fun reset() {
        placed = false
    }

    fun tiltDegrees(rightX: Double, rightY: Double, rightZ: Double): Double =
        ((x.velocity * rightX + y.velocity * rightY + z.velocity * rightZ) * TILT_PER_SPEED).coerceIn(-MAX_TILT, MAX_TILT)

    private companion object {
        const val FOLLOW_STIFFNESS = 90.0
        const val FOLLOW_DAMPING_RATIO = 0.8
        const val TILT_PER_SPEED = 6.0
        const val MAX_TILT = 8.0
    }
}
