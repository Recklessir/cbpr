package com.cbpr.audio

import com.cbpr.config.CbprConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoundGateTest {
    private var config = CbprConfig()
    private val gate = SoundGate { config }

    @Test
    fun aSoundPlaysOnce() {
        assertTrue(gate.allow(SoundSlot.CRIT, 1.0))
    }

    @Test
    fun theSameSlotRepeatedTooSoonIsDroppedButAfterTheGapPlays() {
        assertTrue(gate.allow(SoundSlot.CRIT, 1.0))
        assertFalse(gate.allow(SoundSlot.CRIT, 1.0 + SoundGate.DEFAULT_GAP / 2))
        assertTrue(gate.allow(SoundSlot.CRIT, 1.0 + SoundGate.DEFAULT_GAP * 2))
    }

    @Test
    fun differentSlotsDoNotBlockEachOther() {
        assertTrue(gate.allow(SoundSlot.CRIT, 1.0))
        assertTrue(gate.allow(SoundSlot.SUPER_EFFECTIVE, 1.0))
    }

    @Test
    fun hoverIsThrottledHarderThanOtherSounds() {
        assertTrue(gate.allow(SoundSlot.UI_HOVER, 1.0))
        assertFalse(gate.allow(SoundSlot.UI_HOVER, 1.0 + SoundGate.HOVER_GAP / 2))
        assertTrue(gate.allow(SoundSlot.UI_HOVER, 1.0 + SoundGate.HOVER_GAP + 0.01))
    }

    @Test
    fun soundsOffSilencesEverythingAndZeroVolumeToo() {
        config = CbprConfig(sounds = false)
        assertFalse(gate.allow(SoundSlot.UI_SELECT, 1.0))
        config = CbprConfig(soundVolume = 0.0)
        assertFalse(gate.allow(SoundSlot.UI_SELECT, 2.0))
    }

    @Test
    fun reducedNavigationCutsOnlyNavigationSounds() {
        config = CbprConfig(reducedNavigationSounds = true)
        assertFalse(gate.allow(SoundSlot.UI_HOVER, 1.0))
        assertFalse(gate.allow(SoundSlot.PANEL_OPEN, 1.0))
        assertTrue(gate.allow(SoundSlot.UI_SELECT, 1.0))
        assertTrue(gate.allow(SoundSlot.FAINT, 1.0))
    }

    @Test
    fun reducedCinematicCutsOnlyCinematicSounds() {
        config = CbprConfig(reducedCinematicAudio = true)
        assertFalse(gate.allow(SoundSlot.TRANSITION, 1.0))
        assertTrue(gate.allow(SoundSlot.UI_HOVER, 1.0))
        assertTrue(gate.allow(SoundSlot.FAINT, 1.0))
    }

    @Test
    fun aDroppedSoundDoesNotExtendTheGap() {
        assertTrue(gate.allow(SoundSlot.CRIT, 1.0))
        assertFalse(gate.allow(SoundSlot.CRIT, 1.03))
        assertTrue(gate.allow(SoundSlot.CRIT, 1.06))
    }

    @Test
    fun volumeFollowsTheConfig() {
        config = CbprConfig(soundVolume = 0.4)
        assertEquals(0.4f, gate.volume())
    }

    @Test
    fun resetForgetsTheGaps() {
        gate.allow(SoundSlot.CRIT, 1.0)
        gate.reset()
        assertTrue(gate.allow(SoundSlot.CRIT, 1.0))
    }
}
