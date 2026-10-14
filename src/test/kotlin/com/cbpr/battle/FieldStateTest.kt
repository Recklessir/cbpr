package com.cbpr.battle

import com.cbpr.ui.WashMotion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FieldStateTest {
    @Test
    fun weatherAndTerrainKeysGiveTheirChangeAndOtherKeysNone() {
        assertEquals("raindance" to true, FieldKeys.of("cobblemon.battle.weather.raindance.start")!!.let { it.id to it.started })
        assertEquals("sandstorm" to false, FieldKeys.of("cobblemon.battle.weather.sandstorm.end")!!.let { it.id to it.started })
        assertEquals("grassyterrain" to true, FieldKeys.of("cobblemon.battle.fieldstart.grassyterrain")!!.let { it.id to it.started })
        assertEquals("psychicterrain" to false, FieldKeys.of("cobblemon.battle.fieldend.psychicterrain")!!.let { it.id to it.started })
        assertNull(FieldKeys.of("cobblemon.battle.weather.raindance.upkeep"))
        assertNull(FieldKeys.of("cobblemon.battle.fieldstart.gravity"))
        assertNull(FieldKeys.of("cobblemon.battle.fieldstart.trickroom"))
        assertNull(FieldKeys.of("cobblemon.battle.weather.fog.start"))
        assertNull(FieldKeys.of("cobblemon.battle.crit"))
        assertEquals(MessageKind.FIELD_CHANGE, MessageKind.of("cobblemon.battle.weather.hail.start"))
        assertNull(MessageKind.of("cobblemon.battle.fieldstart.gravity"))
    }

    @Test
    fun theNewestFieldShowsAndTheOtherReturnsWhenItEnds() {
        val state = FieldState()
        assertNull(state.current)
        state.apply("raindance", true)
        assertEquals("raindance", state.current)
        state.apply("electricterrain", true)
        assertEquals("electricterrain", state.current)
        state.apply("electricterrain", false)
        assertEquals("raindance", state.current)
        state.apply("sunnyday", true)
        assertEquals("sunnyday", state.current, "weather replaces weather")
        state.apply("sunnyday", false)
        assertNull(state.current)
        state.apply("mistyterrain", true)
        state.apply("hail", true)
        assertEquals("hail", state.current)
        state.apply("hail", false)
        assertEquals("mistyterrain", state.current)
        state.clear()
        assertNull(state.current)
    }

    @Test
    fun everyFieldHasAWashColour() {
        for (id in FieldKeys.WEATHER + FieldKeys.TERRAIN) assertNotNull(FieldWash.rgb(id), id)
        assertNull(FieldWash.rgb("gravity"))
    }
}

class WashMotionTest {
    private val frame = 1.0 / 60

    private fun run(wash: WashMotion, seconds: Double, wanted: Int?) = repeat((seconds / frame).toInt()) { wash.step(frame, wanted) }

    @Test
    fun theWashEasesInAndOut() {
        val wash = WashMotion()
        assertEquals(0.0, wash.amount)
        run(wash, 0.2, 0x3A6FD8)
        assertTrue(wash.amount in 0.05..0.95, "mid way ${wash.amount}")
        run(wash, 3.0, 0x3A6FD8)
        assertEquals(1.0, wash.amount, 0.02)
        assertEquals(0x3A6FD8, wash.rgb)
        run(wash, 3.0, null)
        assertEquals(0.0, wash.amount, 0.02)
    }

    @Test
    fun oneFieldReplacingAnotherChangesColourGraduallyAndNothingShowingTakesTheNewColourAtOnce() {
        val wash = WashMotion()
        run(wash, 0.5, 0xFF0000)
        run(wash, 3.0, 0xFF0000)
        run(wash, 0.05, 0x0000FF)
        assertTrue(wash.rgb != 0xFF0000 && wash.rgb != 0x0000FF, "blending ${wash.rgb.toString(16)}")
        run(wash, 2.0, 0x0000FF)
        assertEquals(0x0000FF, wash.rgb)
        val fresh = WashMotion()
        run(fresh, frame, 0x00FF00)
        assertEquals(0x00FF00, fresh.rgb)
    }

    @Test
    fun resetClearsIt() {
        val wash = WashMotion()
        run(wash, 3.0, 0x3A6FD8)
        wash.reset()
        assertEquals(0.0, wash.amount)
    }
}

class VignetteTest {
    @Test
    fun theColourStartsAtNothingAndGrowsSmoothlyToFullAtTheOuterRing() {
        assertEquals(0.0, com.cbpr.ui.Vignette.strength(0.0))
        assertEquals(1.0, com.cbpr.ui.Vignette.strength(1.0))
        var previous = -1.0
        for (ring in 0..com.cbpr.ui.Vignette.RINGS) {
            val value = com.cbpr.ui.Vignette.strength(ring.toDouble() / com.cbpr.ui.Vignette.RINGS)
            assertTrue(value >= previous, "ring $ring")
            previous = value
        }
        assertTrue(com.cbpr.ui.Vignette.strength(0.1) < 0.1, "eased, no hard edge where it starts")
    }

    @Test
    fun theRingsLeaveTheMiddleClearAndReachPastTheCorners() {
        assertTrue(com.cbpr.ui.Vignette.radius(0) in 0.3..0.8)
        assertTrue(com.cbpr.ui.Vignette.radius(com.cbpr.ui.Vignette.RINGS) > kotlin.math.sqrt(2.0))
    }
}
