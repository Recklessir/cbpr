package com.cbpr.camera

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShotsBeatTest {
    private val subject = Point(0.0, 1.0, 0.0)
    private val other = Point(0.0, 1.0, 8.0)

    private fun horizontal(pose: Pose) = hypot(pose.x - subject.x, pose.z - subject.z)

    @Test
    fun aPullBackStartsCloseAndLowAndEndsFarAndHigh() {
        val start = Shots.pullBack(subject, other, 0.0)
        val end = Shots.pullBack(subject, other, 1.0)
        assertTrue(horizontal(end) > horizontal(start) * 1.8, "start ${horizontal(start)} end ${horizontal(end)}")
        assertTrue(end.y > start.y + 1.0)
    }

    @Test
    fun aPullBackSitsOnTheFarSideOfTheSubjectFromTheOtherPokemon() {
        val pose = Shots.pullBack(subject, other, 0.5)
        assertTrue(pose.z < subject.z, "camera z ${pose.z}")
    }

    @Test
    fun aPullBackStaysPointedAtTheSubjectAsItGrows() {
        for (p in listOf(0.0, 0.3, 0.7, 1.0)) {
            val pose = Shots.pullBack(subject, other, p)
            val toSubject = Shots.lookAt(Point(pose.x, pose.y, pose.z), subject)
            assertEquals(toSubject.yaw, pose.yaw, 1e-6)
        }
    }

    @Test
    fun aSwoopStartsLowCloseAndToTheSideThenSettlesBehindTheSubject() {
        val start = Shots.swoop(subject, other, 0.0)
        val end = Shots.swoop(subject, other, 1.0)
        assertTrue(start.y < end.y)
        assertTrue(horizontal(start) < horizontal(end))
        assertTrue(abs(start.x) > 1.0, "starts off to the side, x ${start.x}")
        assertEquals(0.0, end.x, 1e-9)
        assertTrue(end.z < subject.z)
    }

    @Test
    fun anOrbitKeepsItsDistanceWhileGoingRound() {
        val distances = (0..10).map { horizontal(Shots.orbit(subject, other, it / 10.0, 200.0)) }
        assertTrue(distances.max() - distances.min() < 1e-9, "distances $distances")
        val first = Shots.orbit(subject, other, 0.0, 200.0)
        val last = Shots.orbit(subject, other, 1.0, 200.0)
        assertTrue(abs(BattleCamera.nearest(first.yaw, last.yaw) - first.yaw) > 150.0)
    }

    @Test
    fun anOrbitRisesAndAlwaysLooksAtTheWinner() {
        val low = Shots.orbit(subject, other, 0.0, 200.0)
        val high = Shots.orbit(subject, other, 1.0, 200.0)
        assertTrue(high.y > low.y)
        val lookAt = Shots.lookAt(Point(high.x, high.y, high.z), Point(subject.x, subject.y + 0.1, subject.z))
        assertEquals(lookAt.yaw, high.yaw, 1e-6)
    }

    @Test
    fun aSmallerSweepGoesRoundLess() {
        val full = Shots.orbit(subject, other, 1.0, 200.0)
        val gentle = Shots.orbit(subject, other, 1.0, 40.0)
        val start = Shots.orbit(subject, other, 0.0, 200.0)
        val turnedFull = abs(BattleCamera.nearest(start.yaw, full.yaw) - start.yaw)
        val turnedGentle = abs(BattleCamera.nearest(start.yaw, gentle.yaw) - start.yaw)
        assertTrue(turnedGentle < turnedFull / 2)
    }

    @Test
    fun theRotationsKeepEveryPoseFiniteWhenThePokemonShareASpot() {
        for (pose in listOf(Shots.pullBack(subject, subject, 0.5), Shots.swoop(subject, subject, 0.5), Shots.orbit(subject, subject, 0.5, 200.0))) {
            assertTrue(pose.x.isFinite() && pose.y.isFinite() && pose.z.isFinite() && pose.yaw.isFinite() && pose.pitch.isFinite())
        }
    }
}
