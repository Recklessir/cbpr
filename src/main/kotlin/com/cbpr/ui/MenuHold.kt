package com.cbpr.ui

import kotlin.math.max

class MenuHold(private val holdSeconds: Double = HOLD_SECONDS) {
    private var left = 0.0
    private var inMenu = false

    val held: Boolean get() = !inMenu && left > 0.0

    fun update(screen: BattleScreen, acting: Boolean, dt: Double): Boolean {
        inMenu = screen.sub
        left = when {
            screen.sub -> holdSeconds
            screen == BattleScreen.GENERAL || acting -> 0.0
            else -> max(0.0, left - dt)
        }
        return inMenu || left > 0.0
    }

    fun reset() {
        left = 0.0
        inMenu = false
    }

    companion object {
        const val HOLD_SECONDS = 3.0
    }
}
