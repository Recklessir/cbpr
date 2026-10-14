package com.cbpr.mixin;

import com.cbpr.render.MenuHost;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.client.battle.hud.MovePreviewWidget", remap = false)
public abstract class MovePreviewMixin {
    @WrapMethod(method = {"method_48579", "renderWidget"}, require = 0)
    private void cbpr$hideWhileFloating(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, Operation<Void> original) {
        if (!MenuHost.hidesExternalPreview()) {
            original.call(graphics, mouseX, mouseY, partialTicks);
        }
    }
}
