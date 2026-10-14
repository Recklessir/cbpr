package com.cbpr.ui

import com.cbpr.camera.Spring
import kotlin.math.abs

class BannerMotion<K : Any> {
    private class State {
        val pop = Spring(0.0, ENTER_STIFFNESS, Spring.dampingFor(ENTER_STIFFNESS, ENTER_DAMPING_RATIO))
        val hover = Spring(1.0, HOVER_STIFFNESS, Spring.dampingFor(HOVER_STIFFNESS, HOVER_DAMPING_RATIO))
        var delay = 0.0
        var wanted = false
        var entering = false
    }

    private val states = HashMap<K, State>()
    private fun state(key: K) = states.getOrPut(key) { State() }

    val keys: Set<K> get() = states.keys

    var reduced = false

    private fun enterStiffness() = if (reduced) REDUCED_ENTER_STIFFNESS else ENTER_STIFFNESS
    private fun enterDamping() = Spring.dampingFor(enterStiffness(), if (reduced) 1.0 else ENTER_DAMPING_RATIO)
    private fun turn(order: Int) = if (reduced) 0.0 else order * STAGGER_SECONDS

    fun setVisible(key: K, visible: Boolean, order: Int = 0) {
        val s = state(key)
        if (visible == s.wanted) return
        s.wanted = visible
        s.entering = false
        if (visible) {
            s.delay = turn(order)
            s.pop.stiffness = enterStiffness()
            s.pop.damping = enterDamping()
        } else {

            s.delay = 0.0
            s.pop.stiffness = EXIT_STIFFNESS
            s.pop.damping = Spring.dampingFor(EXIT_STIFFNESS, 1.0)
        }
    }

    fun replay(key: K, order: Int) {
        val s = state(key)
        s.wanted = true
        s.entering = true
        s.pop.snap(0.0)
        s.pop.stiffness = enterStiffness()
        s.pop.damping = enterDamping()
        s.delay = turn(order)
    }

    fun isEntering(key: K): Boolean {
        val s = states[key] ?: return false
        return s.wanted && s.entering && (s.delay > 0.0 || abs(1.0 - s.pop.value) > ENTRANCE_DONE)
    }

    fun step(dt: Double, hovered: K?) {
        for ((key, s) in states) {
            if (s.delay > 0.0) {
                s.delay -= dt
            } else {
                s.pop.step(if (s.wanted) 1.0 else 0.0, dt)
            }
            s.hover.step(if (key == hovered && s.wanted && !reduced) HOVER_SCALE else 1.0, dt)
        }
    }

    fun scale(key: K): Double = popScale(key) * (states[key]?.hover?.value ?: 1.0)

    fun popScale(key: K): Double = (states[key]?.pop?.value ?: 0.0).coerceAtLeast(0.0)

    fun hidden(key: K): Boolean = states[key]?.let { !it.wanted && abs(it.pop.value) < HIDDEN_SCALE } ?: true

    fun reset() {
        states.clear()
    }

    companion object {
        const val STAGGER_SECONDS = 0.06
        const val ENTRANCE_DONE = 0.02

        private const val ENTER_STIFFNESS = 220.0

        private const val ENTER_DAMPING_RATIO = 0.63
        private const val REDUCED_ENTER_STIFFNESS = 500.0
        private const val EXIT_STIFFNESS = 900.0
        private const val HOVER_SCALE = 1.07
        private const val HOVER_STIFFNESS = 320.0
        private const val HOVER_DAMPING_RATIO = 0.45
        private const val HIDDEN_SCALE = 0.02
    }
}
