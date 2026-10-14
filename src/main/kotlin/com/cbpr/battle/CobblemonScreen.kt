package com.cbpr.battle

import com.cbpr.ui.BattleScreen
import com.cobblemon.mod.common.client.gui.battle.BattleGUI
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleTargetSelection
import com.cobblemon.mod.common.client.gui.battle.subscreen.ForfeitConfirmationSelection
import net.minecraft.client.Minecraft

object CobblemonScreen {

    fun selection(): Any? = (Minecraft.getInstance().screen as? BattleGUI)?.getCurrentActionSelection()

    fun of(selection: Any?): BattleScreen = when (selection) {
        null -> BattleScreen.NONE
        is BattleGeneralActionSelection -> BattleScreen.GENERAL
        is BattleMoveSelection -> BattleScreen.MOVES
        is BattleSwitchPokemonSelection -> BattleScreen.PARTY
        is BattleTargetSelection -> BattleScreen.TARGET
        is ForfeitConfirmationSelection -> BattleScreen.FORFEIT
        else -> BattleScreen.OTHER
    }
}
