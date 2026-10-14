package com.cbpr.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShotsTest {
    private val mon = Point(0.0, 1.0, 0.0)
    private val foe = Point(0.0, 1.0, 8.0)

    @Test
    fun lookAtSouthIsYawZero() {
        val pose = Shots.lookAt(Point(0.0, 0.0, 0.0), Point(0.0, 0.0, 5.0))
        assertEquals(0.0, pose.yaw, 1e-9)
        assertEquals(0.0, pose.pitch, 1e-9)
    }

    @Test
    fun lookAtWestIsYaw90() {
        val pose = Shots.lookAt(Point(0.0, 0.0, 0.0), Point(-5.0, 0.0, 0.0))
        assertEquals(90.0, pose.yaw, 1e-9)
    }

    @Test
    fun lookingDownGivesPositivePitch() {
        val pose = Shots.lookAt(Point(0.0, 5.0, 0.0), Point(0.0, 0.0, 5.0))
        assertEquals(45.0, pose.pitch, 1e-9)
    }

    @Test
    fun commandShotSitsBehindMonAndFacesFoe() {
        val pose = Shots.command(mon, foe, 0.0)
        assertTrue(pose.z < mon.z, "camera should be behind the Mon on the far side from the foe")
        assertTrue(pose.y > mon.y)
        assertTrue(kotlin.math.abs(pose.yaw) < 15.0, "yaw ${pose.yaw}")
    }

    @Test
    fun swayStaysSmall() {
        val base = Shots.command(mon, foe, 0.0)
        for (t in 0..200) {
            val pose = Shots.command(mon, foe, t * 0.1)
            assertTrue(kotlin.math.abs(pose.yaw - base.yaw) < 1.5)
            assertTrue(kotlin.math.abs(pose.pitch - base.pitch) < 1.5)
        }
    }

    @Test
    fun wideShotSeesBothSides() {
        val pose = Shots.wide(mon, foe)
        assertTrue(kotlin.math.abs(pose.x) > 4.0, "wide shot should be off to the side")
        assertTrue(pose.y > mon.y)
    }

    @Test
    fun coincidentPokemonDoNotProduceNaN() {
        val pose = Shots.command(mon, mon, 0.0)
        assertTrue(pose.x.isFinite() && pose.y.isFinite() && pose.z.isFinite())
        assertTrue(Shots.wide(mon, mon).yaw.isFinite())
    }
}

class ShotsGimmickTest {
    private val subject = Point(0.0, 1.0, 0.0)
    private val other = Point(0.0, 1.0, 8.0)

    @Test
    fun everyStyleKeepsLookingAtThePokemonItIsAbout() {
        for (style in GimmickStyle.entries) {
            for (i in 0..10) {
                val pose = Shots.gimmick(subject, other, i / 10.0, style)
                val aim = Shots.lookAt(Point(pose.x, pose.y, pose.z), subject)
                var diff = kotlin.math.abs(aim.yaw - pose.yaw) % 360.0
                if (diff > 180.0) diff = 360.0 - diff
                assertTrue(diff < 20.0, "$style at $i: yaw off by $diff")
            }
        }
    }

    @Test
    fun theStylesAreDifferentShots() {
        val poses = GimmickStyle.entries.map { Shots.gimmick(subject, other, 0.5, it) }
        for (a in poses.indices) for (b in a + 1 until poses.size) {
            val gap = (Point(poses[a].x, poses[a].y, poses[a].z) - Point(poses[b].x, poses[b].y, poses[b].z)).length()
            assertTrue(gap > 0.3, "styles $a and $b are the same shot")
        }
    }

    @Test
    fun zPowerCranesUpAndUltraBurstDropsFromTheSky() {
        assertTrue(Shots.gimmick(subject, other, 1.0, GimmickStyle.ZPOWER).y > Shots.gimmick(subject, other, 0.0, GimmickStyle.ZPOWER).y + 1.5)
        assertTrue(Shots.gimmick(subject, other, 0.0, GimmickStyle.ULTRA).y > Shots.gimmick(subject, other, 1.0, GimmickStyle.ULTRA).y + 4.0)
    }

    @Test
    fun miniMegaOrbitSweepsAroundTheSubject() {
        val start = Shots.gimmick(subject, other, 0.0, GimmickStyle.MEGA)
        val end = Shots.gimmick(subject, other, 1.0, GimmickStyle.MEGA)
        assertTrue(kotlin.math.abs(start.x - end.x) > 1.5, "orbit moves sideways")
    }
}
