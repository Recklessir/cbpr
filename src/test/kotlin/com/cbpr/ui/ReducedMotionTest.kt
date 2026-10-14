package com.cbpr.ui

import com.cbpr.camera.BattleCamera
import com.cbpr.camera.Point
import com.cbpr.camera.Pose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReducedMotionTest {
    private val frame = 1.0 / 60

    @Test
    fun reducedBannersAppearTogetherWithoutOvershootAndDoNotBounceOnHover() {
        val normal = BannerMotion<Int>()
        val reduced = BannerMotion<Int>().also { it.reduced = true }
        for (motion in listOf(normal, reduced)) for (slot in 0 until 4) motion.setVisible(slot, true, slot)
        var peakNormal = 0.0
        var peakReduced = 0.0
        repeat(60) {
            normal.step(frame, 3)
            reduced.step(frame, 3)
            peakNormal = maxOf(peakNormal, normal.popScale(0))
            peakReduced = maxOf(peakReduced, reduced.popScale(0))
        }
        assertTrue(peakNormal > 1.02, "the normal pop overshoots ($peakNormal)")
        assertTrue(peakReduced <= 1.001, "the reduced pop does not ($peakReduced)")

        val a = BannerMotion<Int>()
        val b = BannerMotion<Int>().also { it.reduced = true }
        a.setVisible(3, true, 3)
        b.setVisible(3, true, 3)
        repeat(5) { a.step(frame, -1); b.step(frame, -1) }
        assertEquals(0.0, a.popScale(3), 1e-9)
        assertTrue(b.popScale(3) > 0.0)

        repeat(120) { reduced.step(frame, 0) }
        assertEquals(1.0, reduced.scale(0), 0.01)
    }

    @Test
    fun reducedBarsAreQuickerAndDoNotOvershoot() {
        val normal = LetterboxMotion()
        val reduced = LetterboxMotion()
        var peakNormal = 0.0
        var peakReduced = 0.0
        var timeNormal = -1.0
        var timeReduced = -1.0
        repeat(240) { i ->
            normal.step(frame, true)
            reduced.step(frame, true, reduced = true)
            peakNormal = maxOf(peakNormal, normal.fraction)
            peakReduced = maxOf(peakReduced, reduced.fraction)
            if (timeNormal < 0 && normal.fraction > 0.95) timeNormal = i * frame
            if (timeReduced < 0 && reduced.fraction > 0.95) timeReduced = i * frame
        }
        assertTrue(peakNormal > 1.001, "normal bars overshoot")
        assertTrue(peakReduced <= 1.001, "reduced bars do not ($peakReduced)")
        assertTrue(timeReduced in 0.0..timeNormal, "reduced $timeReduced normal $timeNormal")
    }

    @Test
    fun reducedTextPopsWithoutOvershoot() {
        val normal = CalloutMotion().also { it.show() }
        val reduced = CalloutMotion().also { it.reduced = true; it.show() }
        var peakNormal = 0.0
        var peakReduced = 0.0
        repeat(60) {
            normal.step(frame)
            reduced.step(frame)
            peakNormal = maxOf(peakNormal, normal.scale)
            peakReduced = maxOf(peakReduced, reduced.scale)
        }
        assertTrue(peakNormal > 1.02)
        assertTrue(peakReduced <= 1.001, "reduced $peakReduced")
    }

    @Test
    fun reducedCameraOpensQuickerAndTheMenuViewArrivesSooner() {
        val vanilla = Pose(10.0, 65.0, 10.0, 170.0, 15.0)
        val mon = Point(0.0, 1.0, 0.0)
        val foe = Point(0.0, 1.0, 8.0)
        fun secondsToCommand(reduced: Boolean): Double {
            val camera = BattleCamera()
            camera.reducedMotion = reduced
            camera.begin(vanilla)
            var t = 0.0
            while (camera.phase == BattleCamera.Phase.INTRO && t < 10.0) { camera.update(frame, vanilla, mon, foe); t += frame }
            return t
        }
        assertTrue(secondsToCommand(true) < secondsToCommand(false) * 0.8)
        fun menuProgress(reduced: Boolean): Double {
            val camera = BattleCamera()
            camera.reducedMotion = reduced
            camera.begin(vanilla)
            repeat(400) { camera.update(frame, vanilla, mon, foe) }
            val before = camera.update(frame, vanilla, mon, foe)!!
            camera.menuView = true
            repeat((0.5 / frame).toInt()) { camera.update(frame, vanilla, mon, foe) }
            val after = camera.update(frame, vanilla, mon, foe)!!
            return kotlin.math.hypot(after.x - before.x, after.z - before.z)
        }
        assertTrue(menuProgress(true) > menuProgress(false), "in half a second the reduced camera has moved further")
    }
}
