package com.cbpr.ui

import com.cbpr.camera.Spring

class LetterboxMotion {
    private val spring = Spring(0.0, ENTER_STIFFNESS, Spring.dampingFor(ENTER_STIFFNESS, ENTER_DAMPING_RATIO))

    val fraction: Double get() = spring.value.coerceIn(0.0, MAX_FRACTION)
    val hidden: Boolean get() = spring.value < HIDDEN_BELOW && spring.velocity < HIDDEN_BELOW

    fun step(dt: Double, wanted: Boolean, reduced: Boolean = false) {
        if (wanted) {
            val stiffness = if (reduced) REDUCED_ENTER_STIFFNESS else ENTER_STIFFNESS
            spring.stiffness = stiffness
            spring.damping = Spring.dampingFor(stiffness, if (reduced) 1.0 else ENTER_DAMPING_RATIO)
        } else {
            spring.stiffness = EXIT_STIFFNESS
            spring.damping = Spring.dampingFor(EXIT_STIFFNESS, 1.0)
        }
        spring.step(if (wanted) 1.0 else 0.0, dt)
    }

    fun reset() = spring.snap(0.0)

    private companion object {
        const val ENTER_STIFFNESS = 90.0
        const val REDUCED_ENTER_STIFFNESS = 250.0
        const val ENTER_DAMPING_RATIO = 0.8
        const val EXIT_STIFFNESS = 500.0
        const val MAX_FRACTION = 1.05
        const val HIDDEN_BELOW = 0.005
    }
}

class CalloutMotion {
    private val pop = Spring(0.0, POP_STIFFNESS, Spring.dampingFor(POP_STIFFNESS, POP_DAMPING_RATIO))
    private var age = Double.POSITIVE_INFINITY
    private var hold = HOLD_SECONDS

    val scale: Double get() = pop.value.coerceAtLeast(0.0)
    val alpha: Double get() = if (age < hold) 1.0 else (1.0 - (age - hold) / FADE_SECONDS).coerceIn(0.0, 1.0)
    val active: Boolean get() = age < hold + FADE_SECONDS

    var reduced = false

    fun show(holdSeconds: Double = HOLD_SECONDS) {
        age = 0.0
        hold = holdSeconds
        pop.snap(0.0)
    }

    fun step(dt: Double) {
        if (!active) return
        age += dt
        pop.stiffness = if (reduced) REDUCED_POP_STIFFNESS else POP_STIFFNESS
        pop.damping = Spring.dampingFor(pop.stiffness, if (reduced) 1.0 else POP_DAMPING_RATIO)
        pop.step(1.0, dt)
    }

    fun reset() {
        age = Double.POSITIVE_INFINITY
        pop.snap(0.0)
    }

    private companion object {
        const val POP_STIFFNESS = 260.0
        const val REDUCED_POP_STIFFNESS = 600.0

        const val POP_DAMPING_RATIO = 0.63
        const val HOLD_SECONDS = 1.1
        const val FADE_SECONDS = 0.35
    }
}

class TintMotion {
    private val spring = Spring(0.0, STIFFNESS, Spring.dampingFor(STIFFNESS, 1.0))

    val amount: Double get() = spring.value.coerceIn(0.0, 1.0)
    val visible: Boolean get() = spring.value > VISIBLE_ABOVE

    fun step(dt: Double, target: Double) = spring.step(target.coerceIn(0.0, 1.0), dt)

    fun reset() = spring.snap(0.0)

    private companion object {
        const val STIFFNESS = 30.0
        const val VISIBLE_ABOVE = 0.003
    }
}

class WashMotion {
    private val strength = Spring(0.0, STRENGTH_STIFFNESS, Spring.dampingFor(STRENGTH_STIFFNESS, 1.0))
    private val blend = Spring(1.0, BLEND_STIFFNESS, Spring.dampingFor(BLEND_STIFFNESS, 1.0))
    private var from = 0
    private var to = 0

    val amount: Double get() = strength.value.coerceIn(0.0, 1.0)
    val rgb: Int get() = Rgb.mix(from, to, blend.value)

    fun step(dt: Double, wanted: Int?) {
        if (wanted != null && wanted != to) {

            from = if (amount < HIDDEN_BELOW) wanted else rgb
            to = wanted
            blend.snap(if (amount < HIDDEN_BELOW) 1.0 else 0.0)
        }
        strength.step(if (wanted != null) 1.0 else 0.0, dt)
        blend.step(1.0, dt)
    }

    fun reset() {
        strength.snap(0.0)
        blend.snap(1.0)
        from = 0
        to = 0
    }

    private companion object {
        const val STRENGTH_STIFFNESS = 14.0
        const val BLEND_STIFFNESS = 20.0
        const val HIDDEN_BELOW = 0.02
    }
}

object Vignette {
    const val INNER_RADIUS = 0.7

    const val OUTER_RADIUS = 1.5
    const val RINGS = 8
    const val SEGMENTS = 64

    val COS = FloatArray(SEGMENTS + 1) { Math.cos(2.0 * Math.PI * it / SEGMENTS).toFloat() }
    val SIN = FloatArray(SEGMENTS + 1) { Math.sin(2.0 * Math.PI * it / SEGMENTS).toFloat() }

    fun radius(ring: Int): Double = INNER_RADIUS + (OUTER_RADIUS - INNER_RADIUS) * ring / RINGS

    fun strength(t: Double): Double {
        val x = t.coerceIn(0.0, 1.0)
        return x * x * (3.0 - 2.0 * x)
    }
}
