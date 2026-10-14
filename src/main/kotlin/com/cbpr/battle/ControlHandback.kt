package com.cbpr.battle

import com.cbpr.ui.BattleScreen

class ControlHandback {
    var handedBack = false
        private set

    private var wasGeneral = false
    private var sawNone = false
    private var resumed = false

    fun update(screen: BattleScreen, clickedRecently: Boolean): Boolean {
        if (handedBack) {
            if (screen == BattleScreen.NONE) {
                sawNone = true
            } else if (sawNone) {
                handedBack = false
                sawNone = false
                resumed = true
            }
            wasGeneral = false
            return false
        }
        val hand = wasGeneral && screen == BattleScreen.NONE && clickedRecently
        wasGeneral = screen == BattleScreen.GENERAL
        if (hand) {
            handedBack = true
            sawNone = true
        }
        return hand
    }

    fun resume() {
        if (!handedBack) return
        handedBack = false
        sawNone = false
        resumed = true
    }

    fun consumeResumed(): Boolean = resumed.also { resumed = false }

    fun reset() {
        handedBack = false
        wasGeneral = false
        sawNone = false
        resumed = false
    }
}
