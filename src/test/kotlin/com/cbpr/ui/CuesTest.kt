package com.cbpr.ui

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CuesTest {
    @Test
    fun mixingGoesFromOneColourToTheOther() {
        assertEquals(0xFF0000, Rgb.mix(0xFF0000, 0x0000FF, 0.0))
        assertEquals(0x0000FF, Rgb.mix(0xFF0000, 0x0000FF, 1.0))
        assertEquals(0x800080, Rgb.mix(0xFF0000, 0x0000FF, 0.5))
        assertEquals(0x0000FF, Rgb.mix(0xFF0000, 0x0000FF, 5.0), "t is held to 0 to 1")
    }

    @Test
    fun everyCobblemonPersistentStatusHasAColourAndOthersHaveNone() {
        for (path in listOf("burn", "frozen", "paralysis", "poison", "poisonbadly", "sleep")) assertTrue(StatusCue.rgb(path) != null, path)
        assertNull(StatusCue.rgb("somemodstatus"))
        assertEquals("cbpr.status.burn", StatusCue.langKey("burn"))
    }

    @Test
    fun aStatusOnlyAnnouncesWhenItIsNewToAPokemonAlreadySeen() {
        val watch = StatusWatch()
        val a = UUID.randomUUID()
        assertNull(watch.update(a, "burn"), "first sighting only remembers the status")
        assertNull(watch.update(a, "burn"), "no change")
        assertNull(watch.update(a, null), "being cured is not announced")
        assertEquals("sleep", watch.update(a, "sleep"))
        assertNull(watch.update(a, "sleep"))
        assertEquals("poison", watch.update(a, "poison"), "a different status is announced")
    }

    @Test
    fun eachPokemonHasItsOwnHistoryAndClearForgetsThem() {
        val watch = StatusWatch()
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        watch.update(a, null)
        assertNull(watch.update(b, "burn"))
        assertEquals("burn", watch.update(a, "burn"))
        watch.clear()
        assertNull(watch.update(a, "sleep"), "after clear it is a first sighting again")
    }
}

class RingDangerTest {
    private val frame = 1.0 / 60

    private fun run(ring: RingMotion, seconds: Double, low: Boolean, reduced: Boolean = false) {
        repeat((seconds / frame).toInt()) { ring.step(frame, false, reduced, low) }
    }

    @Test
    fun lowHealthMakesTheRingBrighterAndTurnFasterAndItRelaxesAgain() {
        val calm = RingMotion()
        val alarmed = RingMotion()
        run(calm, 2.0, low = false)
        run(alarmed, 2.0, low = true)
        assertEquals(1.0, alarmed.danger, 0.02)
        assertTrue(alarmed.alpha > calm.alpha + 0.2, "alpha ${alarmed.alpha} vs ${calm.alpha}")
        assertTrue(alarmed.spin != calm.spin)
        run(alarmed, 3.0, low = false)
        assertEquals(0.0, alarmed.danger, 0.02)
    }

    @Test
    fun reducedMotionBrightensButDoesNotBreatheOrTurn() {
        val ring = RingMotion()
        run(ring, 2.0, low = true, reduced = true)
        assertEquals(0.0, ring.spin)
        assertEquals(1.0, ring.radiusScale, 1e-9)
        assertTrue(ring.alpha > RingMotion.BASE_ALPHA + 0.2)
    }
}
