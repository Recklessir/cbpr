package com.cbpr.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CbprConfigTest {
    private fun parse(text: String?, problems: MutableList<String> = mutableListOf()) = CbprConfig.parse(text) { problems += it }

    @Test
    fun absentFileGivesDefaultsWithoutAProblem() {
        val problems = mutableListOf<String>()
        val c = parse(null, problems)
        assertTrue(c.cinematicShots && c.letterbox && c.callouts && c.critSpecials)
        assertFalse(c.hitStop || c.sendOutSwoop, "the defaults are CBPR Balanced")
        assertFalse(c.reducedMotion)
        assertTrue(c.cameraAvoidance)
        assertTrue(c.occlusionFade)
        assertTrue(c.menuCamera)
        assertTrue(c.parallax && c.groundRing)
        assertTrue(c.specialShot)
        assertTrue(c.doublesBattles, "doubles are on by default")
        assertTrue(c.triplesBattles, "triples are on by default")
        assertTrue(c.spectatorCamera, "spectated battles are on by default")
        assertTrue(c.sounds)
        assertTrue(c.environmentAdaptation)
        assertFalse(c.debugLog)
        assertTrue(c.impactStamps && c.typeColour && c.damageNumbers && c.titleCard && c.foeRing && c.statusCues && c.healthCue && c.captureShot && c.weatherWash)
        assertEquals(1.0, c.soundVolume)
        assertFalse(c.experimentalRendering || c.occlusionOutline)
        assertTrue(problems.isEmpty())
    }

    @Test
    fun validValuesAreRead() {
        val c = parse("""{"letterbox":false,"reducedMotion":true,"callouts":false,"impactStamps":false,"typeColour":false,"damageNumbers":false,"titleCard":false,"foeRing":false,"statusCues":false,"healthCue":false,"captureShot":false,"weatherWash":false}""")
        assertFalse(c.impactStamps || c.typeColour || c.damageNumbers || c.titleCard || c.foeRing || c.statusCues || c.healthCue || c.captureShot || c.weatherWash)
        assertFalse(c.letterbox)
        assertFalse(c.callouts)
        assertTrue(c.reducedMotion)
        assertTrue(c.cinematicShots)
    }

    @Test
    fun wrongTypesAreReplacedByTheirDefaultsAndReported() {
        val problems = mutableListOf<String>()
        val c = parse("""{"letterbox":"no","hitStop":3,"reducedMotion":true}""", problems)
        assertTrue(c.letterbox)
        assertFalse(c.hitStop)
        assertTrue(c.reducedMotion)
        assertEquals(2, problems.size)
    }

    @Test
    fun brokenJsonFallsBackToDefaults() {
        val problems = mutableListOf<String>()
        val c = parse("{ not json", problems)
        assertTrue(c.cinematicShots)
        assertEquals(1, problems.size)
    }

    @Test
    fun aJsonArrayInsteadOfAnObjectFallsBackToDefaults() {
        val problems = mutableListOf<String>()
        parse("[1,2,3]", problems)
        assertEquals(1, problems.size)
    }

    @Test
    fun unknownKeysAreIgnored() {
        val problems = mutableListOf<String>()
        parse("""{"somethingNew":1}""", problems)
        assertTrue(problems.isEmpty())
    }

    @Test
    fun writtenConfigReadsBackIdentically() {
        val original = CbprConfig(
            cinematicShots = false, letterbox = false, callouts = true, critSpecials = false, hitStop = false,
            faintShot = false, sendOutSwoop = false, victoryOrbit = false, reducedMotion = true, cameraAvoidance = false, occlusionFade = false, experimentalRendering = true, occlusionOutline = true, menuCamera = false, environmentAdaptation = false, parallax = false, groundRing = false, specialShot = false, sounds = false, reducedNavigationSounds = true, reducedCinematicAudio = true, soundVolume = 0.25,
        )
        val back = parse(original.toJson())
        assertEquals(original.cinematicShots, back.cinematicShots)
        assertEquals(original.letterbox, back.letterbox)
        assertEquals(original.callouts, back.callouts)
        assertEquals(original.critSpecials, back.critSpecials)
        assertEquals(original.hitStop, back.hitStop)
        assertEquals(original.reducedMotion, back.reducedMotion)
        assertEquals(original.faintShot, back.faintShot)
        assertEquals(original.sendOutSwoop, back.sendOutSwoop)
        assertEquals(original.victoryOrbit, back.victoryOrbit)
        assertEquals(original.cameraAvoidance, back.cameraAvoidance)
        assertEquals(original.occlusionFade, back.occlusionFade)
        assertEquals(original.experimentalRendering, back.experimentalRendering)
        assertEquals(original.occlusionOutline, back.occlusionOutline)
        assertEquals(original.menuCamera, back.menuCamera)
        assertEquals(original.sounds, back.sounds)
        assertEquals(original.environmentAdaptation, back.environmentAdaptation)
        assertEquals(original.parallax, back.parallax)
        assertEquals(original.groundRing, back.groundRing)
        assertEquals(original.specialShot, back.specialShot)
        assertEquals(original.reducedNavigationSounds, back.reducedNavigationSounds)
        assertEquals(original.reducedCinematicAudio, back.reducedCinematicAudio)
        assertEquals(original.soundVolume, back.soundVolume)
    }
}

class CbprConfigVolumeTest {
    @Test
    fun aVolumeOutsideZeroToOneOrNotANumberFallsBackAndIsReported() {
        val problems = mutableListOf<String>()
        val high = CbprConfig.parse("""{"soundVolume":3}""") { problems += it }
        val text = CbprConfig.parse("""{"soundVolume":"loud"}""") { problems += it }
        val ok = CbprConfig.parse("""{"soundVolume":0.5}""") { problems += it }
        assertEquals(1.0, high.soundVolume)
        assertEquals(1.0, text.soundVolume)
        assertEquals(0.5, ok.soundVolume)
        assertEquals(2, problems.size)
    }
}
