package com.cbpr.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BattleCameraBarsTest {
    private val vanilla = Pose(10.0, 65.0, 10.0, 170.0, 15.0)
    private val mon = Point(0.0, 1.0, 0.0)
    private val foe = Point(0.0, 1.0, 8.0)
    private val frame = 1.0 / 60

    private fun run(camera: BattleCamera, seconds: Double) {
        repeat((seconds / frame).toInt()) { camera.update(frame, vanilla, mon, foe) }
    }

    private fun commandCamera(): BattleCamera {
        val camera = BattleCamera()
        camera.begin(vanilla)
        run(camera, 4.0)
        assertEquals(BattleCamera.Phase.COMMAND, camera.phase)
        return camera
    }

    private fun attack() = ShotPlan(
        com.cbpr.battle.MoveProfile.parse(com.google.gson.JsonParser.parseString("""{"id":"generic"}""").asJsonObject) {}!!,
        attackerIsMon = true, reducedMotion = false, hitStopEnabled = true, critSpecials = true,
    )

    @Test
    fun theBarsStayInBetweenAGimmickCloseUpAndTheAttackThatFollowsItThenGoOutOnce() {
        val camera = commandCamera()
        camera.playBeat(BeatPlan(BeatPlan.Kind.GIMMICK, true, false, style = GimmickStyle.MEGA))
        var seconds = 0.0

        while (seconds < GimmickStyle.MEGA.seconds + 0.4) {
            camera.update(frame, vanilla, mon, foe)
            seconds += frame
            assertTrue(camera.wantsBars, "bars out too early at $seconds")
        }
        camera.play(attack())
        var dipped = false
        repeat((6.0 / frame).toInt()) {
            camera.update(frame, vanilla, mon, foe)
            if (!camera.wantsBars) dipped = true
        }
        assertTrue(dipped, "and they do go out once the attack is over")
    }

    @Test
    fun theBarsWaitForAnAttackThatComesSecondsAfterTheCloseUp() {
        val camera = commandCamera()
        camera.playBeat(BeatPlan(BeatPlan.Kind.GIMMICK, true, false, style = GimmickStyle.MEGA))

        run(camera, GimmickStyle.MEGA.seconds + 5.0)
        assertTrue(camera.wantsBars, "still waiting for the move")
        camera.play(attack())
        assertTrue(camera.wantsBars)
    }

    @Test
    fun withNoAttackTheHeldBarsGoOutAfterTheHold() {
        val camera = commandCamera()
        camera.playBeat(BeatPlan(BeatPlan.Kind.GIMMICK, true, false, style = GimmickStyle.MEGA))
        run(camera, GimmickStyle.MEGA.seconds + GimmickStyle.MEGA.barHoldSeconds + 0.3)
        assertFalse(camera.wantsBars)
    }

    @Test
    fun eachGimmickWaitsItsOwnTime() {
        assertEquals(12.0, GimmickStyle.MEGA.barHoldSeconds)
        assertEquals(8.0, GimmickStyle.TERA.barHoldSeconds)
        assertEquals(16.0, BeatShot.DYNAMAX_BAR_HOLD_SECONDS)
    }

    @Test
    fun teraBarsGoOutAfterEightSecondsAndDynamaxBarsStayInPastTwelve() {
        val tera = commandCamera()
        tera.playBeat(BeatPlan(BeatPlan.Kind.GIMMICK, true, false, style = GimmickStyle.TERA))
        run(tera, GimmickStyle.TERA.seconds + 8.0 + 0.3)
        assertFalse(tera.wantsBars)

        val dynamax = commandCamera()
        dynamax.playBeat(BeatPlan(BeatPlan.Kind.DYNAMAX, true, false))
        run(dynamax, 7.0 + 12.0)
        assertTrue(dynamax.wantsBars, "still waiting 12 s after the giant is revealed")
        run(dynamax, 4.5)
        assertFalse(dynamax.wantsBars)
    }

    @Test
    fun aFaintShotDoesNotHoldTheBars() {
        val camera = commandCamera()
        camera.playBeat(BeatPlan(BeatPlan.Kind.FAINT, true, false))
        run(camera, 1.8 + 0.05)
        assertFalse(camera.wantsBars)
    }

    @Test
    fun startingAnAttackWhileAMoveIsHoveredDoesNotJumpTheCamera() {
        val camera = commandCamera()
        camera.menuView = true
        camera.hoverView = true
        var before: Pose? = null
        repeat(120) { before = camera.update(frame, vanilla, mon, foe) }

        camera.play(attack())
        camera.menuView = false
        camera.hoverView = false
        val after = camera.update(frame, vanilla, mon, foe)!!
        val jump = (Point(after.x, after.y, after.z) - Point(before!!.x, before!!.y, before!!.z)).length()
        assertTrue(jump < 0.15, "the camera moved $jump blocks in one frame")
    }
}
