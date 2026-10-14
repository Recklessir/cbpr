package com.cbpr.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BeatShotTuningTest {
    private fun shot(kind: BeatPlan.Kind, style: GimmickStyle = GimmickStyle.MEGA) = BeatShot(BeatPlan(kind, subjectIsMon = true, reducedMotion = false, style = style))

    @Test
    fun eachMomentHasItsOwnStiffness() {
        val stiffness = mapOf(
            BeatPlan.Kind.FAINT to 25.0,
            BeatPlan.Kind.SEND_OUT to 40.0,
            BeatPlan.Kind.SWITCH to 40.0,
            BeatPlan.Kind.VICTORY to 30.0,
            BeatPlan.Kind.DYNAMAX to 22.0,
            BeatPlan.Kind.CAPTURE to 20.0,
            BeatPlan.Kind.WEATHER to 25.0,
        )
        for ((kind, expected) in stiffness) assertEquals(expected, shot(kind).tuning.stiffness, "$kind")
    }

    @Test
    fun aGimmickCloseUpWhipsInThenRelaxes() {
        val shot = shot(BeatPlan.Kind.GIMMICK)
        assertEquals(75.0, shot.tuning.stiffness)
        shot.step(0.35)
        assertEquals(75.0, shot.tuning.stiffness)
        shot.step(0.01)
        assertEquals(40.0, shot.tuning.stiffness)
        assertEquals(0.7, shot.tuning.dampingRatio)
    }

    @Test
    fun otherMomentsKeepOneTuningThroughout() {
        val shot = shot(BeatPlan.Kind.FAINT)
        val first = shot.tuning
        shot.step(1.0)
        assertEquals(first, shot.tuning)
    }

    @Test
    fun onlyTheWinReturnsFast() {
        for (kind in BeatPlan.Kind.entries) {
            if (kind == BeatPlan.Kind.VICTORY) assertEquals(90.0, shot(kind).returnTuning?.stiffness) else assertNull(shot(kind).returnTuning, "$kind")
        }
    }

    @Test
    fun onlyGimmickCloseUpsLeadToAnAttack() {
        for (kind in BeatPlan.Kind.entries) {
            assertEquals(kind == BeatPlan.Kind.GIMMICK || kind == BeatPlan.Kind.DYNAMAX, shot(kind).leadsToAttack, "$kind")
        }
    }

    @Test
    fun theBarsWaitAsLongAsTheGimmickSays() {
        assertEquals(12.0, shot(BeatPlan.Kind.GIMMICK, GimmickStyle.MEGA).barHoldSeconds)
        assertEquals(8.0, shot(BeatPlan.Kind.GIMMICK, GimmickStyle.TERA).barHoldSeconds)
        assertEquals(BeatShot.DYNAMAX_BAR_HOLD_SECONDS, shot(BeatPlan.Kind.DYNAMAX).barHoldSeconds)
    }

    @Test
    fun onlyDynamaxSkipsFittingToTheSpace() {
        for (kind in BeatPlan.Kind.entries) assertEquals(kind != BeatPlan.Kind.DYNAMAX, shot(kind).fitsToSpace, "$kind")
    }

    @Test
    fun onlyACaptureCanBeConcluded() {
        for (kind in BeatPlan.Kind.entries) assertEquals(kind == BeatPlan.Kind.CAPTURE, shot(kind).conclude(), "$kind")
    }

    @Test
    fun aConcludedCaptureEndsSoonAfter() {
        val shot = shot(BeatPlan.Kind.CAPTURE)
        shot.step(2.0)
        assertTrue(shot.conclude())
        assertFalse(shot.finished)
        shot.step(1.1)
        assertTrue(shot.finished)
        assertNotEquals(0.0, shot.elapsed)
    }
}
