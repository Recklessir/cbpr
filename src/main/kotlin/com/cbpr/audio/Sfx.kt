package com.cbpr.audio

import com.cbpr.camera.Point
import com.cbpr.config.Settings
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import org.slf4j.LoggerFactory

object Sfx {
    private val log = LoggerFactory.getLogger("cbpr")
    private val gate = SoundGate { Settings.current }
    private val events = SoundSlot.entries.associateWith {
        SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("cbpr", it.id))
    }
    private var failed = false

    fun play(slot: SoundSlot?, at: Point? = null): Boolean {
        if (slot == null || failed) return false
        try {
            val event = events.getValue(slot)
            val mc = Minecraft.getInstance()
            val weighed = mc.soundManager.getSoundEvent(event.location) ?: return false
            if (weighed.getSound(RandomSource.create()) === SoundManager.EMPTY_SOUND) return false
            if (!gate.allow(slot, System.nanoTime() / 1e9)) return false
            val volume = gate.volume()
            val instance = if (slot.positional && at != null) {
                SimpleSoundInstance(event, SoundSource.PLAYERS, volume, 1f, RandomSource.create(), at.x, at.y, at.z)
            } else {
                SimpleSoundInstance.forUI(event, 1f, volume)
            }
            mc.soundManager.play(instance)
            return true
        } catch (e: Exception) {
            failed = true
            log.error("CBPR sound failure, sounds are off until restart", e)
            return false
        }
    }

    fun reset() = gate.reset()
}
