package com.cbpr.battle

import com.cbpr.camera.GimmickStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BattleEventTest {
    @Test
    fun aModifierBeforeTheDamageAppliesToThatHit() {
        val tracker = ImpactTracker()
        assertNull(tracker.modifier(BattleEvent.Crit(), 1.0))
        val flags = tracker.damage(1.2)
        assertTrue(flags.crit)
        assertFalse(flags.superEffective)
    }

    @Test
    fun aModifierJustAfterTheDamageUpgradesThatHit() {
        val tracker = ImpactTracker()
        val plain = tracker.damage(1.0)
        assertFalse(plain.strong)
        val upgraded = assertNotNull(tracker.modifier(BattleEvent.SuperEffective(), 1.2))
        assertTrue(upgraded.superEffective)
    }

    @Test
    fun aModifierIsUsedOnceAndNotCarriedToTheNextHit() {
        val tracker = ImpactTracker()
        tracker.modifier(BattleEvent.Crit(), 1.0)
        assertTrue(tracker.damage(1.1).crit)
        assertFalse(tracker.damage(1.3).crit, "a second hit must not inherit the crit")
    }

    @Test
    fun aStaleModifierIsIgnored() {
        val tracker = ImpactTracker()
        tracker.modifier(BattleEvent.Crit(), 1.0)
        assertFalse(tracker.damage(5.0).crit)
    }

    @Test
    fun aLateModifierAfterTheWindowDoesNotUpgradeAnOldHit() {
        val tracker = ImpactTracker()
        tracker.damage(1.0)
        assertNull(tracker.modifier(BattleEvent.Crit(), 3.0))
    }

    @Test
    fun bothModifiersCanApplyToOneHit() {
        val tracker = ImpactTracker()
        tracker.modifier(BattleEvent.Crit(), 1.0)
        tracker.modifier(BattleEvent.SuperEffective(), 1.1)
        val flags = tracker.damage(1.2)
        assertTrue(flags.crit && flags.superEffective)
    }

    @Test
    fun clearForgetsEverything() {
        val tracker = ImpactTracker()
        tracker.modifier(BattleEvent.Crit(), 1.0)
        tracker.damage(1.1)
        tracker.clear()
        assertFalse(tracker.damage(1.2).crit)
        assertNull(tracker.modifier(BattleEvent.Crit(), 9.0))
    }

    @Test
    fun theQueueKeepsOrderAndEmptiesWhenDrained() {
        val queue = BattleEventQueue()
        queue.post(BattleEvent.MoveUsed("A", "ember"))
        queue.post(BattleEvent.Damage(0.3))
        queue.post(BattleEvent.Crit())
        val events = queue.drain()
        assertEquals(3, events.size)
        assertTrue(events[0] is BattleEvent.MoveUsed)
        assertTrue(events[1] is BattleEvent.Damage)
        assertTrue(events[2] is BattleEvent.Crit)
        assertTrue(queue.drain().isEmpty())
    }

    @Test
    fun theQueueDropsDuplicatesRightBehindEachOther() {
        val queue = BattleEventQueue()
        queue.post(BattleEvent.MoveUsed("A", "ember"))
        queue.post(BattleEvent.MoveUsed("A", "ember"))
        queue.post(BattleEvent.Crit())
        queue.post(BattleEvent.Crit())
        queue.post(BattleEvent.SuperEffective())
        queue.post(BattleEvent.SuperEffective())
        queue.post(BattleEvent.NoEffect())
        queue.post(BattleEvent.NoEffect())
        assertEquals(4, queue.drain().size)
    }

    @Test
    fun repeatedDamageIsNeverTreatedAsADuplicateBecauseMultiHitMovesNeedEachHit() {
        val queue = BattleEventQueue()
        repeat(3) { queue.post(BattleEvent.Damage(0.1)) }
        assertEquals(3, queue.drain().size)
    }

    @Test
    fun theSameMoveByADifferentPokemonIsNotADuplicate() {
        val queue = BattleEventQueue()
        queue.post(BattleEvent.MoveUsed("A", "ember"))
        queue.post(BattleEvent.MoveUsed("B", "ember"))
        assertEquals(2, queue.drain().size)
    }

    @Test
    fun theQueueIsBoundedAndDropsTheOldestFirst() {
        val queue = BattleEventQueue(capacity = 4)
        repeat(10) { queue.post(BattleEvent.Damage(it.toDouble() + 1)) }
        val events = queue.drain()
        assertEquals(4, events.size)
        assertEquals(7.0, (events.first() as BattleEvent.Damage).lost)
        assertEquals(10.0, (events.last() as BattleEvent.Damage).lost)
    }

    @Test
    fun clearEmptiesTheQueue() {
        val queue = BattleEventQueue()
        queue.post(BattleEvent.Crit())
        queue.clear()
        assertTrue(queue.drain().isEmpty())
    }

    @Test
    fun theNewMomentsAreDeduplicatedPerSideButNotAcrossSides() {
        val queue = BattleEventQueue()
        queue.post(BattleEvent.Fainted(true))
        queue.post(BattleEvent.Fainted(true))
        queue.post(BattleEvent.Fainted(false))
        queue.post(BattleEvent.SentOut(true))
        queue.post(BattleEvent.SentOut(true))
        queue.post(BattleEvent.SentOut(false))
        queue.post(BattleEvent.Outcome("Reckshin"))
        queue.post(BattleEvent.Outcome("Reckshin"))
        assertEquals(5, queue.drain().size)
    }

    @Test
    fun twoFaintsOnTheSameSideInDoublesAreBothKeptButTheSameSlotTwiceIsOne() {
        val queue = BattleEventQueue()
        queue.post(BattleEvent.Fainted(true, "p1a"))
        queue.post(BattleEvent.Fainted(true, "p1b"))
        queue.post(BattleEvent.Fainted(true, "p1b"))
        queue.post(BattleEvent.SentOut(true, "p1a"))
        queue.post(BattleEvent.SentOut(true, "p1b"))
        assertEquals(4, queue.drain().size)
    }

    @Test
    fun aMoveOnADifferentTargetIsNotADuplicate() {
        val queue = BattleEventQueue()
        queue.post(BattleEvent.MoveUsed("Reckshin's Charmander", "tackle", "Wild Absol"))
        queue.post(BattleEvent.MoveUsed("Reckshin's Charmander", "tackle", "Wild Mew"))
        queue.post(BattleEvent.MoveUsed("Reckshin's Charmander", "tackle", "Wild Mew"))
        assertEquals(2, queue.drain().size)
    }

    @Test
    fun twoDifferentOutcomesAreBothKept() {
        val queue = BattleEventQueue()
        queue.post(BattleEvent.Outcome("Reckshin"))
        queue.post(BattleEvent.Outcome("Wild Yungoos"))
        assertEquals(2, queue.drain().size)
    }
}

class BattleEventGimmickTest {
    @Test
    fun theSameGimmickAnnouncedTwiceIsOneEventButDifferentPokemonAreBoth() {
        val queue = BattleEventQueue()
        queue.post(BattleEvent.Gimmick("Reckshin's Charizard", GimmickStyle.MEGA))
        queue.post(BattleEvent.Gimmick("Reckshin's Charizard", GimmickStyle.MEGA))
        queue.post(BattleEvent.Gimmick("Wild Absol", GimmickStyle.MEGA))
        queue.post(BattleEvent.Transformation("Wild Absol"))
        queue.post(BattleEvent.Transformation("Wild Absol"))
        assertEquals(3, queue.drain().size)
    }
}
