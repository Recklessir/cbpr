package com.cbpr.mixin;

import com.cbpr.render.MenuHost;
import com.cobblemon.mod.common.client.gui.battle.widgets.BattleOptionTile;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BattleOptionTile.class)
public abstract class BattleOptionTileMixin {

    @WrapMethod(method = "render", require = 0)
    private void cbpr$relocate(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, Operation<Void> original) {
        MenuHost.relocateOption((BattleOptionTile) (Object) this, graphics,
            () -> original.call(graphics, mouseX, mouseY, partialTicks));
    }
}
