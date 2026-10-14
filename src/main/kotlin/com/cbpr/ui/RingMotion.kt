package com.cbpr.ui

import com.cbpr.camera.Spring
import kotlin.math.sin

class RingMotion {
    private val engagement = Spring(0.0, STIFFNESS, Spring.dampingFor(STIFFNESS, DAMPING_RATIO))
    private val alarm = Spring(0.0, ALARM_STIFFNESS, Spring.dampingFor(ALARM_STIFFNESS, 1.0))

    private var breath = 0.0

    val danger: Double get() = alarm.value.coerceIn(0.0, 1.0)

    var spin = 0.0
        private set

    val engaged: Double get() = engagement.value

    val radiusScale: Double get() = 1.0 - SHRINK * engagement.value + BREATH * (1.0 + ALARM_BREATH * danger) * sin(breath)
    val alpha: Double get() = (BASE_ALPHA + (ENGAGED_ALPHA - BASE_ALPHA) * engagement.value + ALARM_ALPHA * danger).coerceIn(0.0, 1.0)

    fun step(dt: Double, isEngaged: Boolean, reducedMotion: Boolean, low: Boolean = false) {
        engagement.step(if (isEngaged) 1.0 else 0.0, dt)
        alarm.step(if (low) 1.0 else 0.0, dt)
        if (reducedMotion) return
        breath += dt * BREATH_SPEED * (1.0 + ALARM_SPEED * danger)
        spin = (spin + dt * (BASE_SPIN + (ENGAGED_SPIN - BASE_SPIN) * engagement.value.coerceIn(0.0, 1.0)) * (1.0 + ALARM_SPIN * danger)) % 360.0
    }

    fun reset() {
        engagement.snap(0.0)
        alarm.snap(0.0)
        breath = 0.0
        spin = 0.0
    }

    companion object {
        const val BASE_SPIN = 25.0
        const val ENGAGED_SPIN = 70.0
        const val BASE_ALPHA = 0.45
        const val ENGAGED_ALPHA = 0.9
        const val SHRINK = 0.12
        const val BREATH = 0.03
        const val BREATH_SPEED = 2.2

        const val ALARM_BREATH = 2.0
        const val ALARM_SPEED = 1.5
        const val ALARM_SPIN = 0.8
        const val ALARM_ALPHA = 0.25
        private const val ALARM_STIFFNESS = 40.0
        private const val STIFFNESS = 90.0
        private const val DAMPING_RATIO = 0.55
    }
}
