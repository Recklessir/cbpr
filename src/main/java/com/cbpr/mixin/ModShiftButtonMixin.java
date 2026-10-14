package com.cbpr.mixin;

import com.cbpr.render.MenuHost;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.cobblemon.battle.button.BattleShiftButton", remap = false)
public abstract class ModShiftButtonMixin {
    @WrapMethod(method = "render", require = 0)
    private void cbpr$relocate(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, Operation<Void> original) {
        MenuHost.drawShiftInstead(this, graphics, mouseX, mouseY, partialTicks, () -> original.call(graphics, mouseX, mouseY, partialTicks));
    }
}
