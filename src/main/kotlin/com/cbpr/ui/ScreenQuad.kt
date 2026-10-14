package com.cbpr.ui

class ScreenQuad(private val xs: DoubleArray, private val ys: DoubleArray) {
    init {
        require(xs.size == 4 && ys.size == 4)
    }

    fun contains(x: Double, y: Double, grow: Double = 1.0): Boolean {
        val cx = xs.average()
        val cy = ys.average()
        var positive = false
        var negative = false
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val ax = cx + (xs[i] - cx) * grow
            val ay = cy + (ys[i] - cy) * grow
            val bx = cx + (xs[j] - cx) * grow
            val by = cy + (ys[j] - cy) * grow
            val cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
            if (cross > 0.0) positive = true
            if (cross < 0.0) negative = true
        }
        return !(positive && negative)
    }
}
