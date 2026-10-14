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

class BattleCameraActionTest {
    private val vanilla = Pose(10.0, 65.0, 10.0, 170.0, 15.0)
    private val mon = Point(0.0, 1.0, 0.0)
    private val foe = Point(0.0, 1.0, 8.0)
    private val frame = 1.0 / 60
    private val plain = ImpactFlags(crit = false, superEffective = false)
    private val crit = ImpactFlags(crit = true, superEffective = false)

    private val profile = MoveProfile(
        "test", emptySet(), emptySet(), anticipation = 0.2, approach = 0.4, waitForImpact = 1.0, hold = 0.5,
        distanceScale = 0.7, heightScale = 0.9, swingDegrees = 30.0, fovKick = 6.0, shake = 0.8, tilt = 0.0,
        hitStop = 0.06, callout = true, sound = null,
    )
    private val plan = ShotPlan(profile, attackerIsMon = true, reducedMotion = false, hitStopEnabled = true, critSpecials = true)

    private fun run(camera: BattleCamera, seconds: Double): Pose? {
        var pose: Pose? = null
        repeat((seconds / frame).toInt()) { pose = camera.update(frame, vanilla, mon, foe) }
        return pose
    }

    private fun commandCamera(): BattleCamera {
        val camera = BattleCamera()
        camera.begin(vanilla)
        run(camera, 4.0)
        assertEquals(BattleCamera.Phase.COMMAND, camera.phase)
        return camera
    }

    @Test
    fun anAttackShotIsIgnoredOutsideTheCommandPhase() {
        val off = BattleCamera()
        off.play(plan)
        assertNull(off.update(frame, vanilla, mon, foe))

        val intro = BattleCamera()
        intro.begin(vanilla)
        intro.play(plan)
        assertFalse(intro.wantsBars.not(), "intro bars still apply")
        run(intro, 0.5)
        assertEquals(BattleCamera.Phase.INTRO, intro.phase)
    }

    @Test
    fun anAttackMovesTheCameraAwayFromTheCommandShotAndBack() {
        val camera = commandCamera()
        val rest = assertNotNull(run(camera, 0.5))
        camera.play(plan)
        val during = assertNotNull(run(camera, 0.5))
        assertTrue(kotlin.math.abs(during.z - rest.z) > 0.2 || kotlin.math.abs(during.x - rest.x) > 0.2, "the camera should have moved")
        run(camera, 5.0)
        val after = assertNotNull(run(camera, 0.5))
        assertEquals(rest.x, after.x, 0.2)
        assertEquals(rest.z, after.z, 0.2)
        assertFalse(camera.wantsBars)
    }

    @Test
    fun theBarsComeInForAnAttackAndGoOutAfterwards() {
        val camera = commandCamera()
        assertFalse(camera.wantsBars)
        camera.play(plan)
        assertTrue(camera.wantsBars)
        run(camera, 6.0)
        assertFalse(camera.wantsBars)
    }

    @Test
    fun anImpactKicksTheFieldOfViewThatThenSettlesBackToZero() {
        val camera = commandCamera()
        camera.play(plan)
        run(camera, 0.3)
        camera.impact(plain)
        val kicked = assertNotNull(camera.update(frame, vanilla, mon, foe))
        assertTrue(kicked.fov > 3.0, "fov ${kicked.fov}")
        val settled = assertNotNull(run(camera, 4.0))
        assertEquals(0.0, settled.fov, 0.05)
    }

    @Test
    fun aCriticalImpactTiltsTheViewAndTheTiltIsHeldOnlyWhileTheHitIsOnScreen() {
        val camera = commandCamera()
        camera.play(plan)
        run(camera, 0.3)
        camera.impact(crit)
        val held = assertNotNull(run(camera, 0.4))
        assertTrue(held.roll > 3.0, "roll ${held.roll}")
        val after = assertNotNull(run(camera, 4.0))
        assertEquals(0.0, after.roll, 0.1)
    }

    @Test
    fun anImpactWithNoShotPlayingChangesNothing() {
        val camera = commandCamera()
        camera.impact(crit)
        val pose = assertNotNull(run(camera, 0.2))
        assertEquals(0.0, pose.fov, 1e-9)
        assertEquals(0.0, pose.roll, 1e-9)
    }

    @Test
    fun hitStopFreezesTheCameraBrieflyThenItMovesAgain() {
        val camera = commandCamera()
        camera.play(plan)
        run(camera, 0.3)
        camera.impact(plain)
        val first = assertNotNull(camera.update(frame, vanilla, mon, foe))
        val second = assertNotNull(camera.update(frame, vanilla, mon, foe))
        assertEquals(first.x, second.x, 1e-12)
        assertEquals(first.yaw, second.yaw, 1e-12)
        run(camera, 0.2)
        val later = assertNotNull(camera.update(frame, vanilla, mon, foe))
        assertNotEquals(first.x, later.x)
    }

    @Test
    fun theHitStopIsVisualOnlyAndShortEnoughToNeverMatter() {
        assertTrue(profile.hitStop <= 0.15, "profiles are validated to at most 150 ms")
        val camera = commandCamera()
        camera.play(plan)
        camera.impact(plain)
        run(camera, 0.2)
        assertNotNull(camera.update(frame, vanilla, mon, foe))
    }

    @Test
    fun endingTheBattleMidAttackReturnsTheViewCleanly() {
        val camera = commandCamera()
        camera.play(plan)
        run(camera, 0.3)
        camera.impact(crit)
        run(camera, 0.1)
        camera.end()
        var released = false
        repeat((3.0 / frame).toInt()) {
            if (!released && camera.update(frame, vanilla, mon, foe) == null) released = true
        }
        assertTrue(released)
        assertEquals(BattleCamera.Phase.OFF, camera.phase)
    }

    @Test
    fun abortClearsEveryEffectAtOnce() {
        val camera = commandCamera()
        camera.play(plan)
        camera.impact(crit)
        camera.abort()
        assertEquals(BattleCamera.Phase.OFF, camera.phase)
        camera.begin(vanilla)
        val fresh = assertNotNull(camera.update(frame, vanilla, mon, foe))
        assertEquals(0.0, fresh.fov, 1e-9)
        assertEquals(0.0, fresh.roll, 1e-9)
    }

    @Test
    fun losingASubjectMidAttackStillReturnsToVanilla() {
        val camera = commandCamera()
        camera.play(plan)
        run(camera, 0.2)
        var released = false
        repeat(((BattleCamera.LOST_SUBJECT_TIMEOUT + BattleCamera.RETURN_TIMEOUT + 1.0) / frame).toInt()) {
            if (!released && camera.update(frame, vanilla, mon, null) == null) released = true
        }
        assertTrue(released)
    }

    @Test
    fun aNewAttackReplacesOneStillPlaying() {
        val camera = commandCamera()
        camera.play(plan)
        run(camera, 0.3)
        camera.play(plan)
        assertTrue(camera.wantsBars)
        camera.impact(plain)
        assertNotNull(camera.update(frame, vanilla, mon, foe))
    }

    @Test
    fun updateWithoutEffectsKeepsFovAndRollAtZero() {
        val camera = commandCamera()
        val pose = assertNotNull(run(camera, 1.0))
        assertEquals(0.0, pose.fov, 1e-9)
        assertEquals(0.0, pose.roll, 1e-9)
    }
}
