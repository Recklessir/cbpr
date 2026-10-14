package com.cbpr.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CameraSpaceTest {
    private val mon = Point(0.0, 1.0, 0.0)
    private val foe = Point(0.0, 1.0, 8.0)
    private val frame = 1.0 / 60
    private val open = Clearance { _, _ -> 1.0 }
    private val tunnel = Clearance { _, _ -> 0.0 }

    private fun settle(space: CameraSpace, seconds: Double): Double {
        var scale = 1.0
        repeat((seconds / frame).toInt()) { scale = space.update(frame, mon, foe) }
        return scale
    }

    @Test
    fun anOrdinaryPokemonInTheOpenKeepsTheShotAsIs() {
        val space = CameraSpace(open)
        space.monSize = 1.0
        space.foeSize = 1.2
        assertEquals(1.0, settle(space, 2.0), 1e-9)
    }

    @Test
    fun aHugePokemonPushesTheCameraBackUpToTheLimit() {
        val space = CameraSpace(open)
        space.foeSize = 8.8
        assertEquals(CameraSpace.MAX_SIZE_SCALE, settle(space, 4.0), 1e-9)
    }

    @Test
    fun aSmallPokemonNeverChangesTheShot() {
        assertEquals(CameraSpace.MIN_SIZE_SCALE, CameraSpace.sizeScale(0.2, 0.3), 1e-9)
    }

    @Test
    fun aTunnelShrinksTheShotToItsFloorAndNoFurther() {
        val space = CameraSpace(tunnel)
        assertEquals(CameraSpace.MIN_TIGHT_SCALE * CameraSpace.MIN_SIZE_SCALE, settle(space, 4.0), 1e-9)
    }

    @Test
    fun theMostBlockedDirectionDecides() {
        var calls = 0
        val oneSided = Clearance { _, _ -> if (calls++ == 1) 0.2 else 1.0 }
        val tight = CameraSpace.tightScale(oneSided, mon, foe, 1.0)
        assertEquals(CameraSpace.MIN_TIGHT_SCALE + (1 - CameraSpace.MIN_TIGHT_SCALE) * 0.2, tight, 1e-9)
    }

    @Test
    fun theScaleChangesAtALimitedRateNeverAJump() {
        val space = CameraSpace(tunnel)
        var last = space.update(frame, mon, foe)
        repeat(120) {
            val next = space.update(frame, mon, foe)
            assertTrue(last - next <= CameraSpace.RATE * frame + 1e-9, "step ${last - next}")
            last = next
        }
    }

    @Test
    fun withNoClearanceOnlySizeCounts() {
        val space = CameraSpace(null)
        assertEquals(1.0, settle(space, 1.0), 1e-9)
    }

    @Test
    fun measuringRaysPerCheckAreThree() {
        var calls = 0
        CameraSpace.tightScale({ _, _ -> calls++; 1.0 }, mon, foe, 1.0)
        assertEquals(CameraSpace.RAYS, calls)
    }

    @Test
    fun resetGoesBackToTheNormalShot() {
        val space = CameraSpace(tunnel)
        settle(space, 3.0)
        space.reset()
        assertEquals(1.0, space.scale)
    }
}

class BattleCameraSpaceTest {
    private val vanilla = Pose(10.0, 65.0, 10.0, 170.0, 15.0)
    private val mon = Point(0.0, 1.0, 0.0)
    private val foe = Point(0.0, 1.0, 8.0)
    private val frame = 1.0 / 60

    private fun commandPose(prepare: (BattleCamera) -> Unit): Pose {
        val camera = BattleCamera()
        prepare(camera)
        camera.begin(vanilla)
        var pose: Pose? = null
        repeat(480) { pose = camera.update(frame, vanilla, mon, foe) }
        return pose!!
    }

    private fun distanceToMon(p: Pose) = (Point(p.x, p.y, p.z) - mon).length()

    @Test
    fun aBigPokemonGetsAFartherCameraAndATunnelACloserOne() {
        val normal = commandPose { }
        val big = commandPose { it.useSpace(null, true); it.useBattle(BattleSnapshot(listOf(BattleSnapshot.Member("p1a", mon, 1.0)), listOf(BattleSnapshot.Member("p2a", foe, 8.8)))) }
        val tunnel = commandPose { it.useSpace({ _, _ -> 0.0 }, true) }
        assertTrue(distanceToMon(big) > distanceToMon(normal) + 1.0)
        assertTrue(distanceToMon(tunnel) < distanceToMon(normal) - 0.5)
    }

    @Test
    fun switchingTheAdaptationOffRestoresTheNormalShot() {
        val normal = commandPose { }
        val off = commandPose { it.useSpace({ _, _ -> 0.0 }, false) }
        assertEquals(distanceToMon(normal), distanceToMon(off), 1e-6)
    }
}

class BattleCameraDriftTest {
    private val vanilla = Pose(10.0, 65.0, 10.0, 170.0, 15.0)
    private val mon = Point(0.0, 1.0, 0.0)
    private val foe = Point(0.0, 1.0, 8.0)
    private val frame = 1.0 / 60

    private fun settledPose(setup: (BattleCamera) -> Unit): Pose {
        val camera = BattleCamera()
        camera.begin(vanilla)
        setup(camera)
        var pose: Pose? = null
        repeat(480) { pose = camera.update(frame, vanilla, mon, foe) }
        return pose!!
    }

