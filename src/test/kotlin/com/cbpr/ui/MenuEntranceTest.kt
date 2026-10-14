package com.cbpr.ui

import com.cbpr.audio.SoundGate
import com.cbpr.audio.SoundSlot
import com.cbpr.config.CbprConfig
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MenuEntranceTest {
    private val frame = 1.0 / 240

    private fun settledAt(motion: BannerMotion<Int>, slot: Int, limit: Double = 1.0): Double {
        var t = 0.0
        var lastOutside = 0.0
        while (t < limit) {
            motion.step(frame, -1)
            t += frame
            if (abs(1.0 - motion.popScale(slot)) > 0.025) lastOutside = t
        }
        return lastOutside
    }

    @Test
    fun aReplayIsTheSamePopAsASlotAppearingForTheFirstTime() {
        val first = BannerMotion<Int>()
        first.setVisible(0, true)
        val again = BannerMotion<Int>()
        again.setVisible(0, true)
        repeat((1.0 / frame).toInt()) { again.step(frame, -1) }
        again.replay(0, 0)
        repeat((0.6 / frame).toInt()) {
            first.step(frame, -1)
            again.step(frame, -1)
            assertEquals(first.popScale(0), again.popScale(0), 1e-9, "step $it")
        }
    }

    @Test
    fun aMenuSwapPopsTheButtonsInAgainEvenThoughTheirSlotsWereAlreadyFullSize() {
        val motion = BannerMotion<Int>()
        for (i in 0 until 4) motion.setVisible(i, true, i)
        repeat((1.0 / frame).toInt()) { motion.step(frame, -1) }
        assertEquals(1.0, motion.popScale(0), 0.01)

        for (i in 0 until 4) motion.replay(i, i)
        assertEquals(0.0, motion.popScale(0), 1e-12, "starts from nothing")
        repeat((1.0 / frame).toInt()) { motion.step(frame, -1) }
        assertEquals(1.0, motion.popScale(0), 0.01, "and settles")
    }

    @Test
    fun everyOpeningPopsAgainWithNoMemoryOfHavingPlayed() {
        val motion = BannerMotion<Int>()
        motion.setVisible(0, true)
        repeat(4) {
            repeat((0.8 / frame).toInt()) { motion.step(frame, -1) }
            motion.replay(0, 0)
            assertEquals(0.0, motion.popScale(0), 1e-12, "opening ${it + 1} starts from nothing")
        }
    }

    @Test
    fun buttonsPopOneAfterAnotherSixtyMillisecondsApart() {
        val motion = BannerMotion<Int>()
        for (i in 0 until 4) motion.replay(i, i)
        var t = 0.0
        val firstMoved = DoubleArray(4) { -1.0 }
        while (t < 0.4) {
            motion.step(frame, -1)
            t += frame
            for (i in 0 until 4) if (firstMoved[i] < 0.0 && motion.popScale(i) > 1e-6) firstMoved[i] = t
        }
        for (i in 1 until 4) assertEquals(BannerMotion.STAGGER_SECONDS, firstMoved[i] - firstMoved[i - 1], 0.01, "button $i")
    }

    @Test
    fun thePopOvershootsAboutEightPercentThenSettles() {
        val motion = BannerMotion<Int>()
        motion.replay(0, 0)
        var peak = 0.0
        repeat((1.0 / frame).toInt()) {
            motion.step(frame, -1)
            peak = maxOf(peak, motion.popScale(0))
        }
        assertTrue(peak in 1.04..1.14, "peak $peak")
        assertEquals(1.0, motion.popScale(0), 0.01)
        assertFalse(motion.isEntering(0))
    }

    @Test
    fun aButtonStillPoppingInCountsAsHittableAtOnce() {
        val motion = BannerMotion<Int>()
        motion.replay(0, 3)
        assertTrue(motion.isEntering(0), "even while it is still waiting its turn")
        val plain = BannerMotion<Int>()
        plain.setVisible(0, true)
        assertFalse(plain.isEntering(0), "the first-time pop is unchanged")
    }

    @Test
    fun theExitIsFasterThanTheEntranceAndNeverNegative() {
        val motion = BannerMotion<Int>()
        motion.replay(0, 0)
        repeat((1.0 / frame).toInt()) { motion.step(frame, -1) }
        var enter = 0.0
        val entering = BannerMotion<Int>()
        entering.replay(0, 0)
        while (entering.popScale(0) < 0.9) {
            entering.step(frame, -1)
            enter += frame
        }
        val settle = settledAt(BannerMotion<Int>().also { it.replay(0, 0) }, 0, 2.0)
        motion.setVisible(0, false)
        var exit = 0.0
        while (motion.popScale(0) > 0.1) {
            motion.step(frame, -1)
            exit += frame
            assertTrue(motion.popScale(0) >= 0.0)
        }
        assertTrue(exit < settle, "exit $exit against a settle of $settle (first reaches 90% at $enter)")
    }

    @Test
    fun menuOpenIsAnavigationSlotSoReducedMenuSoundsSilencesIt() {
        assertEquals(SoundSlot.Group.NAVIGATION, SoundSlot.MENU_OPEN.group)
        assertEquals(SoundSlot.MENU_OPEN, SoundSlot.fromId("menu_open"))
        assertTrue(SoundGate { CbprConfig() }.allow(SoundSlot.MENU_OPEN, 1.0))
        assertFalse(SoundGate { CbprConfig(reducedNavigationSounds = true) }.allow(SoundSlot.MENU_OPEN, 1.0))
        assertFalse(SoundGate { CbprConfig(sounds = false) }.allow(SoundSlot.MENU_OPEN, 1.0))
        assertFalse(SoundGate { CbprConfig(soundVolume = 0.0) }.allow(SoundSlot.MENU_OPEN, 1.0))
    }
}
