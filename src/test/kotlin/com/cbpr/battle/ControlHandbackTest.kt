package com.cbpr.battle

import com.cbpr.ui.BattleScreen
import com.cbpr.ui.BattleScreen.GENERAL
import com.cbpr.ui.BattleScreen.NONE
import com.cbpr.ui.BattleScreen.MOVES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlHandbackTest {
    @Test
    fun clickingAMainMenuButtonThatClosesTheMenuHandsTheViewBack() {
        val handback = ControlHandback()
        assertFalse(handback.update(GENERAL, false))
        assertTrue(handback.update(NONE, clickedRecently = true), "Catch closes the screen, Run sends the flee")
        assertTrue(handback.handedBack)
    }

    @Test
    fun fightAndSwitchOpenASubMenuAndAreNotAHandback() {
        val handback = ControlHandback()
        handback.update(GENERAL, false)
        assertFalse(handback.update(MOVES, clickedRecently = true))
        assertFalse(handback.handedBack)

        assertFalse(handback.update(NONE, clickedRecently = true))
        assertFalse(handback.handedBack)
    }

    @Test
    fun aMenuThatClosesWithoutAClickIsNotAHandback() {
        val handback = ControlHandback()
        handback.update(GENERAL, false)
        assertFalse(handback.update(NONE, clickedRecently = false), "a timeout or the battle ending")
    }

    @Test
    fun theViewStaysWithTheTrainerUntilANewMenuAppearsAfterTheOldOneWasGone() {
        val handback = ControlHandback()
        handback.update(GENERAL, false)
        handback.update(NONE, true)
        repeat(20) {
            assertFalse(handback.update(NONE, false))
            assertTrue(handback.handedBack)
        }
        assertFalse(handback.update(GENERAL, false))
        assertFalse(handback.handedBack, "the next action menu brings the camera back")
        assertTrue(handback.consumeResumed())
        assertFalse(handback.consumeResumed(), "once")
    }

    @Test
    fun aMenuStillUpRightAfterTheClickDoesNotResumeAtOnce() {

        val handback = ControlHandback()
        handback.update(GENERAL, false)
        handback.update(NONE, true)
        assertTrue(handback.handedBack)
        handback.update(NONE, false)
        assertTrue(handback.handedBack)
    }

    @Test
    fun aCaptureThatLeavesTheBattleGoingBringsTheCameraBackWithoutWaitingForTheMenu() {
        val handback = ControlHandback()
        handback.update(GENERAL, false)
        handback.update(NONE, true)
        assertTrue(handback.handedBack)
        handback.resume()
        assertFalse(handback.handedBack)
        assertTrue(handback.consumeResumed(), "framing again is a resume: no title card")

        assertFalse(handback.update(GENERAL, false))
        assertFalse(handback.consumeResumed())
    }

    @Test
    fun resumeDoesNothingWhenTheViewWasNeverHandedBack() {
        val handback = ControlHandback()
        handback.update(GENERAL, false)
        handback.resume()
        assertFalse(handback.consumeResumed())
    }

    @Test
    fun resetForgetsEverything() {
        val handback = ControlHandback()
        handback.update(GENERAL, false)
        handback.update(NONE, true)
        handback.reset()
        assertFalse(handback.handedBack)
        assertEquals(false, handback.consumeResumed())
    }

    @Test
    fun theTargetStepAndForfeitAreNotCatchOrRun() {
        for (screen in listOf(BattleScreen.TARGET, BattleScreen.FORFEIT)) {
            val handback = ControlHandback()
            handback.update(GENERAL, false)
            handback.update(screen, clickedRecently = true)
            assertFalse(handback.update(NONE, clickedRecently = true), "$screen")
        }
    }
}
