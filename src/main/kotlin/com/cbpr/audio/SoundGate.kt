package com.cbpr.audio

import com.cbpr.config.CbprConfig

class SoundGate(private val settings: () -> CbprConfig) {
    private val last = HashMap<SoundSlot, Double>()

    fun allow(slot: SoundSlot, nowSeconds: Double): Boolean {
        val config = settings()
        if (!config.sounds || config.soundVolume <= 0.0) return false
        if (config.reducedNavigationSounds && slot.group == SoundSlot.Group.NAVIGATION) return false
        if (config.reducedCinematicAudio && slot.group == SoundSlot.Group.CINEMATIC) return false
        val before = last[slot]
        if (before != null && nowSeconds - before < minGap(slot)) return false
        last[slot] = nowSeconds
        return true
    }

    fun volume(): Float = settings().soundVolume.toFloat()

    fun reset() = last.clear()

    private fun minGap(slot: SoundSlot) = when (slot) {
        SoundSlot.UI_HOVER -> HOVER_GAP
        else -> DEFAULT_GAP
    }

    companion object {
        const val HOVER_GAP = 0.12
        const val DEFAULT_GAP = 0.05
    }
}
