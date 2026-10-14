package com.cbpr.ui

import com.cbpr.config.CbprConfig

class Toggle(val key: String, val get: (CbprConfig) -> Boolean, val set: (CbprConfig, Boolean) -> CbprConfig)

sealed interface Entry {
    class Switch(val toggle: Toggle) : Entry

    data object Volume : Entry
}

class Tab(val id: String, val entries: List<Entry>) {
    val switches: List<Entry.Switch> get() = entries.filterIsInstance<Entry.Switch>()
}

object SettingsModel {
    private fun sw(key: String, get: (CbprConfig) -> Boolean, set: (CbprConfig, Boolean) -> CbprConfig) = Entry.Switch(Toggle(key, get, set))

    val master = Toggle("enabled", { it.enabled }) { c, v -> c.copy(enabled = v) }

    val tabs: List<Tab> = listOf(
        Tab(
            "camera",
            listOf(
                sw("doublesBattles", { it.doublesBattles }) { c, v -> c.copy(doublesBattles = v) },
                sw("triplesBattles", { it.triplesBattles }) { c, v -> c.copy(triplesBattles = v) },
                sw("spectatorCamera", { it.spectatorCamera }) { c, v -> c.copy(spectatorCamera = v) },
                sw("cinematicShots", { it.cinematicShots }) { c, v -> c.copy(cinematicShots = v) },
                sw("letterbox", { it.letterbox }) { c, v -> c.copy(letterbox = v) },
                sw("hitStop", { it.hitStop }) { c, v -> c.copy(hitStop = v) },
                sw("critSpecials", { it.critSpecials }) { c, v -> c.copy(critSpecials = v) },
                sw("faintShot", { it.faintShot }) { c, v -> c.copy(faintShot = v) },
                sw("sendOutSwoop", { it.sendOutSwoop }) { c, v -> c.copy(sendOutSwoop = v) },
                sw("victoryOrbit", { it.victoryOrbit }) { c, v -> c.copy(victoryOrbit = v) },
                sw("specialShot", { it.specialShot }) { c, v -> c.copy(specialShot = v) },
                sw("captureShot", { it.captureShot }) { c, v -> c.copy(captureShot = v) },
                sw("menuCamera", { it.menuCamera }) { c, v -> c.copy(menuCamera = v) },
                sw("parallax", { it.parallax }) { c, v -> c.copy(parallax = v) },
                sw("occlusionFade", { it.occlusionFade }) { c, v -> c.copy(occlusionFade = v) },
                sw("cameraAvoidance", { it.cameraAvoidance }) { c, v -> c.copy(cameraAvoidance = v) },
                sw("environmentAdaptation", { it.environmentAdaptation }) { c, v -> c.copy(environmentAdaptation = v) },
            ),
        ),
        Tab(
            "interface",
            listOf(
                sw("callouts", { it.callouts }) { c, v -> c.copy(callouts = v) },
                sw("impactStamps", { it.impactStamps }) { c, v -> c.copy(impactStamps = v) },
                sw("typeColour", { it.typeColour }) { c, v -> c.copy(typeColour = v) },
                sw("damageNumbers", { it.damageNumbers }) { c, v -> c.copy(damageNumbers = v) },
                sw("titleCard", { it.titleCard }) { c, v -> c.copy(titleCard = v) },
                sw("statusCues", { it.statusCues }) { c, v -> c.copy(statusCues = v) },
                sw("groundRing", { it.groundRing }) { c, v -> c.copy(groundRing = v) },
                sw("foeRing", { it.foeRing }) { c, v -> c.copy(foeRing = v) },
                sw("healthCue", { it.healthCue }) { c, v -> c.copy(healthCue = v) },
                sw("weatherWash", { it.weatherWash }) { c, v -> c.copy(weatherWash = v) },
                sw("reducedMotion", { it.reducedMotion }) { c, v -> c.copy(reducedMotion = v) },
            ),
        ),
        Tab(
            "audio",
            listOf(
                sw("sounds", { it.sounds }) { c, v -> c.copy(sounds = v) },
                sw("reducedNavigationSounds", { it.reducedNavigationSounds }) { c, v -> c.copy(reducedNavigationSounds = v) },
                sw("reducedCinematicAudio", { it.reducedCinematicAudio }) { c, v -> c.copy(reducedCinematicAudio = v) },
                Entry.Volume,
            ),
        ),
    )

    val switchKeys: List<String> get() = listOf(master.key) + tabs.flatMap { tab -> tab.switches.map { it.toggle.key } }
    const val VOLUME_KEY = "soundVolume"

    fun reset(config: CbprConfig, tab: Tab): CbprConfig {
        val defaults = CbprConfig()
        var next = config
        for (entry in tab.entries) {
            when (entry) {
                is Entry.Switch -> next = entry.toggle.set(next, entry.toggle.get(defaults))
                Entry.Volume -> next = next.copy(soundVolume = defaults.soundVolume)
            }
        }
        return next
    }
}
