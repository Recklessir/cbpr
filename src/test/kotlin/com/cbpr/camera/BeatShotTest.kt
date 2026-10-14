package com.cbpr.camera

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BeatShotTest {
    private val frame = 1.0 / 60
    private val mon = Point(0.0, 1.0, 0.0)
    private val foe = Point(0.0, 1.0, 8.0)

    private fun beat(
        kind: BeatPlan.Kind,
        subjectIsMon: Boolean = true,
        reduced: Boolean = false,
        darkened: Boolean = false,
    ) = BeatShot(BeatPlan(kind, subjectIsMon, reduced, darkened))

    private fun advance(shot: BeatShot, seconds: Double) = repeat((seconds / frame).toInt()) { shot.step(frame) }

    @Test
    fun everyKindFinishesAfterAFixedTimeWithNothingToWaitFor() {

        for (kind in BeatPlan.Kind.entries - BeatPlan.Kind.CAPTURE) {
            val shot = beat(kind)
            assertFalse(shot.finished)
            advance(shot, 8.0)
            assertTrue(shot.finished, "$kind should be over after eight seconds")
        }
    }

    @Test
    fun theKindsHaveTheirOwnLengths() {
        val faint = beat(BeatPlan.Kind.FAINT)
        val swoop = beat(BeatPlan.Kind.SEND_OUT)
        val orbit = beat(BeatPlan.Kind.VICTORY)
        advance(faint, 1.0)
        advance(swoop, 1.0)
        advance(orbit, 1.0)
        assertFalse(swoop.finished)
        assertFalse(orbit.finished, "the win holds for about a second")
        advance(swoop, 0.7)
        advance(orbit, 0.2)
        assertTrue(swoop.finished)
        assertTrue(orbit.finished, "and then the camera goes straight back, with no recovery of its own")
        assertFalse(faint.finished, "the faint is still playing")
        advance(faint, 1.4)
        assertTrue(faint.finished, "the faint is the longest")
    }

    @Test
    fun theBarsLeaveWhenTheMainPartEndsAndTheCameraStartsRecovering() {
        val shot = beat(BeatPlan.Kind.FAINT)
        assertTrue(shot.wantsBars)
        advance(shot, 1.9)
        assertFalse(shot.wantsBars)
        assertFalse(shot.finished)
    }

    @Test
    fun theMainPartHasAFramingAndTheRecoveryHandsBackToTheCommandShot() {
        val shot = beat(BeatPlan.Kind.SEND_OUT)
        assertNotNull(shot.pose(mon, foe))
        advance(shot, 1.2)
        assertNull(shot.pose(mon, foe))
    }

    @Test
    fun aFaintDimsTheWorldOnlyWhileItPlays() {
        val shot = beat(BeatPlan.Kind.FAINT)
        assertEquals(1.0, shot.tint)
        advance(shot, 1.9)
        assertEquals(0.0, shot.tint)
    }

    @Test
    fun aWinIsBrightUnlessTheFoeWonThenItIsDark() {
        assertEquals(0.0, beat(BeatPlan.Kind.VICTORY, darkened = false).tint)
        assertEquals(1.0, beat(BeatPlan.Kind.VICTORY, darkened = true).tint)
        assertEquals(0.0, beat(BeatPlan.Kind.SEND_OUT).tint)
    }

    @Test
    fun onlyAWinHoldsTheEndOfTheBattleAndOnlyUntilItIsOver() {
        assertFalse(beat(BeatPlan.Kind.FAINT).holdsEnd)
        assertFalse(beat(BeatPlan.Kind.SEND_OUT).holdsEnd)
        val win = beat(BeatPlan.Kind.VICTORY)
        assertTrue(win.holdsEnd)
        advance(win, 6.0)
        assertFalse(win.holdsEnd)
    }

    @Test
    fun reducedMotionShortensEveryKind() {
        for (kind in BeatPlan.Kind.entries - BeatPlan.Kind.CAPTURE) {
            val normal = beat(kind)
            val reduced = beat(kind, reduced = true)
            var normalTime = 0.0
            var reducedTime = 0.0
            while (!normal.finished && normalTime < 10) { normal.step(frame); normalTime += frame }
            while (!reduced.finished && reducedTime < 10) { reduced.step(frame); reducedTime += frame }
            assertTrue(reducedTime < normalTime, "$kind: reduced $reducedTime normal $normalTime")
        }
    }

    @Test
    fun theOrbitGoesRoundAndReducedMotionOnlyDriftsNearlyStill() {
        fun turned(reduced: Boolean): Double {
            val shot = beat(BeatPlan.Kind.VICTORY, reduced = reduced)
            val start = assertNotNull(shot.pose(mon, foe))
            val speed = if (reduced) 0.6 else 1.0
            advance(shot, 1.05 * speed)
            val end = assertNotNull(shot.pose(mon, foe))
            return abs(BattleCamera.nearest(start.yaw, end.yaw) - start.yaw)
        }
        assertTrue(turned(reduced = false) > 30.0, "full orbit turned ${turned(false)}")
        assertTrue(turned(reduced = true) < turned(reduced = false) / 2, "reduced orbit turned ${turned(true)}")
    }

    @Test
    fun theSubjectDecidesWhoTheShotIsAbout() {
        val aboutMon = beat(BeatPlan.Kind.FAINT, subjectIsMon = true)
        val aboutFoe = beat(BeatPlan.Kind.FAINT, subjectIsMon = false)
        val a = assertNotNull(aboutMon.pose(mon, foe))
        val b = assertNotNull(aboutFoe.pose(mon, foe))
        assertTrue(a.z < foe.z / 2, "camera near the Mon, z ${a.z}")
        assertTrue(b.z > foe.z / 2, "camera near the foe, z ${b.z}")
    }

    @Test
    fun everyPoseIsFinite() {

        for (kind in BeatPlan.Kind.entries - BeatPlan.Kind.CAPTURE) {
            val shot = beat(kind)
            repeat(400) {
                shot.pose(mon, foe)?.let {
                    assertTrue(it.x.isFinite() && it.y.isFinite() && it.z.isFinite() && it.yaw.isFinite() && it.pitch.isFinite())
                }
                shot.step(frame)
            }
        }
    }

    @Test
    fun coincidentPokemonDoNotBreakAnyShot() {
        for (kind in BeatPlan.Kind.entries) {
            val pose = assertNotNull(beat(kind).pose(mon, mon))
            assertTrue(pose.x.isFinite() && pose.z.isFinite() && pose.yaw.isFinite())
        }
    }
}
