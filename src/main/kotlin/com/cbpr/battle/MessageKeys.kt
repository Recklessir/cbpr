package com.cbpr.battle

import kotlin.math.abs

enum class MessageKind {
    MOVE_USED, CRIT, SUPER_EFFECTIVE, NO_EFFECT, WIN, MEGA, TERA, ZPOWER, ULTRA, TRANSFORMATION, DYNAMAX, STAT_CHANGE, FIELD_CHANGE, VOLATILE;

    companion object {
        private const val PREFIX = "cobblemon.battle."

        fun of(key: String): MessageKind? {
            if (!key.startsWith(PREFIX, ignoreCase = true)) return null
            val name = key.substring(PREFIX.length).lowercase().removeSuffix("_spread")
            return when {
                name == "used_move" || name == "used_move_on" -> MOVE_USED
                name == "crit" -> CRIT
                name == "supereffective" || name == "extremelyeffective" -> SUPER_EFFECTIVE
                name == "missed" || name == "immune" || name == "resisted" || name == "mostlyineffective" -> NO_EFFECT
                name == "win" -> WIN
                name == "start.dynamax" || name == "start.gmax" -> DYNAMAX
                name == "mega" -> MEGA
                name == "terastallize" -> TERA
                name == "zpower" -> ZPOWER
                name == "ultra" -> ULTRA
                StatStages.of(key) != null -> STAT_CHANGE
                FieldKeys.of(key) != null -> FIELD_CHANGE
                VolatileKeys.of(key) != null -> VOLATILE
                name == "transform" || name == "formechange" || name.startsWith("formechange.") -> TRANSFORMATION
                else -> null
            }
        }
    }
}

object StatStages {
    private const val PREFIX = "cobblemon.battle."

    fun of(key: String): Int? {
        val lower = key.lowercase()
        if (!lower.startsWith(PREFIX)) return null

        val name = lower.substring(PREFIX.length).removeSuffix(".zeffect")
        val sign = when {
            name.startsWith("unboost.") -> -1
            name.startsWith("boost.") -> 1
            else -> return null
        }
        val size = when (name.substringAfter('.')) {
            "slight" -> 1
            "sharp" -> 2
            "severe" -> 3
            else -> return null
        }
        return sign * size
    }

    fun label(stat: String, stages: Int): String = "$stat ${if (stages > 0) "+" else "-"}${abs(stages)}"

    fun rgb(stages: Int): Int = if (stages > 0) UP_RGB else DOWN_RGB

    const val UP_RGB = 0x6FD3FF
    const val DOWN_RGB = 0xFF6F8A
}

object VolatileKeys {
    val CONDITIONS = setOf(
        "confusion", "attract", "leechseed", "taunt", "torment", "encore", "disable", "yawn", "nightmare", "curse",
        "healblock", "embargo", "octolock", "saltcure", "syrupbomb", "noretreat",
    )

    private const val PREFIX = "cobblemon.battle.start."

    fun of(key: String): String? {
        val lower = key.lowercase()
        return if (lower.startsWith(PREFIX)) lower.substring(PREFIX.length).takeIf { it in CONDITIONS } else null
    }

    fun afflictedArgument(id: String, argumentCount: Int): Int = if (id == "curse" && argumentCount >= 2) 1 else 0

    const val RGB = 0xD7B8FF
}
