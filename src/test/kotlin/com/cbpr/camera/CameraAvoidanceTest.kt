package com.cbpr.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CameraAvoidanceTest {
    private val mon = Point(0.0, 1.0, 0.0)
    private val foe = Point(0.0, 1.0, 8.0)
    private val target = Pose(0.0, 2.3, -3.2, 0.0, 10.0)
    private val frame = 1.0 / 60

    private val wall = Sight { a, b -> !((a.z < -1.5) != (b.z < -1.5)) }
    private val open = Sight { _, _ -> true }
    private val shut = Sight { _, _ -> false }

    private fun run(avoid: CameraAvoidance, seconds: Double, t: Pose = target): Pose {
        var pose = t
        repeat((seconds / frame).toInt()) { pose = avoid.adjust(t, mon, foe, frame) }
        return pose
    }

    @Test
    fun aClearShotIsLeftAlone() {
        val avoid = CameraAvoidance(open)
        assertEquals(target, run(avoid, 1.0))
        assertEquals(CameraAvoidance.NONE, avoid.offset)
    }

    @Test
    fun aCameraBehindAWallIsPulledInFrontOfIt() {
        val avoid = CameraAvoidance(wall)
        val pose = run(avoid, 0.3)
        assertTrue(pose.z > -1.5, "camera z ${pose.z}")
        assertNotEquals(CameraAvoidance.NONE, avoid.offset)
        assertFalse(avoid.released)
    }

    @Test
    fun whenNothingWorksTheShotReleasesAfterTheTimeout() {
        val avoid = CameraAvoidance(shut)
        run(avoid, CameraAvoidance.RELEASE_SECONDS - 0.2)
        assertFalse(avoid.released)
        run(avoid, 0.5)
        assertTrue(avoid.released)
    }

    @Test
    fun theTimerRestartsOnceTheViewClears() {
        var clear = false
        val avoid = CameraAvoidance { _, _ -> clear }
        run(avoid, 1.0)
        clear = true
        run(avoid, 0.3)
        assertEquals(0.0, avoid.blockedSeconds)
        clear = false
        run(avoid, 1.0)
        assertFalse(avoid.released)
    }

    @Test
    fun theNudgeIsHeldBrieflyAfterTheWayClearsThenDropped() {
        var wallUp = true
        val sight = Sight { a, b -> !wallUp || wall.clear(a, b) }
        val avoid = CameraAvoidance(sight)
        run(avoid, 0.3)
        val held = avoid.offset
        assertNotEquals(CameraAvoidance.NONE, held)
        wallUp = false

        run(avoid, 0.2)
        assertEquals(held, avoid.offset)
        run(avoid, 0.6)
        assertEquals(CameraAvoidance.NONE, avoid.offset)
    }

    @Test
    fun aBlockedPokemonIsReportedHiddenOnlyWhileTheCameraCannotSeeIt() {
        val sight = Sight { _, b -> b.z < 4.0 }
        val avoid = CameraAvoidance(sight)
        run(avoid, 0.2)
        assertTrue(avoid.foeHidden)
        assertFalse(avoid.monHidden)
    }

    @Test
    fun raysPerCheckStayWithinTheBudget() {
        var calls = 0
        val avoid = CameraAvoidance { _, _ -> calls++; false }
        avoid.adjust(target, mon, foe, frame)
        assertTrue(calls <= CameraAvoidance.MAX_RAYS, "rays $calls")
        calls = 0
        run(avoid, 1.0)
        assertTrue(calls <= 10 * CameraAvoidance.MAX_RAYS, "rays in a second $calls")
    }

    @Test
    fun aPullInKeepsTheAimAndASwingLooksAtTheMiddle() {
        val focus = CameraAvoidance.focus(mon, foe)
        val pulled = CameraAvoidance.apply(CameraAvoidance.Offset(0.5, 0.0, 0.0), target, focus)
        assertEquals(target.yaw, pulled.yaw)
        assertEquals((target.z + focus.z) / 2, pulled.z, 1e-9)
        val swung = CameraAvoidance.apply(CameraAvoidance.Offset(1.0, 0.0, 90.0), target, focus)
        val aim = Shots.lookAt(Point(swung.x, swung.y, swung.z), focus)
        assertEquals(aim.yaw, swung.yaw, 1e-9)
        assertEquals((Point(target.x, target.y, target.z) - focus).length(), (Point(swung.x, swung.y, swung.z) - focus).length(), 1e-9)
    }

    @Test
    fun resetClearsEverything() {
        val avoid = CameraAvoidance(shut)
        run(avoid, 2.0)
        avoid.reset()
        assertEquals(0.0, avoid.blockedSeconds)
        assertFalse(avoid.released)
    }
}

class CameraAvoidanceDoublesTest {
    private val mon = Point(0.0, 1.0, 0.0)
    private val foe = Point(0.0, 1.0, 8.0)
    private val monB = Point(4.0, 1.0, 0.0)
    private val target = Pose(0.0, 2.3, -3.2, 0.0, 10.0)
    private val frame = 1.0 / 60

    private val hidesTheSecond = Sight { _, b -> b.x <= 2.0 }
    private val open = Sight { _, _ -> true }

    private fun run(avoid: CameraAvoidance, mons: List<Point>, foes: List<Point>) {
        repeat(60) { avoid.adjust(target, mon, foe, frame, mons, foes) }
    }

    @Test
    fun aPokemonHiddenBehindTheFirstOnItsSideIsStillNoticed() {
        val avoid = CameraAvoidance(hidesTheSecond)
        run(avoid, listOf(mon, monB), listOf(foe))
        assertTrue(avoid.monHidden || avoid.blockedSeconds > 0.0, "the second Mon is blocked")
        assertFalse(avoid.foeHidden)
    }

    @Test
    fun checkingOnlyTheCentreMissesIt() {
        val avoid = CameraAvoidance(hidesTheSecond)
        run(avoid, listOf(mon), listOf(foe))
        assertFalse(avoid.monHidden)
        assertEquals(0.0, avoid.blockedSeconds)
    }

    @Test
    fun everyPokemonInTheOpenIsClear() {
        val avoid = CameraAvoidance(open)
        run(avoid, listOf(mon, monB), listOf(foe, Point(-4.0, 1.0, 8.0)))
        assertEquals(CameraAvoidance.NONE, avoid.offset)
        assertFalse(avoid.monHidden || avoid.foeHidden)
    }
}
