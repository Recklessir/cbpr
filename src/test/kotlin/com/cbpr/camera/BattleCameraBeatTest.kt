package com.cbpr.camera

import com.cbpr.battle.ImpactFlags
import com.cbpr.battle.MoveProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BattleCameraBeatTest {
    private val vanilla = Pose(10.0, 65.0, 10.0, 170.0, 15.0)
    private val mon = Point(0.0, 1.0, 0.0)
    private val foe = Point(0.0, 1.0, 8.0)
    private val frame = 1.0 / 60

    private fun plan(kind: BeatPlan.Kind, subjectIsMon: Boolean = true, darkened: Boolean = false) =
        BeatPlan(kind, subjectIsMon, reducedMotion = false, darkened = darkened)

    private fun run(camera: BattleCamera, seconds: Double, p: Point? = mon, f: Point? = foe): Pose? {
        var pose: Pose? = null
        repeat((seconds / frame).toInt()) { pose = camera.update(frame, vanilla, p, f) }
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
    fun aBeatIsIgnoredOutsideTheCommandPhase() {
        val off = BattleCamera()
        off.playBeat(plan(BeatPlan.Kind.FAINT))
        assertFalse(off.busy)

        val intro = BattleCamera()
        intro.begin(vanilla)
        intro.playBeat(plan(BeatPlan.Kind.SEND_OUT))
        assertFalse(intro.busy, "the intro must not be interrupted by a send-out")
    }

    @Test
    fun aFaintShotMovesTheCameraDimsTheWorldAndThenHandsBack() {
        val camera = commandCamera()
        val rest = assertNotNull(run(camera, 0.5))
        camera.playBeat(plan(BeatPlan.Kind.FAINT))
        assertTrue(camera.busy)
        assertTrue(camera.wantsBars)
        assertEquals(1.0, camera.tint)
        val during = assertNotNull(run(camera, 1.0))
        assertTrue(kotlin.math.abs(during.z - rest.z) > 0.3 || kotlin.math.abs(during.x - rest.x) > 0.3)
        run(camera, 3.0)
        assertFalse(camera.busy)
        assertEquals(0.0, camera.tint)
        assertFalse(camera.wantsBars)
        val after = assertNotNull(run(camera, 1.0))
        assertEquals(rest.x, after.x, 0.3)
        assertEquals(rest.z, after.z, 0.3)
    }

    @Test
    fun aLossIsDarkenedAndAWinIsNot() {
        val loss = commandCamera()
        loss.playBeat(plan(BeatPlan.Kind.VICTORY, subjectIsMon = false, darkened = true))
        assertEquals(1.0, loss.tint)
        val win = commandCamera()
        win.playBeat(plan(BeatPlan.Kind.VICTORY))
        assertEquals(0.0, win.tint)
    }

    @Test
    fun theBattleEndingMidWinDefersTheReturnUntilTheOrbitIsDone() {
        val camera = commandCamera()
        camera.playBeat(plan(BeatPlan.Kind.VICTORY))
        run(camera, 0.3)
        camera.end()
        assertEquals(BattleCamera.Phase.COMMAND, camera.phase, "the orbit must keep playing after the battle has ended")
        assertTrue(camera.busy)
        run(camera, 0.5)
        assertEquals(BattleCamera.Phase.COMMAND, camera.phase)
        run(camera, 0.5)
        assertEquals(BattleCamera.Phase.RETURNING, camera.phase, "once the orbit is over the view returns")
        var released = false
        repeat((3.0 / frame).toInt()) {
            if (!released && camera.update(frame, vanilla, mon, foe) == null) released = true
        }
        assertTrue(released)
        assertEquals(BattleCamera.Phase.OFF, camera.phase)
    }

    @Test
    fun endingWithoutAWinReturnsAtOnceAndDropsAnyBeat() {
        val camera = commandCamera()
        camera.playBeat(plan(BeatPlan.Kind.FAINT))
        camera.end()
        assertEquals(BattleCamera.Phase.RETURNING, camera.phase)
        assertFalse(camera.busy)
        assertEquals(0.0, camera.tint)
    }

    @Test
    fun endingWithNothingPlayingReturnsAtOnce() {
        val camera = commandCamera()
        camera.end()
        assertEquals(BattleCamera.Phase.RETURNING, camera.phase)
    }

    @Test
    fun anAttackShotCannotStartWhileABeatIsPlaying() {
        val camera = commandCamera()
        camera.playBeat(plan(BeatPlan.Kind.FAINT))
        val profile = MoveProfile.GENERIC
        camera.play(ShotPlan(profile, attackerIsMon = true, reducedMotion = false, hitStopEnabled = true, critSpecials = true))
        camera.impact(ImpactFlags(crit = true, superEffective = false))
        val pose = assertNotNull(run(camera, 0.3))
        assertEquals(0.0, pose.fov, 1e-9)
        assertEquals(0.0, pose.roll, 1e-9)
    }

    @Test
    fun aBeatReplacesAnAttackShotAlreadyPlaying() {
        val camera = commandCamera()
        camera.play(ShotPlan(MoveProfile.GENERIC, attackerIsMon = true, reducedMotion = false, hitStopEnabled = true, critSpecials = true))
        run(camera, 0.3)
        camera.playBeat(plan(BeatPlan.Kind.FAINT, subjectIsMon = false))
        assertTrue(camera.busy)
        camera.impact(ImpactFlags(crit = true, superEffective = false))
        val pose = assertNotNull(run(camera, 0.2))
        assertEquals(0.0, pose.roll, 1e-9, "the replaced attack must not tilt the faint shot")
    }

    @Test
    fun aSubjectThatTakesAWhileToAppearDoesNotEndTheShot() {
        val camera = commandCamera()
        camera.playBeat(plan(BeatPlan.Kind.SEND_OUT))
        run(camera, 3.0, mon, null)
        assertEquals(BattleCamera.Phase.COMMAND, camera.phase, "a send-out can take a few seconds to appear in the world")
        run(camera, 1.0, mon, foe)
        assertEquals(BattleCamera.Phase.COMMAND, camera.phase)
    }

    @Test
    fun aSubjectThatNeverComesBackStillReturnsToVanilla() {
        val camera = commandCamera()
        camera.playBeat(plan(BeatPlan.Kind.VICTORY))
        camera.end()
        var released = false
        repeat(((BattleCamera.LOST_SUBJECT_TIMEOUT + BattleCamera.RETURN_TIMEOUT + 1.0) / frame).toInt()) {
            if (!released && camera.update(frame, vanilla, null, foe) == null) released = true
        }
        assertTrue(released, "a win with nobody left to film must not hold the camera forever")
        assertFalse(camera.busy)
    }

    @Test
    fun abortClearsABeatAndAPendingEnd() {
        val camera = commandCamera()
        camera.playBeat(plan(BeatPlan.Kind.VICTORY))
        camera.end()
        camera.abort()
        assertFalse(camera.busy)
        assertEquals(0.0, camera.tint)
        camera.begin(vanilla)
        run(camera, 5.0)
        assertEquals(BattleCamera.Phase.COMMAND, camera.phase, "a stale end request must not leak into the next battle")
    }

    @Test
    fun theSendOutSwoopEndsBackOnTheCommandShot() {
        val camera = commandCamera()
        val rest = assertNotNull(run(camera, 0.5))
        camera.playBeat(plan(BeatPlan.Kind.SEND_OUT))
        val swoop = assertNotNull(run(camera, 0.3))
        assertNotEquals(rest.y, swoop.y, "the swoop starts low")
        run(camera, 4.0)
        val after = assertNotNull(run(camera, 1.0))
        assertEquals(rest.y, after.y, 0.3)
    }
}

class BattleCameraAvoidanceTest {
    private val vanilla = Pose(10.0, 65.0, 10.0, 170.0, 15.0)
    private val frame = 1.0 / 60

    @Test
    fun aCameraThatStaysBlockedReleasesToVanillaAndReports() {
        val camera = BattleCamera()
        camera.useSight { _, _ -> false }
        camera.begin(vanilla)
        var released = false
        repeat((4.0 / frame).toInt()) {
            if (camera.update(frame, vanilla, Point(0.0, 1.0, 0.0), Point(0.0, 1.0, 8.0)) == null) released = true
        }
        assertTrue(camera.released)
        assertTrue(released, "the view must return to vanilla")
        assertEquals(BattleCamera.Phase.OFF, camera.phase)
    }

    @Test
    fun withoutASightTheCameraIsNeverReleased() {
        val camera = BattleCamera()
        camera.begin(vanilla)
        repeat((4.0 / frame).toInt()) { camera.update(frame, vanilla, Point(0.0, 1.0, 0.0), Point(0.0, 1.0, 8.0)) }
        assertFalse(camera.released)
        assertEquals(BattleCamera.Phase.COMMAND, camera.phase)
    }
}

class BattleCameraMenuTest {
    private val vanilla = Pose(10.0, 65.0, 10.0, 170.0, 15.0)
    private val mon = Point(0.0, 1.0, 0.0)
    private val foe = Point(0.0, 1.0, 8.0)
    private val frame = 1.0 / 60

    private fun settled(camera: BattleCamera, seconds: Double): Pose {
        var pose: Pose? = null
        repeat((seconds / frame).toInt()) { pose = camera.update(frame, vanilla, mon, foe) }
        return pose!!
    }

    @Test
    fun aSubMenuPushesInAndClosingItEasesBackOut() {
        val camera = BattleCamera()
        camera.begin(vanilla)
        val usual = settled(camera, 5.0)
        camera.menuView = true
        val close = settled(camera, 2.0)
        assertTrue(kotlin.math.abs(close.z - mon.z) < kotlin.math.abs(usual.z - mon.z) - 0.4, "closer: ${close.z} vs ${usual.z}")
        camera.menuView = false
        val back = settled(camera, 2.0)
        assertEquals(usual.z, back.z, 0.1)
    }

    @Test
    fun theMenuShotKeepsBothPokemonInFront() {
        val pose = Shots.menuView(mon, foe, 0.0)
        assertTrue(pose.z < mon.z && pose.z > Shots.command(mon, foe, 0.0).z)

        assertTrue(kotlin.math.abs(pose.x - mon.x) > 1.4, "side offset ${pose.x}")
        val towardFoe = Shots.lookAt(Point(pose.x, pose.y, pose.z), foe).yaw
        assertTrue(kotlin.math.abs(towardFoe - pose.yaw) in 3.0..40.0, "aim differs by ${towardFoe - pose.yaw}")
    }
}
