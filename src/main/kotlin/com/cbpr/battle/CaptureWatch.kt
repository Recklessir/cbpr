package com.cbpr.battle

import com.cbpr.camera.CaptureSpot
import com.cbpr.camera.Point
import org.slf4j.LoggerFactory

class CaptureWatch<B : Any>(private val balls: () -> List<B>, private val positionOf: (B, Float) -> Point?) {
    enum class Change { NONE, WANTS_START, ENDED }

    private val log = LoggerFactory.getLogger("cbpr")

    var capturing = false
        private set
    private var spots: Pair<Point, Point>? = null

    private var ball: B? = null
    private var ballSearched = false

    fun update(shaking: Boolean): Change {
        if (capturing && !ballSearched) findBall()
        if (shaking && !capturing) return Change.WANTS_START
        if (!shaking && capturing) {
            capturing = false
            return Change.ENDED
        }
        return Change.NONE
    }

    fun begin(mine: Point, foe: Point) {
        spots = mine to foe
        capturing = true
        ball = null
        ballSearched = false
        findBall()
    }

    fun conclude(): Boolean = capturing.also { capturing = false }

    fun spots(partialTick: Float): Pair<Point, Point>? {
        val held = spots ?: return null
        val at = ball?.let { positionOf(it, partialTick) } ?: return held
        return (held.first to Point(at.x, at.y + BALL_LIFT, at.z)).also { spots = it }
    }

    fun settle(captureShotPlaying: Boolean) {
        if (spots != null && !captureShotPlaying) spots = null
    }

    fun clear() {
        capturing = false
        spots = null
        ball = null
        ballSearched = false
    }

    private fun findBall() {
        val near = spots?.second ?: return
        val thrown = balls()
        val index = CaptureSpot.nearest(thrown.map { positionOf(it, 1f) ?: return }, near, BALL_SEARCH_RADIUS) ?: return
        ball = thrown[index]
        ballSearched = true
        log.info("CBPR capture shot follows the thrown ball")
    }

    private companion object {
        const val BALL_LIFT = 0.15
        const val BALL_SEARCH_RADIUS = 24.0
    }
}
