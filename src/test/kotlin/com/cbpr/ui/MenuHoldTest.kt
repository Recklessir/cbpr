package com.cbpr.ui

import com.cbpr.ui.BattleScreen.GENERAL
import com.cbpr.ui.BattleScreen.MOVES
import com.cbpr.ui.BattleScreen.NONE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MenuHoldTest {
    private val frame = 1.0 / 60

    @Test
    fun aOpenSubMenuIsTheMenuViewAndIsNotAHold() {
        val hold = MenuHold()
        assertTrue(hold.update(MOVES, false, frame))
        assertFalse(hold.held)
    }

    @Test
    fun afterAPickTheViewIsHeldUntilTheAttackStarts() {
        val hold = MenuHold()
        hold.update(MOVES, false, frame)
        repeat(120) {
            assertTrue(hold.update(NONE, false, frame), "still held ${it * frame} s after the pick")
            assertTrue(hold.held)
        }
        assertFalse(hold.update(NONE, true, frame), "the attack has started: the hold is over")
        assertFalse(hold.held)
    }

    @Test
    fun goingBackToTheMainMenuLetsGoAtOnce() {
        val hold = MenuHold()
        hold.update(MOVES, false, frame)
        assertFalse(hold.update(GENERAL, false, frame))
        assertFalse(hold.update(NONE, false, frame), "and it does not come back")
    }

    @Test
    fun theHoldRunsOutIfNothingHappens() {
        val hold = MenuHold()
        hold.update(MOVES, false, frame)
        var seconds = 0.0
        while (hold.update(NONE, false, frame) && seconds < 10.0) seconds += frame
        assertEquals(MenuHold.HOLD_SECONDS, seconds, 0.1)
    }

    @Test
    fun noMenuViewIsHeldWithoutAPick() {
        val hold = MenuHold()
        assertFalse(hold.update(NONE, false, frame))
        assertFalse(hold.update(GENERAL, false, frame))
    }

    @Test
    fun resetForgetsTheHold() {
        val hold = MenuHold()
        hold.update(MOVES, false, frame)
        hold.reset()
        assertFalse(hold.update(NONE, false, frame))
    }

    @Test
    fun everyStepBeyondTheMainMenuIsAMenuView() {
        for (screen in BattleScreen.entries.filter { it != NONE && it != GENERAL }) {
            assertTrue(MenuHold().update(screen, false, frame), "$screen")
        }
    }
}
