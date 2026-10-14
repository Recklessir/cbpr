package com.cbpr.camera

import com.cbpr.battle.BattleEvent
import com.cbpr.battle.MessageSubject
import com.cbpr.battle.MoveProfile
import com.cbpr.battle.ProfileSet
import com.cbpr.battle.ShotDirector
import com.cbpr.config.CbprConfig
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SwitchShotTest {

    private val pokemon = Point(0.0, 1.0, 0.0)
    private val foe = Point(0.0, 1.0, 8.0)
    private val trainer = Point(0.0, 0.0, -2.0)

    private fun horizontalAngleToFoe(spot: Point): Double {
        val toSpot = Point(spot.x - pokemon.x, 0.0, spot.z - pokemon.z)
        val toFoe = Point(foe.x - pokemon.x, 0.0, foe.z - pokemon.z)
        return Math.toDegrees(acos((toSpot dot toFoe) / (toSpot.length() * toFoe.length())))
    }

    @Test
    fun theCameraIsInFrontOfThePokemonTurnedByTheNamedAngleAtTheNamedDistanceAndHeight() {
        for (right in listOf(true, false)) {
            val spot = Shots.switchSpot(pokemon, foe, right, Shots.switchDistance(pokemon, foe))
            assertEquals(Shots.SWITCH_FRONT_ANGLE_DEGREES, horizontalAngleToFoe(spot), 1e-6)
            assertTrue(Shots.switchDistance(pokemon, foe) >= Shots.SWITCH_DISTANCE)
            assertEquals(Shots.switchDistance(pokemon, foe), hypot(spot.x - pokemon.x, spot.z - pokemon.z), 1e-9)
            assertEquals(pokemon.y + Shots.SWITCH_HEIGHT, spot.y, 1e-9)
            assertTrue(spot.z > pokemon.z, "on the foe's side of the Pokemon, opposite the command shot behind it")
        }
    }

    @Test
    fun rightAndLeftAreMirrorImagesAndRightIsTheTrainersRightHandFacingTheFoe() {
        val right = Shots.switchSpot(pokemon, foe, true, 4.0)
        val left = Shots.switchSpot(pokemon, foe, false, 4.0)
        assertEquals(-right.x, left.x, 1e-9)
        assertEquals(right.z, left.z, 1e-9)

        assertTrue(right.x < 0.0 && left.x > 0.0)
    }

    @Test
    fun theViewLooksBackAtThePokemonWithTheFoeAndTheTrainerInTheSameShot() {
        val pose = Shots.switchFront(pokemon, foe, true, 1.0)
        val eye = Point(pose.x, pose.y, pose.z)
        val aim = Shots.lookAt(eye, Point(0.0, 1.0, 8.0 * Shots.SWITCH_LOOK_TOWARD_OTHER))
        assertEquals(aim.yaw, pose.yaw, 1e-6)

        fun off(point: Point): Double {
            val want = Shots.lookAt(eye, point)
            var dyaw = abs(want.yaw - pose.yaw) % 360.0
            if (dyaw > 180.0) dyaw = 360.0 - dyaw
            return dyaw
        }
        assertTrue(off(pokemon) < 35.0, "Pokemon at ${off(pokemon)}")
        assertTrue(off(foe) < 50.0, "foe at ${off(foe)}")
    }

    @Test
    fun theShotStartsFurtherOutAndEasesInWithoutJumps() {
        var last = Shots.switchFront(pokemon, foe, true, 0.0)
        val start = (Point(last.x, last.y, last.z) - pokemon).length()
        for (i in 1..100) {
            val pose = Shots.switchFront(pokemon, foe, true, i / 100.0)
            assertTrue((Point(pose.x, pose.y, pose.z) - Point(last.x, last.y, last.z)).length() < 0.2, "jump at $i")
            last = pose
        }
        val end = (Point(last.x, last.y, last.z) - pokemon).length()
        assertTrue(start > end)
    }

    @Test
    fun theSideIsTheDefaultUnlessTheTrainerStandsInTheWay() {
        assertTrue(Shots.switchSide(true, pokemon, foe, trainer), "trainer behind the Pokemon: no conflict")
        assertFalse(Shots.switchSide(false, pokemon, foe, trainer))

        val rightSpot = Shots.switchSpot(pokemon, foe, true, Shots.switchDistance(pokemon, foe))
        val leftSpot = Shots.switchSpot(pokemon, foe, false, Shots.switchDistance(pokemon, foe))
        assertFalse(Shots.switchSide(true, pokemon, foe, Point(rightSpot.x, 0.0, rightSpot.z)))
        assertTrue(Shots.switchSide(false, pokemon, foe, Point(leftSpot.x, 0.0, leftSpot.z)))
        assertTrue(Shots.switchSide(true, pokemon, foe, null), "no trainer position: the default")
    }

    @Test
    fun theSwitchBeatIsAsLongAsTheSendOutAndTheSideIsDecidedOnce() {
        val plan = BeatPlan(BeatPlan.Kind.SWITCH, true, false, trainer = trainer, preferRight = true)
        val shot = BeatShot(plan)
        val first = shot.pose(pokemon, foe)!!

        val moved = BeatShot(BeatPlan(BeatPlan.Kind.SWITCH, true, false, trainer = Point(-3.0, 0.0, 1.5), preferRight = true))
        val a = moved.pose(pokemon, foe)!!
        assertNotEquals(first.x, a.x, "a trainer on the right side flips the shot")
        shot.step(0.5)
        assertEquals(first.x < 0.0, shot.pose(pokemon, foe)!!.x < 0.0)
        shot.step(1.2)
        assertTrue(shot.finished)
    }

    private val vanilla = Pose(10.0, 65.0, 10.0, 170.0, 15.0)
    private val frame = 1.0 / 60

    private fun director(camera: BattleCamera, config: CbprConfig) = ShotDirector(
        camera = camera,
        profiles = { ProfileSet(listOf(MoveProfile.GENERIC)) },
        settings = { config },
        lookupMove = { null },
        mons = { MessageSubject.Roster("", emptyList()) },
        foes = { MessageSubject.Roster("", emptyList()) },
        trainerPosition = { trainer },
    )

    private fun commandCamera(): BattleCamera {
        val camera = BattleCamera()
        camera.begin(vanilla)
        repeat(240) { camera.update(frame, vanilla, pokemon, foe) }
        return camera
    }

    @Test
    fun yourOwnSwitchGetsTheMirroredFrontAndTheFoesKeepsTheSwoop() {
        val camera = commandCamera()
        director(camera, CbprConfig(sendOutSwoop = true)).handle(listOf(BattleEvent.SentOut(onMonSide = true)), 0.0)
        assertEquals(BeatPlan.Kind.SWITCH, camera.beatKind)
        val other = commandCamera()
        director(other, CbprConfig(sendOutSwoop = true)).handle(listOf(BattleEvent.SentOut(onMonSide = false)), 0.0)
        assertEquals(BeatPlan.Kind.SEND_OUT, other.beatKind)
    }

    @Test
    fun theOpeningSendOutDuringTheIntroIsNotASwitch() {
        val camera = BattleCamera()
        camera.begin(vanilla)
        director(camera, CbprConfig(sendOutSwoop = true)).handle(listOf(BattleEvent.SentOut(onMonSide = true)), 0.0)
        assertEquals(null, camera.beatKind)
    }

    @Test
    fun theSendOutSwitchStillTurnsEveryShotOffWhenOff() {
        val camera = commandCamera()
        director(camera, CbprConfig(sendOutSwoop = false)).handle(listOf(BattleEvent.SentOut(onMonSide = true)), 0.0)
        assertEquals(null, camera.beatKind)
    }

    @Test
    fun hoveringAMoveDrawsTheMenuViewInTowardTheOwnerAndAimsFurtherAtTheFoe() {
        val plain = Shots.menuView(pokemon, foe, 0.0, 1.0, hover = 0.0)
        val hovered = Shots.menuView(pokemon, foe, 0.0, 1.0, hover = 1.0)
        val before = (Point(plain.x, plain.y, plain.z) - pokemon).length()
        val after = (Point(hovered.x, hovered.y, hovered.z) - pokemon).length()
        assertTrue(after < before * 0.85, "camera $before blocks away, then $after")
        assertEquals(plain, Shots.menuView(pokemon, foe, 0.0, 1.0), "no hover is the usual view")
        val half = Shots.menuView(pokemon, foe, 0.0, 1.0, hover = 0.5)
        val mid = (Point(half.x, half.y, half.z) - pokemon).length()
        assertTrue(mid < before && mid > after, "in between at half")
    }

    @Test
    fun theHoveredViewIsInWithinAboutATenthOfASecondAndBackOutAsFastWhenTheCursorLeaves() {
        val camera = commandCamera()
        camera.menuView = true
        fun distance(pose: Pose) = (Point(pose.x, pose.y, pose.z) - pokemon).length()
        var pose = camera.update(frame, vanilla, pokemon, foe)!!
        repeat(240) { pose = camera.update(frame, vanilla, pokemon, foe)!! }
        val resting = distance(pose)
        camera.hoverView = true
        repeat(9) { pose = camera.update(frame, vanilla, pokemon, foe)!! }
        val soon = distance(pose)
        repeat(120) { pose = camera.update(frame, vanilla, pokemon, foe)!! }
        val hovered = distance(pose)
        assertTrue(hovered < resting * 0.9, "hovered $hovered, resting $resting")
        assertTrue(resting - soon > (resting - hovered) * 0.9, "after 0.15 s it is already $soon of the way from $resting to $hovered")
        camera.hoverView = false
        repeat((0.15 / frame).toInt() + 1) { pose = camera.update(frame, vanilla, pokemon, foe)!! }
        assertEquals(resting, distance(pose), (resting - hovered) * 0.1, "and straight back out when the cursor leaves")
    }

    @Test
    fun theHoverLeavesWithTheMenuViewAndNeverOutlivesAMoveBeingPlayed() {
        val hovered = commandCamera()
        val plain = commandCamera()
        for (camera in listOf(hovered, plain)) camera.menuView = true
        hovered.hoverView = true
        repeat(60) {
            hovered.update(frame, vanilla, pokemon, foe)
            plain.update(frame, vanilla, pokemon, foe)
        }

        for (camera in listOf(hovered, plain)) {
            camera.menuView = false
            camera.hoverView = false
        }

        repeat(600) {
            hovered.update(frame, vanilla, pokemon, foe)
            plain.update(frame, vanilla, pokemon, foe)
        }
        val a = hovered.update(frame, vanilla, pokemon, foe)!!
        val b = plain.update(frame, vanilla, pokemon, foe)!!
        assertEquals(b.yaw, a.yaw, 0.05)
        assertEquals(b.x, a.x, 0.05)
    }

    @Test
    fun aHoverRightAfterTheMenuViewOpensIsIgnoredUntilTheViewHasArrived() {
        val hovering = commandCamera()
        val plain = commandCamera()
        for (camera in listOf(hovering, plain)) camera.menuView = true
        hovering.hoverView = true

        repeat(((BattleCamera.HOVER_ARM_SECONDS - 0.05) / frame).toInt()) {
            val a = hovering.update(frame, vanilla, pokemon, foe)!!
            val b = plain.update(frame, vanilla, pokemon, foe)!!
            assertEquals(b.x, a.x, 1e-9)
            assertEquals(b.z, a.z, 1e-9)
        }
        repeat(60) {
            hovering.update(frame, vanilla, pokemon, foe)
            plain.update(frame, vanilla, pokemon, foe)
        }
        val a = hovering.update(frame, vanilla, pokemon, foe)!!
        val b = plain.update(frame, vanilla, pokemon, foe)!!
        assertTrue((Point(a.x, a.y, a.z) - pokemon).length() < (Point(b.x, b.y, b.z) - pokemon).length() * 0.95, "the zoom joins afterwards")
    }
}
