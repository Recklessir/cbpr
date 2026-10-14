package com.cbpr.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureShotTest {
    private val frame = 1.0 / 60
    private val vanilla = Pose(10.0, 65.0, 10.0, 170.0, 15.0)
    private val mon = Point(0.0, 1.0, 0.0)
    private val ball = Point(0.0, 0.3, 8.0)

    private fun plan(reduced: Boolean = false) = BeatPlan(BeatPlan.Kind.CAPTURE, subjectIsMon = false, reducedMotion = reduced)

    private fun BattleCamera.run(seconds: Double): Pose? {
        var pose: Pose? = null
        repeat((seconds / frame).toInt()) { pose = update(frame, vanilla, mon, ball) }
        return pose
    }

    @Test
    fun aCaptureHoldsUntilConcludedThenEndsAfterAShortTail() {
        val shot = BeatShot(plan())
        repeat((8.0 / frame).toInt()) { shot.step(frame) }
        assertFalse(shot.finished, "no fixed length: it waits for the ball")
        assertTrue(shot.holdsEnd)
        assertTrue(shot.wantsBars)
        shot.conclude()
        repeat((1.2 / frame).toInt()) { shot.step(frame) }
        assertTrue(shot.finished)
        assertFalse(shot.holdsEnd)
    }

    @Test
    fun aCaptureWithNoEndStillStopsAtItsSafetyLimit() {
        val shot = BeatShot(plan())
        repeat((16.0 / frame).toInt()) { shot.step(frame) }
        assertTrue(shot.finished)
    }

    @Test
    fun otherKindsIgnoreConclude() {
        val shot = BeatShot(BeatPlan(BeatPlan.Kind.FAINT, subjectIsMon = true, reducedMotion = false))
        shot.conclude()
        repeat((1.0 / frame).toInt()) { shot.step(frame) }
        assertFalse(shot.finished)
    }

    @Test
    fun theShotIsLowAndCloseToTheBall() {
        val pose = Shots.capture(ball, mon, 0.0)
        assertTrue(pose.y - ball.y in 0.2..1.0, "height ${pose.y - ball.y}")
        val distance = kotlin.math.hypot(pose.x - ball.x, pose.z - ball.z)
        assertTrue(distance in 1.5..4.0, "distance $distance")
        val closer = Shots.capture(ball, mon, 1.0)
        assertTrue(kotlin.math.hypot(closer.x - ball.x, closer.z - ball.z) < distance)
    }

    @Test
    fun theCameraTakesTheBallStraightAwayAndGivesTheViewBackWhenConcluded() {
        val camera = BattleCamera()
        camera.beginCapture(vanilla, plan())
        assertEquals(BattleCamera.Phase.COMMAND, camera.phase)
        assertEquals(BeatPlan.Kind.CAPTURE, camera.beatKind)
        val pose = assertNotNull(camera.run(4.0))
        assertTrue(kotlin.math.hypot(pose.x - ball.x, pose.z - ball.z) < 6.0, "the camera is round the ball")
        assertTrue(camera.wantsBars)
        camera.concludeCapture()
        camera.run(1.5)
        assertNull(camera.beatKind)
        assertTrue(camera.phase == BattleCamera.Phase.RETURNING || camera.phase == BattleCamera.Phase.OFF)
        camera.run(3.0)
        assertEquals(BattleCamera.Phase.OFF, camera.phase)
    }

    @Test
    fun concludeWithoutACaptureDoesNothing() {
        val camera = BattleCamera()
        camera.begin(vanilla)
        camera.concludeCapture()
        assertEquals(BattleCamera.Phase.INTRO, camera.phase)
    }

    @Test
    fun theBallIsTheNearestOneWithinReachOfWhereThePokemonWas() {
        val stood = Point(0.0, 1.0, 8.0)
        val balls = listOf(Point(30.0, 1.0, 8.0), Point(2.0, 1.0, 9.0), Point(-4.0, 1.0, 8.0))
        assertEquals(1, CaptureSpot.nearest(balls, stood, 24.0))
        assertNull(CaptureSpot.nearest(listOf(Point(60.0, 1.0, 8.0)), stood, 24.0), "too far away to be this capture")
        assertNull(CaptureSpot.nearest(emptyList(), stood, 24.0))
    }
}
