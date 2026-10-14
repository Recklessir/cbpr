package com.cbpr.mixin;

import com.cbpr.render.MenuHost;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BattleMoveSelection.MoveTile.class, remap = false)
public abstract class MoveTileMixin {

    @WrapMethod(method = "render", require = 0)
    private void cbpr$relocate(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, Operation<Void> original) {
        MenuHost.relocateMove((BattleMoveSelection.MoveTile) (Object) this, graphics,
            () -> original.call(graphics, mouseX, mouseY, partialTicks));
    }
}
