package com.cbpr.camera

import kotlin.math.abs
import kotlin.math.min

class Spring(var value: Double, var stiffness: Double, var damping: Double) {
    var velocity = 0.0
        private set

    fun step(target: Double, dt: Double) {
        var left = dt
        while (left > 1e-9) {
            val h = min(left, MAX_SUBSTEP)
            velocity += (stiffness * (target - value) - damping * velocity) * h
            value += velocity * h
            left -= h
        }
    }

    fun snap(to: Double) {
        value = to
        velocity = 0.0
    }

    fun isSettled(target: Double, positionEpsilon: Double, velocityEpsilon: Double): Boolean =
        abs(target - value) < positionEpsilon && abs(velocity) < velocityEpsilon

    companion object {

        private const val MAX_SUBSTEP = 1.0 / 120.0

        fun dampingFor(stiffness: Double, ratio: Double): Double = 2.0 * ratio * Math.sqrt(stiffness)
    }
}
