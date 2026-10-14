package com.cbpr.camera

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object TargetPicker {
    class Candidate(val slot: String, val min: Point, val max: Point)

    fun pick(origin: Point, direction: Point, candidates: List<Candidate>, keep: String? = null, keepGrow: Double = 0.0): String? {
        val direct = nearest(origin, direction, candidates)
        if (direct != null || keep == null || keepGrow <= 0.0) return direct
        val held = candidates.firstOrNull { it.slot == keep } ?: return null
        return if (hit(origin, direction, grow(held, keepGrow)) != null) keep else null
    }

    private fun grow(box: Candidate, by: Double) =
        Candidate(box.slot, Point(box.min.x - by, box.min.y - by, box.min.z - by), Point(box.max.x + by, box.max.y + by, box.max.z + by))

    private fun nearest(origin: Point, direction: Point, candidates: List<Candidate>): String? {
        var best: String? = null
        var bestDistance = Double.MAX_VALUE
        for (candidate in candidates) {
            val distance = hit(origin, direction, candidate) ?: continue
            if (distance < bestDistance) {
                bestDistance = distance
                best = candidate.slot
            }
        }
        return best
    }

    fun hit(origin: Point, direction: Point, box: Candidate): Double? {
        var near = 0.0
        var far = Double.MAX_VALUE
        val axes = listOf(
            Triple(origin.x, direction.x, box.min.x to box.max.x),
            Triple(origin.y, direction.y, box.min.y to box.max.y),
            Triple(origin.z, direction.z, box.min.z to box.max.z),
        )
        for ((o, d, range) in axes) {
            val (low, high) = range
            if (abs(d) < PARALLEL) {
                if (o < low || o > high) return null
                continue
            }
            val a = (low - o) / d
            val b = (high - o) / d
            near = max(near, min(a, b))
            far = min(far, max(a, b))
            if (far < near) return null
        }
        return near
    }

    private const val PARALLEL = 1e-12
}
