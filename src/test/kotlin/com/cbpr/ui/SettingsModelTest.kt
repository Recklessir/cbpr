package com.cbpr.ui

import com.cbpr.config.CbprConfig
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsModelTest {
    private val lang = JsonParser.parseString(checkNotNull(javaClass.getResourceAsStream("/assets/cbpr/lang/en_us.json")).reader().readText()).asJsonObject

    private val longDashes = listOf('—', '–')

    @Test
    fun everyConfigKeyIsOnExactlyOneTabExceptTheHiddenOnes() {
        val keys = JsonParser.parseString(CbprConfig().toJson()).asJsonObject.keySet()
        val shown = SettingsModel.switchKeys + SettingsModel.VOLUME_KEY
        assertEquals(shown.size, shown.toSet().size, "a key sits on two tabs")
        assertEquals(keys - setOf("experimentalRendering", "occlusionOutline", "debugLog"), shown.toSet())
    }

    @Test
    fun everySettingHasALabel() {
        for (key in SettingsModel.switchKeys + SettingsModel.VOLUME_KEY) assertTrue(lang.has("cbpr.config.$key"), "label for $key")
    }

    @Test
    fun noSettingHasATooltipText() {
        assertFalse(lang.keySet().any { it.startsWith("cbpr.tip.") || it.startsWith("cbpr.presetdesc.") })
    }

    @Test
    fun tabsHaveTheirWordsAndThereAreNoPresets() {
        for (tab in SettingsModel.tabs) {
            assertTrue(lang.has("cbpr.tab.${tab.id}"), tab.id)
        }
        assertFalse(lang.keySet().any { it.startsWith("cbpr.preset.") })
    }

    @Test
    fun noWordInTheLanguageFileUsesALongDash() {
        for ((key, value) in lang.entrySet()) assertFalse(longDashes.any { it in value.asString }, key)
    }

    @Test
    fun theSwitchesReadAndChangeTheirKey() {
        val toggle = SettingsModel.tabs.flatMap { it.switches }.first { it.toggle.key == "letterbox" }.toggle
        val on = CbprConfig(letterbox = true)
        assertTrue(toggle.get(on))
        val off = toggle.set(on, false)
        assertFalse(toggle.get(off))
        assertEquals(on.copy(letterbox = false), off)
    }

    @Test
    fun resettingATabLeavesTheOthersAlone() {
        val changed = CbprConfig(letterbox = false, hitStop = true, faintShot = false, callouts = false, soundVolume = 0.2, sounds = false)
        val camera = SettingsModel.tabs.first { it.id == "camera" }
        val after = SettingsModel.reset(changed, camera)
        assertTrue(after.letterbox && after.faintShot, "the camera tab is back to its defaults")
        assertFalse(after.hitStop, "and the default has no hit pause")
        assertFalse(after.callouts, "the interface tab is not touched")
        assertEquals(0.2, after.soundVolume)
        val audio = SettingsModel.tabs.first { it.id == "audio" }
        val audioAfter = SettingsModel.reset(changed, audio)
        assertEquals(1.0, audioAfter.soundVolume)
        assertTrue(audioAfter.sounds)
        assertFalse(audioAfter.letterbox)
    }

    @Test
    fun theMasterSwitchIsNotOnATab() {
        assertEquals("enabled", SettingsModel.master.key)
        assertFalse(SettingsModel.tabs.any { tab -> tab.switches.any { it.toggle.key == "enabled" } })
        assertTrue("enabled" in SettingsModel.switchKeys)
    }
}
