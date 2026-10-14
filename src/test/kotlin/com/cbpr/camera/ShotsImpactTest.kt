package com.cbpr.camera

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShotsImpactTest {
    private val attacker = Point(0.0, 1.0, 0.0)
    private val target = Point(0.0, 1.0, 8.0)

    @Test
    fun theImpactCameraSitsOnTheAttackersSideOfTheTarget() {
        val pose = Shots.impact(attacker, target, 1.0, 1.0, 0.0)
        assertTrue(pose.z < target.z, "camera z ${pose.z}")
        assertTrue(pose.y > target.y)
    }

    @Test
    fun swingMovesTheCameraSidewaysAroundTheTarget() {
        val straight = Shots.impact(attacker, target, 1.0, 1.0, 0.0)
        val swung = Shots.impact(attacker, target, 1.0, 1.0, 45.0)
        assertEquals(0.0, straight.x, 1e-9)
        assertTrue(abs(swung.x) > 1.0, "x ${swung.x}")
        val straightDistance = Point(straight.x - target.x, 0.0, straight.z - target.z).length()
        val swungDistance = Point(swung.x - target.x, 0.0, swung.z - target.z).length()
        assertEquals(straightDistance, swungDistance, 1e-9)
    }

    @Test
    fun oppositeSwingsMirrorEachOther() {
        val left = Shots.impact(attacker, target, 1.0, 1.0, 30.0)
        val right = Shots.impact(attacker, target, 1.0, 1.0, -30.0)
        assertEquals(left.x, -right.x, 1e-9)
        assertEquals(left.z, right.z, 1e-9)
    }

    @Test
    fun aSmallerDistanceScaleBringsTheCameraCloser() {
        val far = Shots.impact(attacker, target, 1.0, 1.0, 0.0)
        val near = Shots.impact(attacker, target, 0.5, 1.0, 0.0)
        assertTrue(abs(near.z - target.z) < abs(far.z - target.z))
    }

    @Test
    fun theViewLooksAtTheTargetBiasedTowardTheAttacker() {
        val pose = Shots.impact(attacker, target, 1.0, 1.0, 0.0)
        val lookAtTarget = Shots.lookAt(Point(pose.x, pose.y, pose.z), target)
        assertEquals(lookAtTarget.yaw, pose.yaw, 1.0)
    }

    @Test
    fun coincidentPokemonDoNotProduceNaN() {
        val pose = Shots.impact(attacker, attacker, 1.0, 1.0, 20.0)
        assertTrue(pose.x.isFinite() && pose.y.isFinite() && pose.z.isFinite() && pose.yaw.isFinite() && pose.pitch.isFinite())
    }

    @Test
    fun theCommandShotStillMatchesItsOldDefaults() {
        val mon = Point(0.0, 1.0, 0.0)
        val foe = Point(0.0, 1.0, 8.0)
        val plain = Shots.command(mon, foe, 0.0)
        val scaled = Shots.command(mon, foe, 0.0, 1.0, 1.0)
        assertEquals(plain.z, scaled.z, 1e-12)
        assertTrue(Shots.command(mon, foe, 0.0, 0.5, 1.0).z > plain.z, "pushed in, so nearer the Pokemon")
        assertTrue(Shots.command(mon, foe, 0.0, 1.0, 2.0).y > plain.y)
    }

    @Test
    fun poseDefaultsToNoFovOrRoll() {
        val pose = Pose(1.0, 2.0, 3.0, 4.0, 5.0)
        assertEquals(0.0, pose.fov)
        assertEquals(0.0, pose.roll)
    }
}
