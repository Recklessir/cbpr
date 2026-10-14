package com.cbpr.battle

class FieldChange(val id: String, val started: Boolean)

object FieldKeys {
    val WEATHER = setOf("raindance", "sunnyday", "sandstorm", "hail", "snow")
    val TERRAIN = setOf("electricterrain", "grassyterrain", "mistyterrain", "psychicterrain")

    private const val PREFIX = "cobblemon.battle."

    fun of(key: String): FieldChange? {
        val lower = key.lowercase()
        if (!lower.startsWith(PREFIX)) return null
        val name = lower.substring(PREFIX.length)
        return when {
            name.startsWith("weather.") -> {
                val parts = name.removePrefix("weather.").split('.')
                if (parts.size != 2 || parts[0] !in WEATHER) return null
                when (parts[1]) {
                    "start" -> FieldChange(parts[0], true)
                    "end" -> FieldChange(parts[0], false)
                    else -> null
                }
            }
            name.startsWith("fieldstart.") -> name.removePrefix("fieldstart.").takeIf { it in TERRAIN }?.let { FieldChange(it, true) }
            name.startsWith("fieldend.") -> name.removePrefix("fieldend.").takeIf { it in TERRAIN }?.let { FieldChange(it, false) }
            else -> null
        }
    }
}

class FieldState {
    private var weather: String? = null
    private var terrain: String? = null
    private var terrainIsNewer = false

    fun apply(id: String, started: Boolean) {
        if (id in FieldKeys.TERRAIN) {
            terrain = if (started) id else terrain.takeIf { it != id }
            terrainIsNewer = started
        } else {
            weather = if (started) id else weather.takeIf { it != id }
            terrainIsNewer = !started && terrainIsNewer
        }
    }

    val current: String? get() = if (terrainIsNewer) terrain ?: weather else weather ?: terrain

    fun clear() {
        weather = null
        terrain = null
        terrainIsNewer = false
    }
}

object FieldWash {
    fun rgb(id: String): Int? = when (id) {
        "raindance" -> 0x3A6FD8
        "sunnyday" -> 0xFFB43A
        "sandstorm" -> 0xC9A15A
        "hail" -> 0x9FD8FF
        "snow" -> 0xE8F1FF
        "electricterrain" -> 0xFFE14D
        "grassyterrain" -> 0x5FD16A
        "mistyterrain" -> 0xFF9FD1
        "psychicterrain" -> 0xC070FF
        else -> null
    }
}
