package com.cbpr.ui

import kotlin.math.roundToInt

class MoveView(
    val name: String,
    val color: Int,
    val category: String,
    val power: Double,
    val accuracy: Double,
    val pp: Int,
    val maxPp: Int,
)

enum class PpState { FULL, LOW, EMPTY }

class PartyRow(val name: String, val color: Int, val pp: String, val ppState: PpState, val details: List<String>)

object PartyPreview {

    const val LOW_SHARE = 0.25
    const val SEPARATOR = " · "

    const val MIN_BRIGHTNESS = 0.55

    fun ppState(pp: Int, maxPp: Int): PpState = when {
        pp <= 0 -> PpState.EMPTY
        maxPp > 0 && pp.toDouble() / maxPp <= LOW_SHARE -> PpState.LOW
        else -> PpState.FULL
    }

    fun powerText(power: Double): String = if (power > 0) power.roundToInt().toString() else "-"

    fun accuracyText(accuracy: Double): String = if (accuracy > 0 && accuracy <= 100) "${accuracy.roundToInt()}%" else "-"

    fun legible(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        val brightness = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        val mix = if (brightness >= MIN_BRIGHTNESS) 0.0 else ((MIN_BRIGHTNESS - brightness) / (1.0 - brightness)).coerceIn(0.0, 1.0)
        fun lift(channel: Int) = (channel + (255 - channel) * mix).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (lift(r) shl 16) or (lift(g) shl 8) or lift(b)
    }

    fun row(move: MoveView, powerLabel: String, accuracyLabel: String): PartyRow {
        val power = powerText(move.power)
        val accuracy = accuracyText(move.accuracy)
        val details = listOf(
            listOf(move.category, "$powerLabel $power", "$accuracyLabel $accuracy").joinToString(SEPARATOR),
            listOf(move.category, power, accuracy).joinToString(SEPARATOR),
            move.category,
        )
        return PartyRow(move.name, legible(move.color), "${move.pp}/${move.maxPp}", ppState(move.pp, move.maxPp), details)
    }

    fun fit(details: List<String>, widthOf: (String) -> Int, maxWidth: Int): String =
        details.firstOrNull { widthOf(it) <= maxWidth } ?: details.last()
}
