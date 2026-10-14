package com.cbpr.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class CbprConfig(

    val enabled: Boolean = true,

    val doublesBattles: Boolean = true,

    val triplesBattles: Boolean = true,

    val spectatorCamera: Boolean = true,
    val cinematicShots: Boolean = true,
    val letterbox: Boolean = true,
    val callouts: Boolean = true,
    val impactStamps: Boolean = true,
    val typeColour: Boolean = true,
    val damageNumbers: Boolean = true,
    val titleCard: Boolean = true,
    val foeRing: Boolean = true,
    val statusCues: Boolean = true,
    val healthCue: Boolean = true,
    val captureShot: Boolean = true,
    val weatherWash: Boolean = true,

    val debugLog: Boolean = false,
    val critSpecials: Boolean = true,
    val hitStop: Boolean = false,
    val faintShot: Boolean = true,
    val sendOutSwoop: Boolean = false,
    val victoryOrbit: Boolean = true,
    val reducedMotion: Boolean = false,
    val cameraAvoidance: Boolean = true,
    val occlusionFade: Boolean = true,
    val experimentalRendering: Boolean = false,
    val occlusionOutline: Boolean = false,
    val menuCamera: Boolean = true,
    val environmentAdaptation: Boolean = true,
    val specialShot: Boolean = true,
    val parallax: Boolean = true,
    val groundRing: Boolean = true,
    val sounds: Boolean = true,
    val reducedNavigationSounds: Boolean = false,
    val reducedCinematicAudio: Boolean = false,
    val soundVolume: Double = 1.0,
) {
    fun toJson(): String = JsonObject().apply {
        addProperty("enabled", enabled)
        addProperty("doublesBattles", doublesBattles)
        addProperty("triplesBattles", triplesBattles)
        addProperty("spectatorCamera", spectatorCamera)
        addProperty("cinematicShots", cinematicShots)
        addProperty("letterbox", letterbox)
        addProperty("callouts", callouts)
        addProperty("impactStamps", impactStamps)
        addProperty("typeColour", typeColour)
        addProperty("damageNumbers", damageNumbers)
        addProperty("titleCard", titleCard)
        addProperty("foeRing", foeRing)
        addProperty("statusCues", statusCues)
        addProperty("healthCue", healthCue)
        addProperty("captureShot", captureShot)
        addProperty("weatherWash", weatherWash)
        addProperty("debugLog", debugLog)
        addProperty("critSpecials", critSpecials)
        addProperty("hitStop", hitStop)
        addProperty("faintShot", faintShot)
        addProperty("sendOutSwoop", sendOutSwoop)
        addProperty("victoryOrbit", victoryOrbit)
        addProperty("reducedMotion", reducedMotion)
        addProperty("cameraAvoidance", cameraAvoidance)
        addProperty("occlusionFade", occlusionFade)
        addProperty("experimentalRendering", experimentalRendering)
        addProperty("occlusionOutline", occlusionOutline)
        addProperty("menuCamera", menuCamera)
        addProperty("environmentAdaptation", environmentAdaptation)
        addProperty("specialShot", specialShot)
        addProperty("parallax", parallax)
        addProperty("groundRing", groundRing)
        addProperty("sounds", sounds)
        addProperty("reducedNavigationSounds", reducedNavigationSounds)
        addProperty("reducedCinematicAudio", reducedCinematicAudio)
        addProperty("soundVolume", soundVolume)
    }.let { GsonBuilder().setPrettyPrinting().create().toJson(it) }

    companion object {

        fun parse(text: String?, onProblem: (String) -> Unit): CbprConfig {
            val json = try {
                text?.let { JsonParser.parseString(it) }?.takeIf { it.isJsonObject }?.asJsonObject
            } catch (e: RuntimeException) {
                onProblem("config is not valid JSON, using defaults")
                return CbprConfig()
            }
            if (json == null) {
                if (text != null) onProblem("config is not a JSON object, using defaults")
                return CbprConfig()
            }
            val defaults = CbprConfig()
            fun volume(name: String, fallback: Double): Double {
                val element = json.get(name) ?: return fallback
                if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber || element.asDouble !in 0.0..1.0) {
                    onProblem("config \"$name\" must be a number from 0 to 1, using $fallback")
                    return fallback
                }
                return element.asDouble
            }
            fun flag(name: String, fallback: Boolean): Boolean {
                val element = json.get(name) ?: return fallback
                if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) {
                    onProblem("config \"$name\" must be true or false, using $fallback")
                    return fallback
                }
                return element.asBoolean
            }
            return CbprConfig(
                enabled = flag("enabled", defaults.enabled),
                doublesBattles = flag("doublesBattles", defaults.doublesBattles),
                triplesBattles = flag("triplesBattles", defaults.triplesBattles),
                spectatorCamera = flag("spectatorCamera", defaults.spectatorCamera),
                cinematicShots = flag("cinematicShots", defaults.cinematicShots),
                letterbox = flag("letterbox", defaults.letterbox),
                callouts = flag("callouts", defaults.callouts),
                impactStamps = flag("impactStamps", defaults.impactStamps),
                typeColour = flag("typeColour", defaults.typeColour),
                damageNumbers = flag("damageNumbers", defaults.damageNumbers),
                titleCard = flag("titleCard", defaults.titleCard),
                foeRing = flag("foeRing", defaults.foeRing),
                statusCues = flag("statusCues", defaults.statusCues),
                healthCue = flag("healthCue", defaults.healthCue),
                captureShot = flag("captureShot", defaults.captureShot),
                weatherWash = flag("weatherWash", defaults.weatherWash),
                debugLog = flag("debugLog", defaults.debugLog),
                critSpecials = flag("critSpecials", defaults.critSpecials),
                hitStop = flag("hitStop", defaults.hitStop),
                faintShot = flag("faintShot", defaults.faintShot),
                sendOutSwoop = flag("sendOutSwoop", defaults.sendOutSwoop),
                victoryOrbit = flag("victoryOrbit", defaults.victoryOrbit),
                reducedMotion = flag("reducedMotion", defaults.reducedMotion),
                cameraAvoidance = flag("cameraAvoidance", defaults.cameraAvoidance),
                occlusionFade = flag("occlusionFade", defaults.occlusionFade),
                experimentalRendering = flag("experimentalRendering", defaults.experimentalRendering),
                occlusionOutline = flag("occlusionOutline", defaults.occlusionOutline),
                menuCamera = flag("menuCamera", defaults.menuCamera),
                environmentAdaptation = flag("environmentAdaptation", defaults.environmentAdaptation),
                specialShot = flag("specialShot", defaults.specialShot),
                parallax = flag("parallax", defaults.parallax),
                groundRing = flag("groundRing", defaults.groundRing),
                sounds = flag("sounds", defaults.sounds),
                reducedNavigationSounds = flag("reducedNavigationSounds", defaults.reducedNavigationSounds),
                reducedCinematicAudio = flag("reducedCinematicAudio", defaults.reducedCinematicAudio),
                soundVolume = volume("soundVolume", defaults.soundVolume),
            )
        }
    }
}
