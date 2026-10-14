package com.cbpr.mixin;

import com.cbpr.render.MenuHost;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGimmickButton;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BattleGimmickButton.class, remap = false)
public abstract class BattleGimmickButtonMixin {

    @WrapMethod(method = "render", require = 0)
    private void cbpr$relocate(PoseStack pose, int mouseX, int mouseY, float partialTicks, Operation<Void> original) {
        MenuHost.relocateGimmick((BattleGimmickButton) (Object) this, pose, () -> original.call(pose, mouseX, mouseY, partialTicks));
    }
}
