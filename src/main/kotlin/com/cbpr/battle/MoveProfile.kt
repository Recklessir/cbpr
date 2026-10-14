package com.cbpr.battle

import com.google.gson.JsonObject

class MoveProfile(
    val id: String,
    val moves: Set<String>,
    val types: Set<String>,
    val anticipation: Double,
    val approach: Double,
    val waitForImpact: Double,
    val hold: Double,
    val distanceScale: Double,
    val heightScale: Double,
    val swingDegrees: Double,
    val fovKick: Double,
    val shake: Double,
    val tilt: Double,
    val hitStop: Double,
    val callout: Boolean,
    val sound: String?,
) {
    companion object {
        val GENERIC = MoveProfile(
            id = "generic", moves = emptySet(), types = emptySet(),
            anticipation = 0.25, approach = 0.5, waitForImpact = 1.6, hold = 0.7,
            distanceScale = 0.75, heightScale = 0.9, swingDegrees = 35.0,
            fovKick = 5.0, shake = 0.6, tilt = 0.0, hitStop = 0.06, callout = true, sound = null,
        )

        private val ID = Regex("[a-z0-9_]+")

        fun parse(json: JsonObject, onProblem: (String) -> Unit): MoveProfile? {
            val id = json.string("id")
            if (id == null || !ID.matches(id)) {
                onProblem("profile has no valid \"id\" (lowercase letters, digits and underscores)")
                return null
            }
            fun number(name: String, fallback: Double, range: ClosedFloatingPointRange<Double>): Double {
                val element = json.get(name) ?: return fallback
                val value = if (element.isJsonPrimitive && element.asJsonPrimitive.isNumber) element.asDouble else null
                if (value == null || value !in range) {
                    onProblem("profile \"$id\": \"$name\" must be a number from ${range.start} to ${range.endInclusive}, using $fallback")
                    return fallback
                }
                return value
            }
            val g = GENERIC
            return MoveProfile(
                id = id,
                moves = json.strings("moves", id, onProblem).map(::moveId).toSet(),
                types = json.strings("types", id, onProblem).map { it.lowercase() }.toSet(),
                anticipation = number("anticipation", g.anticipation, 0.0..2.0),
                approach = number("approach", g.approach, 0.1..3.0),
                waitForImpact = number("waitForImpact", g.waitForImpact, 0.0..4.0),
                hold = number("hold", g.hold, 0.0..3.0),
                distanceScale = number("distanceScale", g.distanceScale, 0.3..2.0),
                heightScale = number("heightScale", g.heightScale, 0.3..2.0),
                swingDegrees = number("swingDegrees", g.swingDegrees, -90.0..90.0),
                fovKick = number("fovKick", g.fovKick, 0.0..15.0),
                shake = number("shake", g.shake, 0.0..3.0),
                tilt = number("tilt", g.tilt, 0.0..15.0),
                hitStop = number("hitStop", g.hitStop, 0.0..0.15),
                callout = json.get("callout")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: g.callout,
                sound = json.string("sound"),
            )
        }

        fun moveId(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

        private fun JsonObject.string(name: String): String? =
            get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

        private fun JsonObject.strings(name: String, id: String, onProblem: (String) -> Unit): List<String> {
            val element = get(name) ?: return emptyList()
            if (!element.isJsonArray || element.asJsonArray.any { !it.isJsonPrimitive || !it.asJsonPrimitive.isString }) {
                onProblem("profile \"$id\": \"$name\" must be a list of strings, ignoring it")
                return emptyList()
            }
            return element.asJsonArray.map { it.asString }
        }
    }
}

class MoveInfo(val id: String, val category: String, val type: String, val target: String)

object MoveArchetypes {
    private val SELF_TARGETS = setOf("self", "allies", "allyside", "allyteam", "adjacentally", "adjacentallyorself")
    private val FIELD_TARGETS = setOf("all", "foeside")
    private val SPREAD_TARGETS = setOf("alladjacent", "alladjacentfoes")

    fun of(move: MoveInfo): String? {
        val target = move.target.lowercase()
        return when (move.category.lowercase()) {
            "status" -> when (target) {
                in SELF_TARGETS -> "self"
                in FIELD_TARGETS -> "field"
                else -> "status"
            }
            "physical", "special" -> when {
                target in SPREAD_TARGETS -> "area"
                move.category.equals("physical", ignoreCase = true) -> "rush"
                else -> "projectile"
            }
            else -> null
        }
    }
}

class ProfileSet(profiles: Collection<MoveProfile>) {
    private val all = profiles.sortedBy { it.id }
    private val byId = all.associateBy { it.id }

    fun resolve(move: MoveInfo): MoveProfile =
        all.firstOrNull { move.id in it.moves }
            ?: MoveArchetypes.of(move)?.let(byId::get)
            ?: all.firstOrNull { move.type.lowercase() in it.types }
            ?: byId["generic"]
            ?: MoveProfile.GENERIC
}
