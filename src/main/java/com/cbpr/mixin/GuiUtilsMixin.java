package com.cbpr.mixin;

import com.cbpr.render.MenuHost;
import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiUtilsKt.class, remap = false)
public abstract class GuiUtilsMixin {

    @Inject(
        method = "blitk(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;ZF)V",
        at = @At("HEAD"), cancellable = true, require = 0
    )
    private static void cbpr$skipPartyPanel(PoseStack pose, ResourceLocation texture, Number x, Number y, Number width, Number height,
                                            Number uOffset, Number vOffset, Number textureWidth, Number textureHeight,
                                            Number textureWidthFull, Number textureHeightFull, Number rotation, Number extra1, Number extra2, boolean blend,
                                            float alpha, CallbackInfo ci) {
        if (MenuHost.skipsPartyPanel(texture)) {
            ci.cancel();
        }
    }
}
