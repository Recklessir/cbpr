package com.cbpr.battle

import com.cbpr.camera.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureWatchTest {
    private val world = HashMap<String, Point>()

    private fun watch() = CaptureWatch<String>({ world.keys.toList() }, { ball, _ -> world[ball] })

    private val mine = Point(0.0, 1.0, 0.0)
    private val foe = Point(5.0, 1.0, 0.0)

    @Test
    fun startsWhenABallShakesAndEndsWhenItStops() {
        val watch = watch()
        assertEquals(CaptureWatch.Change.NONE, watch.update(false))
        assertEquals(CaptureWatch.Change.WANTS_START, watch.update(true))

        assertEquals(CaptureWatch.Change.WANTS_START, watch.update(true))
        watch.begin(mine, foe)
        assertEquals(CaptureWatch.Change.NONE, watch.update(true))
        assertEquals(CaptureWatch.Change.ENDED, watch.update(false))
        assertFalse(watch.capturing)
    }

    @Test
    fun aimsAtTheFoesSpotWhenNoBallIsThere() {
        val watch = watch()
        watch.begin(mine, foe)
        assertEquals(mine to foe, watch.spots(0f))
    }

    @Test
    fun followsTheNearestBallWithinReachAndLiftsIt() {
        val watch = watch()
        world["far"] = Point(40.0, 1.0, 0.0)
        world["near"] = Point(6.0, 0.0, 1.0)
        watch.begin(mine, foe)
        assertEquals(mine to Point(6.0, 0.15, 1.0), watch.spots(0f))
        world["near"] = Point(7.0, 0.0, 2.0)
        assertEquals(mine to Point(7.0, 0.15, 2.0), watch.spots(0f))
    }

    @Test
    fun ignoresBallsOutOfReach() {
        val watch = watch()
        world["far"] = Point(40.0, 1.0, 0.0)
        watch.begin(mine, foe)
        assertEquals(mine to foe, watch.spots(0f))
    }

    @Test
    fun aBallThatAppearsLaterIsFoundOnTheNextUpdate() {
        val watch = watch()
        watch.begin(mine, foe)
        world["ball"] = Point(5.5, 0.0, 0.0)
        watch.update(true)
        assertEquals(mine to Point(5.5, 0.15, 0.0), watch.spots(0f))
    }

    @Test
    fun aRemovedBallLeavesTheLastSpot() {
        val watch = watch()
        world["ball"] = Point(6.0, 0.0, 0.0)
        watch.begin(mine, foe)
        val seen = watch.spots(0f)
        world.remove("ball")
        assertEquals(seen, watch.spots(0f))
    }

    @Test
    fun spotsAreLetGoOnceTheCaptureShotIsOver() {
        val watch = watch()
        watch.begin(mine, foe)
        watch.settle(captureShotPlaying = true)
        assertEquals(mine to foe, watch.spots(0f))
        watch.settle(captureShotPlaying = false)
        assertNull(watch.spots(0f))
    }

    @Test
    fun theBattleEndingDuringACaptureConcludesItOnce() {
        val watch = watch()
        assertFalse(watch.conclude())
        watch.begin(mine, foe)
        assertTrue(watch.conclude())
        assertFalse(watch.conclude())
    }

    @Test
    fun clearForgetsEverything() {
        val watch = watch()
        world["ball"] = Point(6.0, 0.0, 0.0)
        watch.begin(mine, foe)
        watch.clear()
        assertFalse(watch.capturing)
        assertNull(watch.spots(0f))
    }
}