    @Test
    fun theViewLeansTowardTheCursorByAFewDegreesAtMost() {
        val still = settledPose { }
        val right = settledPose { it.parallax = true; it.cursorX = 1.0; it.cursorY = -1.0 }
        assertEquals(BattleCamera.DRIFT_YAW_DEGREES, right.yaw - still.yaw, 0.2)
        assertEquals(-BattleCamera.DRIFT_PITCH_DEGREES, right.pitch - still.pitch, 0.2)
    }

    @Test
    fun withDriftOffTheCursorChangesNothing() {
        val still = settledPose { }
        val off = settledPose { it.parallax = false; it.cursorX = 1.0; it.cursorY = 1.0 }
        assertEquals(still.yaw, off.yaw, 1e-6)
        assertEquals(still.pitch, off.pitch, 1e-6)
    }

    @Test
    fun noDriftDuringTheIntroSweep() {
        val camera = BattleCamera()
        camera.parallax = true
        camera.cursorX = 1.0
        camera.begin(vanilla)
        val reference = BattleCamera()
        reference.begin(vanilla)
        var a: Pose? = null
        var b: Pose? = null
        repeat(60) {
            a = camera.update(frame, vanilla, mon, foe)
            b = reference.update(frame, vanilla, mon, foe)
        }
        assertEquals(b!!.yaw, a!!.yaw, 1e-6)
    }

    @Test
    fun scalingAShotAboutThePokemonItLooksAtKeepsItAimedThereEvenForAGiant() {
        val mon = Point(0.0, 6.0, 0.0)
        val foe = Point(0.0, 1.0, 12.0)

        val shot = Shots.orbit(mon, foe, 0.3, 200.0)
        val candidates = listOf(mon, foe, CameraAvoidance.focus(mon, foe))
        val fitted = CameraAvoidance.apply(CameraAvoidance.Offset(2.5, 0.0, 0.0), shot, CameraSpace.pivot(shot, candidates))
        val aim = Shots.lookAt(Point(fitted.x, fitted.y, fitted.z), Point(mon.x, mon.y + 0.1, mon.z))
        assertEquals(aim.yaw, fitted.yaw, 1.0)
        assertEquals(aim.pitch, fitted.pitch, 2.0)

        val old = CameraAvoidance.apply(CameraAvoidance.Offset(2.5, 0.0, 0.0), shot, CameraAvoidance.focus(mon, foe))
        val oldAim = Shots.lookAt(Point(old.x, old.y, old.z), Point(mon.x, mon.y + 0.1, mon.z))
        assertTrue(kotlin.math.abs(oldAim.pitch - old.pitch) > 5.0, "the old pivot misses by ${oldAim.pitch - old.pitch} degrees")
    }

    @Test
    fun theCommandShotPivotsOnWhicheverPointItLooksThrough() {
        val mon = Point(0.0, 1.0, 0.0)
        val foe = Point(0.0, 1.0, 8.0)
        val shot = Shots.command(mon, foe, 0.0)
        val pivot = CameraSpace.pivot(shot, listOf(mon, foe, CameraAvoidance.focus(mon, foe)))
        val fitted = CameraAvoidance.apply(CameraAvoidance.Offset(2.0, 0.0, 0.0), shot, pivot)
        val before = Shots.lookAt(Point(shot.x, shot.y, shot.z), pivot)
        val after = Shots.lookAt(Point(fitted.x, fitted.y, fitted.z), pivot)
        assertEquals(before.yaw, after.yaw, 0.5)
        assertEquals(before.pitch, after.pitch, 0.5)
    }

    @Test
    fun aTinyEnemyAttackingAGiantNeverPutsTheCameraUnderTheGround() {
        val giant = Point(0.0, 7.0, 0.0)
        val tiny = Point(0.0, 0.4, 10.0)
        val candidates = listOf(giant, tiny, CameraAvoidance.focus(giant, tiny))
        val shot = Shots.command(tiny, giant, 0.0, 1.0, 1.0)
        val naive = CameraAvoidance.apply(CameraAvoidance.Offset(2.5, 0.0, 0.0), shot, CameraSpace.pivot(shot, candidates))
        assertTrue(naive.y < 0.0, "scaling about the giant alone buries the camera at ${naive.y}")
        val fitted = CameraSpace.fit(shot, 2.5, candidates, minOf(giant.y, tiny.y))
        assertTrue(fitted.y >= minOf(shot.y, tiny.y) - 1e-9, "camera height ${fitted.y}")
        val toGiant = Shots.lookAt(Point(fitted.x, fitted.y, fitted.z), Point(giant.x, giant.y, giant.z))
        assertEquals(toGiant.yaw, fitted.yaw, 5.0)
    }

    @Test
    fun fitIsJustTheScaledShotWhenNothingGoesUnderground() {
        val mon = Point(0.0, 1.0, 0.0)
        val foe = Point(0.0, 1.0, 8.0)
        val shot = Shots.command(mon, foe, 0.0)
        val candidates = listOf(mon, foe, CameraAvoidance.focus(mon, foe))
        val fitted = CameraSpace.fit(shot, 1.8, candidates, 1.0)
        assertEquals(CameraAvoidance.apply(CameraAvoidance.Offset(1.8, 0.0, 0.0), shot, CameraSpace.pivot(shot, candidates)), fitted)
    }
}
