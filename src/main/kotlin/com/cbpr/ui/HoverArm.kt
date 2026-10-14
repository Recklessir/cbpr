package com.cbpr.ui

import kotlin.math.hypot

class HoverArm(private val moveThreshold: Double = MOVE_THRESHOLD_PIXELS) {
    var armed = false
        private set
    private var originX = 0.0
    private var originY = 0.0

    fun begin(x: Double, y: Double) {
        armed = false
        originX = x
        originY = y
    }

    fun update(x: Double, y: Double): Boolean {
        if (!armed && hypot(x - originX, y - originY) > moveThreshold) armed = true
        return armed
    }

    fun disarm() {
        armed = false
    }

    companion object {
        const val MOVE_THRESHOLD_PIXELS = 8.0
    }
}
