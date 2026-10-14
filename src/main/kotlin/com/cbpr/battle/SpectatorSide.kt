package com.cbpr.battle

import java.util.UUID

class SpectatorSide {
    private var battle: UUID? = null
    private var firstIsMon: Boolean? = null

    fun firstIsMon(id: UUID, distances: () -> Pair<Double?, Double?>): Boolean? {
        if (id != battle) {
            battle = id
            firstIsMon = null
        }
        firstIsMon?.let { return it }
        val (first, second) = distances()
        if (first == null || second == null) return null
        return (first <= second).also { firstIsMon = it }
    }
}
