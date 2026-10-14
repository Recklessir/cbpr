package com.cbpr.battle

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoveProfileTest {
    private fun parse(json: String, problems: MutableList<String> = mutableListOf()) =
        MoveProfile.parse(JsonParser.parseString(json).asJsonObject) { problems += it }

    @Test
    fun fullProfileReadsEveryField() {
        val p = assertNotNull(
            parse(
                """{"id":"rush","moves":["Quick Attack"],"types":["Fighting"],"anticipation":0.2,"approach":0.4,
                "waitForImpact":1.0,"hold":0.5,"distanceScale":0.6,"heightScale":0.8,"swingDegrees":-20,
                "fovKick":7,"shake":1.1,"tilt":2,"hitStop":0.08,"callout":false,"sound":"crit"}""",
            ),
        )
        assertEquals("rush", p.id)
        assertEquals(setOf("quickattack"), p.moves)
        assertEquals(setOf("fighting"), p.types)
        assertEquals(0.2, p.anticipation)
        assertEquals(-20.0, p.swingDegrees)
        assertEquals(0.08, p.hitStop)
        assertEquals(false, p.callout)
        assertEquals("crit", p.sound)
    }

    @Test
    fun missingFieldsFallBackToTheGenericValues() {
        val p = assertNotNull(parse("""{"id":"tiny"}"""))
        assertEquals(MoveProfile.GENERIC.fovKick, p.fovKick)
        assertEquals(MoveProfile.GENERIC.hold, p.hold)
        assertTrue(p.callout)
        assertNull(p.sound)
    }

    @Test
    fun aBadFieldIsReplacedAndReportedWithoutLosingTheProfile() {
        val problems = mutableListOf<String>()
        val p = assertNotNull(parse("""{"id":"odd","fovKick":99,"shake":"lots","hold":0.5}""", problems))
        assertEquals(MoveProfile.GENERIC.fovKick, p.fovKick)
        assertEquals(MoveProfile.GENERIC.shake, p.shake)
        assertEquals(0.5, p.hold)
        assertEquals(2, problems.size)
    }

    @Test
    fun aProfileWithoutAValidIdIsRejected() {
        val problems = mutableListOf<String>()
        assertNull(parse("""{"fovKick":3}""", problems))
        assertNull(parse("""{"id":"Has Spaces"}""", problems))
        assertEquals(2, problems.size)
    }

    @Test
    fun aNonListOfMovesIsIgnoredWithAReport() {
        val problems = mutableListOf<String>()
        val p = assertNotNull(parse("""{"id":"x","moves":"ember"}""", problems))
        assertTrue(p.moves.isEmpty())
        assertEquals(1, problems.size)
    }

    @Test
    fun moveIdsMatchShowdownStyle() {
        assertEquals("firespin", MoveProfile.moveId("Fire Spin"))
        assertEquals("hyperbeam", MoveProfile.moveId("hyper-beam"))
    }
}
