package com.cbpr.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BattleCameraTest {
    private val vanilla = Pose(10.0, 65.0, 10.0, 170.0, 15.0)
    private val mon = Point(0.0, 1.0, 0.0)
    private val foe = Point(0.0, 1.0, 8.0)
    private val frame = 1.0 / 60

    private fun run(camera: BattleCamera, seconds: Double, p: Point? = mon, f: Point? = foe): Pose? {
        var pose: Pose? = null
        repeat((seconds / frame).toInt()) { pose = camera.update(frame, vanilla, p, f) }
        return pose
    }

    @Test
    fun offCameraLeavesVanillaAlone() {
        assertNull(BattleCamera().update(frame, vanilla, mon, foe))
    }

    @Test
    fun introEndsInCommandShot() {
        val camera = BattleCamera()
        camera.begin(vanilla)
        assertEquals(BattleCamera.Phase.INTRO, camera.phase)
        run(camera, 5.0)
        assertEquals(BattleCamera.Phase.COMMAND, camera.phase)
    }

    @Test
    fun firstFrameStartsAtVanillaPose() {
        val camera = BattleCamera()
        camera.begin(vanilla)
        val first = assertNotNull(camera.update(frame, vanilla, mon, foe))
        assertEquals(vanilla.x, first.x, 0.5)
        assertEquals(vanilla.y, first.y, 0.5)
    }

    @Test
    fun endReturnsToVanillaThenReleases() {
        val camera = BattleCamera()
        camera.begin(vanilla)
        run(camera, 5.0)
        camera.end()
        assertEquals(BattleCamera.Phase.RETURNING, camera.phase)
        var last: Pose? = null
        repeat(((BattleCamera.RETURN_TIMEOUT + 1) / frame).toInt()) {
            last = camera.update(frame, vanilla, null, null) ?: last
        }
        assertEquals(BattleCamera.Phase.OFF, camera.phase)
        assertEquals(vanilla.x, last!!.x, 0.1)
        assertEquals(last.yaw, BattleCamera.nearest(last.yaw, vanilla.yaw), 1.0)
    }

    @Test
    fun returnNeverOutlastsTimeoutEvenIfVanillaKeepsMoving() {
        val camera = BattleCamera()
        camera.begin(vanilla)
        run(camera, 3.0)
        camera.end()
        var t = 0.0
        while (camera.phase != BattleCamera.Phase.OFF && t < 10.0) {
            val moving = vanilla.copy(x = vanilla.x + t * 20)
            camera.update(frame, moving, null, null)
            t += frame
        }
        assertEquals(BattleCamera.Phase.OFF, camera.phase)
        assertTrue(t <= BattleCamera.RETURN_TIMEOUT + 0.1)
    }

    @Test
    fun abortReleasesImmediately() {
        val camera = BattleCamera()
        camera.begin(vanilla)
        run(camera, 1.0)
        camera.abort()
        assertEquals(BattleCamera.Phase.OFF, camera.phase)
        assertNull(camera.update(frame, vanilla, mon, foe))
    }

    @Test
    fun lostSubjectsHoldThenReturn() {
        val camera = BattleCamera()
        camera.begin(vanilla)
        run(camera, 5.0)
        run(camera, 1.0, null, null)
        assertEquals(BattleCamera.Phase.COMMAND, camera.phase)
        run(camera, BattleCamera.LOST_SUBJECT_TIMEOUT, null, null)
        assertEquals(BattleCamera.Phase.RETURNING, camera.phase)
    }

    @Test
    fun beginDuringReturnKeepsPositionInsteadOfSnapping() {
        val camera = BattleCamera()
        camera.begin(vanilla)
        run(camera, 5.0)
        camera.end()
        val mid = assertNotNull(camera.update(frame, vanilla, null, null))
        camera.begin(vanilla)
        val next = assertNotNull(camera.update(frame, vanilla, mon, foe))
        assertEquals(mid.x, next.x, 0.5)
    }

    @Test
    fun nearestPicksShortestWay() {
        assertEquals(190.0, BattleCamera.nearest(170.0, -170.0), 1e-9)
        assertEquals(-190.0, BattleCamera.nearest(-170.0, 170.0), 1e-9)
        assertEquals(10.0, BattleCamera.nearest(0.0, 370.0), 1e-9)
    }
}
