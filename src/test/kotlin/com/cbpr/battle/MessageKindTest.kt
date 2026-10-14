package com.cbpr.battle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageKindDynamaxTest {
    @Test
    fun dynamaxAndGigantamaxStartMessagesAreRecognised() {
        assertEquals(MessageKind.DYNAMAX, MessageKind.of("cobblemon.battle.start.dynamax"))
        assertEquals(MessageKind.DYNAMAX, MessageKind.of("cobblemon.battle.start.gmax"))
        assertNull(MessageKind.of("cobblemon.battle.end.dynamax"))
    }
}

class MessageKindTest {
    @Test
    fun cobblemonsOwnKeysAreRecognised() {
        assertEquals(MessageKind.MOVE_USED, MessageKind.of("cobblemon.battle.used_move"))
        assertEquals(MessageKind.MOVE_USED, MessageKind.of("cobblemon.battle.used_move_on"))
        assertEquals(MessageKind.CRIT, MessageKind.of("cobblemon.battle.crit"))
        assertEquals(MessageKind.CRIT, MessageKind.of("cobblemon.battle.crit_spread"))
        assertEquals(MessageKind.SUPER_EFFECTIVE, MessageKind.of("cobblemon.battle.superEffective"))
        assertEquals(MessageKind.SUPER_EFFECTIVE, MessageKind.of("cobblemon.battle.superEffective_spread"))
        assertEquals(MessageKind.NO_EFFECT, MessageKind.of("cobblemon.battle.missed"))
        assertEquals(MessageKind.NO_EFFECT, MessageKind.of("cobblemon.battle.immune"))
        assertEquals(MessageKind.WIN, MessageKind.of("cobblemon.battle.win"))
    }

    @Test
    fun resistedHitsCountAsNotVeryEffective() {
        assertEquals(MessageKind.NO_EFFECT, MessageKind.of("cobblemon.battle.resisted"))
        assertEquals(MessageKind.NO_EFFECT, MessageKind.of("cobblemon.battle.resisted_spread"))
    }

    @Test
    fun megaShowdownsMessagesAreRecognisedInAnyCapitalisation() {
        assertEquals(MessageKind.SUPER_EFFECTIVE, MessageKind.of("cobblemon.battle.extremelyEffective"))
        assertEquals(MessageKind.SUPER_EFFECTIVE, MessageKind.of("cobblemon.battle.extremelyeffective"))
        assertEquals(MessageKind.SUPER_EFFECTIVE, MessageKind.of("cobblemon.battle.extremelyEffective_spread"))
        assertEquals(MessageKind.NO_EFFECT, MessageKind.of("cobblemon.battle.mostlyIneffective"))
        assertEquals(MessageKind.NO_EFFECT, MessageKind.of("cobblemon.battle.mostlyineffective"))
    }

    @Test
    fun gimmickMessagesAreSpecialAndFormChangesAreTransformations() {
        assertEquals(MessageKind.MEGA, MessageKind.of("cobblemon.battle.mega"))
        assertEquals(MessageKind.TERA, MessageKind.of("cobblemon.battle.terastallize"))
        assertEquals(MessageKind.ZPOWER, MessageKind.of("cobblemon.battle.zpower"))
        assertEquals(MessageKind.ULTRA, MessageKind.of("cobblemon.battle.ultra"))
        assertEquals(MessageKind.TRANSFORMATION, MessageKind.of("cobblemon.battle.transform"))
        assertEquals(MessageKind.TRANSFORMATION, MessageKind.of("cobblemon.battle.formechange.mega"))
        assertEquals(MessageKind.TRANSFORMATION, MessageKind.of("cobblemon.battle.formechange.default.temporary"))
    }

    @Test
    fun unrelatedKeysAreIgnored() {
        assertNull(MessageKind.of("cobblemon.battle.weather.desolateland.start"))
        assertNull(MessageKind.of("cobblemon.battle.megaphone"))
        assertNull(MessageKind.of("minecraft.crit"))
        assertNull(MessageKind.of(""))
    }
}

class StatStagesTest {
    @Test
    fun boostAndUnboostKeysGiveTheirStepsAndCapsGiveNone() {
        assertEquals(1, StatStages.of("cobblemon.battle.boost.slight"))
        assertEquals(2, StatStages.of("cobblemon.battle.boost.sharp"))
        assertEquals(3, StatStages.of("cobblemon.battle.boost.severe"))
        assertEquals(-1, StatStages.of("cobblemon.battle.unboost.slight"))
        assertEquals(-2, StatStages.of("cobblemon.battle.unboost.sharp"))
        assertEquals(-3, StatStages.of("cobblemon.battle.unboost.severe"))
        assertEquals(2, StatStages.of("cobblemon.battle.boost.sharp.zeffect"))
        assertNull(StatStages.of("cobblemon.battle.boost.cap.single"))
        assertNull(StatStages.of("cobblemon.battle.unboost.cap.multiple"))
        assertNull(StatStages.of("cobblemon.battle.crit"))
        assertNull(StatStages.of("minecraft.boost.sharp"))
    }

    @Test
    fun theMessageKindSeesAStatChange() {
        assertEquals(MessageKind.STAT_CHANGE, MessageKind.of("cobblemon.battle.unboost.sharp"))
        assertNull(MessageKind.of("cobblemon.battle.boost.cap.single"))
    }

    @Test
    fun theLabelCarriesTheSignAndTheNumber() {
        assertEquals("Attack +2", StatStages.label("Attack", 2))
        assertEquals("Speed -1", StatStages.label("Speed", -1))
        assertEquals(StatStages.UP_RGB, StatStages.rgb(1))
        assertEquals(StatStages.DOWN_RGB, StatStages.rgb(-3))
    }
}
