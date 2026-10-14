package com.cbpr.mixin;

import com.cbpr.render.MenuHost;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BattleSwitchPokemonSelection.SwitchTile.class, remap = false)
public abstract class SwitchTileMixin {

    @WrapMethod(method = "render", require = 0)
    private void cbpr$relocate(GuiGraphics graphics, double mouseX, double mouseY, float partialTicks, Operation<Void> original) {
        MenuHost.relocateSwitch((BattleSwitchPokemonSelection.SwitchTile) (Object) this, graphics,
            () -> original.call(graphics, mouseX, mouseY, partialTicks));
    }
}
