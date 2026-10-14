package com.cbpr.ui

import com.cbpr.camera.Point
import com.cbpr.camera.Spring
import kotlin.math.roundToInt

object DamageText {

    fun percent(lost: Double): Int {
        val rounded = (-lost * 100).roundToInt()
        return if (rounded == 0 && lost != 0.0) (if (lost > 0) -1 else 1) else rounded
    }

    fun label(percent: Int): String = if (percent > 0) "+$percent%" else "$percent%"

    fun rgb(percent: Int): Int {
        if (percent > 0) return HEAL_RGB
        val mix = (-percent.toDouble() / SEVERE_PERCENT).coerceIn(0.0, 1.0)
        fun channel(shift: Int): Int {
            val from = (LIGHT_RGB shr shift) and 0xFF
            val to = (SEVERE_RGB shr shift) and 0xFF
            return (from + (to - from) * mix).roundToInt()
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun scaleAt(distance: Double): Double = (REFERENCE_DISTANCE / distance.coerceAtLeast(0.1)).coerceIn(MIN_SCALE, MAX_SCALE)

    const val SEVERE_PERCENT = 50.0
    const val HEAL_RGB = 0x6EE07A
    const val LIGHT_RGB = 0xFFE2A0
    const val SEVERE_RGB = 0xFF4636
    const val REFERENCE_DISTANCE = 6.0
    const val MIN_SCALE = 0.5
    const val MAX_SCALE = 1.1
}

class DamagePopup(val label: String, val rgb: Int, val anchor: Point, val slot: Int, private val reduced: Boolean) {
    private val pop = Spring(0.0, POP_STIFFNESS, Spring.dampingFor(POP_STIFFNESS, POP_DAMPING_RATIO))

    var age = 0.0
        private set

    val scale: Double get() = if (reduced) 1.0 else pop.value.coerceAtLeast(0.0)

    val rise: Double get() = if (reduced) 0.0 else RISE_BLOCKS * (1.0 - (1.0 - (age / LIFE_SECONDS).coerceIn(0.0, 1.0)).let { it * it })

    val alpha: Double get() = if (age < FADE_START) 1.0 else (1.0 - (age - FADE_START) / (LIFE_SECONDS - FADE_START)).coerceIn(0.0, 1.0)

    val done: Boolean get() = age >= LIFE_SECONDS

    fun step(dt: Double) {
        age += dt
        pop.step(1.0, dt)
    }

    companion object {
        const val LIFE_SECONDS = 1.3
        const val FADE_START = 0.8
        const val RISE_BLOCKS = 0.9
        private const val POP_STIFFNESS = 320.0

        private const val POP_DAMPING_RATIO = 0.63

        fun damage(percent: Int, anchor: Point, slot: Int, reduced: Boolean) =
            DamagePopup(DamageText.label(percent), DamageText.rgb(percent), anchor, slot, reduced)
    }
}

class DamagePopups {
    private val popups = ArrayList<DamagePopup>()
    private var counter = 0

    val active: List<DamagePopup> get() = popups

    fun add(lost: Double, anchor: Point, reduced: Boolean) {
        if (lost == 0.0) return
        if (popups.size >= MAX_POPUPS) popups.removeAt(0)
        popups += DamagePopup.damage(DamageText.percent(lost), anchor, counter++ % SLOTS, reduced)
    }

    fun addCue(label: String, rgb: Int, anchor: Point, reduced: Boolean) {
        if (popups.size >= MAX_POPUPS) popups.removeAt(0)
        popups += DamagePopup(label, rgb, anchor, counter++ % SLOTS, reduced)
    }

    fun step(dt: Double) {
        popups.forEach { it.step(dt) }
        popups.removeAll { it.done }
    }

    fun clear() {
        popups.clear()
        counter = 0
    }

    companion object {
        const val MAX_POPUPS = 8

        const val SLOTS = 3
    }
}
