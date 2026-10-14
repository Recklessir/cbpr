package com.cbpr.ui

import java.util.UUID
import kotlin.math.roundToInt

object Rgb {

    fun mix(a: Int, b: Int, t: Double): Int {
        val m = t.coerceIn(0.0, 1.0)
        fun channel(shift: Int): Int {
            val from = (a shr shift) and 0xFF
            val to = (b shr shift) and 0xFF
            return (from + (to - from) * m).roundToInt()
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}

object StatusCue {
    fun rgb(path: String): Int? = when (path) {
        "burn" -> 0xFF7A3D
        "frozen" -> 0x7FE8FF
        "paralysis" -> 0xFFE14D
        "poison" -> 0xB36BFF
        "poisonbadly" -> 0x8F3FD6
        "sleep" -> 0x8FA3D9
        else -> null
    }

    fun langKey(path: String): String = "cbpr.status.$path"
}

class StatusWatch {
    private val last = HashMap<UUID, String?>()

    fun update(id: UUID, path: String?): String? {
        if (!last.containsKey(id)) {
            last[id] = path
            return null
        }
        val before = last[id]
        last[id] = path
        return if (path != null && path != before) path else null
    }

    fun clear() = last.clear()
}
