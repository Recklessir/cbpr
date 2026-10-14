package com.cbpr.camera

import com.cbpr.battle.ImpactFlags
import com.cbpr.battle.MoveProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActionShotTest {
    private val frame = 1.0 / 60
    private val mon = Point(0.0, 1.0, 0.0)
    private val foe = Point(0.0, 1.0, 8.0)
    private val plain = ImpactFlags(crit = false, superEffective = false)
    private val crit = ImpactFlags(crit = true, superEffective = false)
    private val superEffective = ImpactFlags(crit = false, superEffective = true)

    private val profile = MoveProfile(
        "test", emptySet(), emptySet(), anticipation = 0.2, approach = 0.4, waitForImpact = 1.0, hold = 0.5,
        distanceScale = 0.7, heightScale = 0.9, swingDegrees = 30.0, fovKick = 5.0, shake = 0.6, tilt = 1.0,
        hitStop = 0.06, callout = true, sound = null,
    )

    private fun plan(
        attackerIsMon: Boolean = true,
        reduced: Boolean = false,
        hitStop: Boolean = true,
        critSpecials: Boolean = true,
        p: MoveProfile = profile,
    ) = ShotPlan(p, attackerIsMon, reduced, hitStop, critSpecials)

    private fun advance(shot: ActionShot, seconds: Double) = repeat((seconds / frame).toInt()) { shot.step(frame) }

    @Test
    fun withoutAHitTheShotTimesOutAndFinishes() {
        val shot = ActionShot(plan())
        assertEquals(ActionShot.Stage.ANTICIPATE, shot.stage)
        advance(shot, 0.25)
        assertEquals(ActionShot.Stage.APPROACH, shot.stage)
        advance(shot, 1.5)
        assertEquals(ActionShot.Stage.RECOVER, shot.stage)
        advance(shot, 0.6)
        assertTrue(shot.finished)
    }

    @Test
    fun aHitDuringTheApproachStartsTheImpactThenRecovers() {
        val shot = ActionShot(plan())
        advance(shot, 0.4)
        val effects = assertNotNull(shot.impact(plain))
        assertEquals(ActionShot.Stage.IMPACT, shot.stage)
        assertEquals(5.0, effects.fovKick)
        assertEquals(0.06, effects.hitStop)
        advance(shot, 0.55)
        assertEquals(ActionShot.Stage.RECOVER, shot.stage)
        advance(shot, 0.6)
        assertTrue(shot.finished)
    }

    @Test
    fun aHitCanLandEvenBeforeTheAnticipationEnds() {
        val shot = ActionShot(plan())
        assertNotNull(shot.impact(plain))
        assertEquals(ActionShot.Stage.IMPACT, shot.stage)
    }

    @Test
    fun aHitWhileRecoveringOrFinishedIsIgnored() {
        val shot = ActionShot(plan())
        advance(shot, 2.0)
        assertEquals(ActionShot.Stage.RECOVER, shot.stage)
        assertNull(shot.impact(plain))
        advance(shot, 1.0)
        assertNull(shot.impact(plain))
    }

    @Test
    fun aHitThatNeverArrivesCannotHoldTheCameraForever() {
        val slow = profile.let {
            MoveProfile(it.id, it.moves, it.types, 2.0, 3.0, 4.0, 3.0, 0.7, 0.9, 30.0, 5.0, 0.6, 1.0, 0.06, true, null)
        }
        val shot = ActionShot(plan(p = slow))
        advance(shot, 6.5)
        assertTrue(shot.finished)
    }

    @Test
    fun criticalAndSuperEffectiveHitsKickAndShakeHarderAndTilt() {
        val normal = assertNotNull(ActionShot(plan()).impact(plain))
        val crits = assertNotNull(ActionShot(plan()).impact(crit))
        val supers = assertNotNull(ActionShot(plan()).impact(superEffective))
        assertTrue(crits.fovKick > normal.fovKick)
        assertTrue(crits.shake > normal.shake)
        assertEquals(6.0, crits.tiltDegrees)
        assertEquals(4.0, supers.tiltDegrees)
        assertEquals(1.0, normal.tiltDegrees)
    }

    @Test
    fun theTiltFlipsWhenTheFoeIsAttacking() {
        val foeCrit = assertNotNull(ActionShot(plan(attackerIsMon = false)).impact(crit))
        assertEquals(-6.0, foeCrit.tiltDegrees)
    }

    @Test
    fun withoutCritSpecialsAStrongHitOnlyUsesTheProfileTilt() {
        val effects = assertNotNull(ActionShot(plan(critSpecials = false)).impact(crit))
        assertEquals(1.0, effects.tiltDegrees)
        assertTrue(effects.fovKick > profile.fovKick, "the bigger kick still applies")
    }

    @Test
    fun reducedMotionRemovesShakeAndTiltHalvesTheKickAndShortensTheShot() {
        val reduced = ActionShot(plan(reduced = true))
        val effects = assertNotNull(reduced.impact(crit))
        assertEquals(0.0, effects.shake)
        assertEquals(0.0, effects.tiltDegrees)
        assertEquals(0.0, effects.hitStop)
        assertEquals(5.0 * 1.6 * 0.5, effects.fovKick, 1e-9)

        val timing = ActionShot(plan(reduced = true))
        advance(timing, 0.15)
        assertEquals(ActionShot.Stage.APPROACH, timing.stage, "anticipation is 40% shorter")
    }

    @Test
    fun hitStopCanBeSwitchedOff() {
        assertEquals(0.0, assertNotNull(ActionShot(plan(hitStop = false)).impact(plain)).hitStop)
    }

    @Test
    fun laterHitsOfAMultiHitMoveKickSofterAndExtendTheHold() {
        val shot = ActionShot(plan())
        val first = assertNotNull(shot.impact(plain))
        advance(shot, 0.4)
        val second = assertNotNull(shot.impact(plain))
        assertTrue(second.fovKick < first.fovKick)
        assertEquals(ActionShot.Stage.IMPACT, shot.stage)
        advance(shot, 0.4)
        assertEquals(ActionShot.Stage.IMPACT, shot.stage, "the second hit restarted the hold timer")
    }

    @Test
    fun aLateCritUpgradesTheImpactAlreadyPlaying() {
        val shot = ActionShot(plan())
        assertNull(shot.upgrade(crit), "nothing to upgrade before a hit")
        shot.impact(plain)
        assertNull(shot.upgrade(plain), "a plain flag is not an upgrade")
        val upgraded = assertNotNull(shot.upgrade(crit))
        assertEquals(6.0, upgraded.tiltDegrees)
        assertEquals(5.0 * 1.6, upgraded.fovKick, 1e-9, "full strength, not softened as a repeat hit")
    }

    @Test
    fun barsStayInUntilTheCameraStartsRecovering() {
        val shot = ActionShot(plan())
        assertTrue(shot.wantsBars)
        shot.impact(plain)
        assertTrue(shot.wantsBars)
        advance(shot, 0.55)
        assertFalse(shot.wantsBars)
    }

    @Test
    fun eachStageHasItsOwnFramingAndRecoveryHandsBackToTheCommandShot() {
        val shot = ActionShot(plan())
        val anticipate = assertNotNull(shot.pose(mon, foe, 0.0))
        advance(shot, 0.25)
        val approach = assertNotNull(shot.pose(mon, foe, 0.0))
        shot.impact(plain)
        val impact = assertNotNull(shot.pose(mon, foe, 0.0))
        assertNotEquals(anticipate.z, approach.z)
        assertNotEquals(approach.yaw, impact.yaw)
        advance(shot, 0.55)
        assertNull(shot.pose(mon, foe, 0.0))
    }

    @Test
    fun aFoeAttackShowsTheReverseShotBehindTheFoe() {
        val shot = ActionShot(plan(attackerIsMon = false))
        advance(shot, 0.25)

        val approach = assertNotNull(shot.pose(attacker = foe, target = mon, seconds = 0.0))
        assertTrue(approach.z > foe.z, "camera z ${approach.z}")
    }

    @Test
    fun aStrongImpactMovesTheCameraCloserAndSwingsItFurther() {
        val plainShot = ActionShot(plan())
        plainShot.impact(plain)
        val strongShot = ActionShot(plan())
        strongShot.impact(crit)
        val plainPose = assertNotNull(plainShot.pose(mon, foe, 0.0))
        val strongPose = assertNotNull(strongShot.pose(mon, foe, 0.0))
        val plainDistance = Point(plainPose.x - foe.x, 0.0, plainPose.z - foe.z).length()
        val strongDistance = Point(strongPose.x - foe.x, 0.0, strongPose.z - foe.z).length()
        assertTrue(strongDistance < plainDistance)
    }

    @Test
    fun shakeDecaysAndStaysSmall() {
        var amplitude = 1.0
        repeat(60) { amplitude = Shake.decay(amplitude, frame) }
        assertTrue(amplitude < 0.01, "amplitude $amplitude")
        for (t in 0..100) {
            assertTrue(kotlin.math.abs(Shake.yaw(t * 0.01, 1.0)) <= 1.5)
            assertTrue(kotlin.math.abs(Shake.pitch(t * 0.01, 1.0)) <= 1.5)
        }
        assertEquals(0.0, Shake.yaw(0.5, 0.0))
    }
}
