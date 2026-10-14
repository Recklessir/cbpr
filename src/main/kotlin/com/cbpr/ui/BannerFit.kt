package com.cbpr.ui

import kotlin.math.max
import kotlin.math.min

object BannerFit {
    class Bounds(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double) {
        val width: Double get() = maxX - minX
        val height: Double get() = maxY - minY
    }

    class Fit(val scale: Double, val shiftX: Double, val shiftY: Double) {
        val unchanged: Boolean get() = scale == 1.0 && shiftX == 0.0 && shiftY == 0.0
    }

    const val LIMIT_X = 0.92
    const val LIMIT_TOP = 0.92
    const val LIMIT_BOTTOM = 0.78

    fun compute(bounds: Bounds, allowScale: Boolean = true, limitX: Double = LIMIT_X, top: Double = LIMIT_TOP, bottom: Double = LIMIT_BOTTOM): Fit {
        var scale = 1.0
        if (allowScale) {
            if (bounds.width > 2 * limitX) scale = min(scale, 2 * limitX / bounds.width)
            if (bounds.height > top + bottom) scale = min(scale, (top + bottom) / bounds.height)
        }
        val centreX = (bounds.minX + bounds.maxX) / 2
        val centreY = (bounds.minY + bounds.maxY) / 2
        val halfWidth = bounds.width * scale / 2
        val halfHeight = bounds.height * scale / 2
        val shiftX = shift(centreX - halfWidth, centreX + halfWidth, -limitX, limitX)
        val shiftY = shift(centreY - halfHeight, centreY + halfHeight, -bottom, top)
        return Fit(scale, shiftX, shiftY)
    }

    private fun shift(low: Double, high: Double, limitLow: Double, limitHigh: Double): Double = when {
        low < limitLow -> limitLow - low
        high > limitHigh -> limitHigh - high
        else -> 0.0
    }

    fun boundsOf(points: List<Pair<Double, Double>>): Bounds? {
        if (points.isEmpty()) return null
        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for ((x, y) in points) {
            minX = min(minX, x)
            maxX = max(maxX, x)
            minY = min(minY, y)
            maxY = max(maxY, y)
        }
        return Bounds(minX, maxX, minY, maxY)
    }
}
