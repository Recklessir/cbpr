package com.cbpr.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DynamaxShotTest {
    private val subject = Point(0.0, 1.0, 0.0)
    private val other = Point(0.0, 1.0, 8.0)

    private fun camera(pose: Pose) = Point(pose.x, pose.y, pose.z)

    @Test
    fun itStartsHighOverTheFieldAndLooksDown() {
        val start = Shots.dynamax(subject, other, 1.0, 1.0, 0.0)
        assertTrue(start.y > 14.0, "height ${start.y}")
        assertTrue(start.pitch > 70.0, "pitch ${start.pitch}")
    }

    @Test
    fun itEndsFarBehindTheOtherPokemonLookingAtTheGiantOne() {
        val end = Shots.dynamax(subject, other, 1.0, 1.0, 1.0)
        assertTrue(end.z > other.z + 2.0, "z ${end.z}")
        val aim = Shots.lookAt(camera(end), Point(subject.x, subject.y + 0.75, subject.z))
        assertEquals(aim.yaw, end.yaw, 2.0)
    }

    @Test
    fun aBigOpponentPushesTheCameraFurtherBackAndHigherSoItDoesNotBlockTheShot() {
        val small = Shots.dynamax(subject, other, 1.0, 1.0, 1.0)
        val big = Shots.dynamax(subject, other, 1.0, 8.0, 1.0)
        assertTrue(big.z > small.z + 3.0, "big ${big.z} small ${small.z}")
        assertTrue(big.y > small.y + 3.0, "big ${big.y} small ${small.y}")
        assertTrue(big.y > other.y + 8.0 * 0.5, "above the Foe's middle")
        assertTrue(big.z - other.z > 8.0 * 0.7, "beyond the Foe's back")
    }

    @Test
    fun aBiggerGiantIsFramedFromFurtherAway() {
        val normal = Shots.dynamax(subject, other, 1.0, 1.0, 1.0)
        val huge = Shots.dynamax(subject, other, 4.0, 1.0, 1.0)
        assertTrue((camera(huge) - subject).length() > (camera(normal) - subject).length())
    }

    @Test
    fun theDistanceBehindTheOpponentIsCappedForAnEnormousOne() {
        val end = Shots.dynamax(subject, other, 1.0, 100.0, 0.62)
        assertTrue(end.z - other.z <= 16.0 + 1e-6, "behind by ${end.z - other.z}")
    }

    @Test
    fun theMovementIsContinuousWithNoJumps() {
        var last = Shots.dynamax(subject, other, 1.0, 1.0, 0.0)
        for (i in 1..200) {
            val next = Shots.dynamax(subject, other, 1.0, 1.0, i / 200.0)
            assertTrue((camera(next) - camera(last)).length() < 1.5, "jump at step $i")
            last = next
        }
    }

    @Test
    fun theFadeIsSolidFarAwayAndSeeThroughWhenTheCameraIsNearlyOnThePokemon() {
        assertEquals(1.0, CameraSpace.fadeAlpha(10.0, 1.5), 1e-9)
        assertEquals(CameraSpace.MIN_FADE_ALPHA, CameraSpace.fadeAlpha(0.2, 1.5), 1e-9)
        val middle = CameraSpace.fadeAlpha(2.0, 1.5)
        assertTrue(middle > CameraSpace.MIN_FADE_ALPHA && middle < 1.0, "middle $middle")
    }

    @Test
    fun aBiggerPokemonStartsFadingFromFurtherAway() {
        assertTrue(CameraSpace.fadeAlpha(3.0, 3.0) < CameraSpace.fadeAlpha(3.0, 1.0))
    }

    @Test
    fun theMenuSwingScalesWithTheOwnersSizeAndStaysBesideIt() {
        val owner = Point(0.0, 1.0, 0.0)
        val normal = Shots.menuView(owner, other, 0.0)
        val bigger = Shots.menuView(owner, other, 0.0, 2.0)
        assertTrue(kotlin.math.abs(bigger.x - owner.x) > kotlin.math.abs(normal.x - owner.x) * 1.3)
        assertTrue(bigger.z < normal.z)
    }

    private val orbit = Shots.DYNAMAX_ORBIT_DEGREES
    private val arrive = Shots.DYNAMAX_ARRIVE_AT

    @Test
    fun theOrbitCirclesTheGiantAtASteadyHeightAlwaysLookingAtIt() {
        val aim = Point(subject.x, subject.y + 2.0 * 0.3, subject.z)
        var last = Shots.dynamax(subject, other, 1.0, 1.0, arrive, orbit)
        val start = (camera(last) - subject).length()
        var farthest = start
        for (i in 1..200) {
            val progress = arrive + (1.0 - arrive) * i / 200
            val pose = Shots.dynamax(subject, other, 1.0, 1.0, progress, orbit)
            assertTrue((camera(pose) - camera(last)).length() < 1.0, "jump at step $i")
            assertEquals(last.y, pose.y, 1e-9, "steady height")
            val want = Shots.lookAt(camera(pose), aim)
            assertEquals(want.yaw, pose.yaw, 1e-6, "looks at the giant")
            farthest = maxOf(farthest, (camera(pose) - subject).length())
            last = pose
        }
        assertTrue(farthest <= start * Shots.DYNAMAX_ORBIT_RADIUS_SCALE * 1.001, "stays within the orbit radius")
    }

    @Test
    fun halfwayThroughTheOrbitTheCameraIsOnTheFarSideOfTheGiant() {
        val before = Shots.dynamax(subject, other, 1.0, 1.0, arrive, orbit)
        val half = Shots.dynamax(subject, other, 1.0, 1.0, arrive + (1.0 - arrive) * 0.5, orbit)
        assertTrue(before.z > subject.z + 2.0, "starts behind the Foe")
        assertTrue(half.z < subject.z - 2.0, "half a turn later it is on the other side")
    }

    @Test
    fun noOrbitIsTheOldPushInAndReducedMotionSkipsTheOrbit() {
        val plain = Shots.dynamax(subject, other, 1.0, 1.0, 0.8)
        assertEquals(plain, Shots.dynamax(subject, other, 1.0, 1.0, 0.8, 0.0))
        val reduced = BeatShot(BeatPlan(BeatPlan.Kind.DYNAMAX, true, reducedMotion = true))
        val full = BeatShot(BeatPlan(BeatPlan.Kind.DYNAMAX, true, reducedMotion = false))
        repeat(60 * 4) {
            reduced.step(1.0 / 60)
            full.step(1.0 / 60)
        }
        val a = reduced.pose(subject, other)!!
        val b = full.pose(subject, other)!!
        assertTrue((camera(a) - camera(b)).length() > 1.0, "the orbit only runs with full motion")
    }
}
