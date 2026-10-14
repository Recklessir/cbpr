package com.cbpr.mixin;

import com.cbpr.battle.BattleObserver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "isBodyVisible", at = @At("HEAD"), cancellable = true)
    private void cbpr$translucentFade(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (BattleObserver.isTranslucent(entity)) {
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"
        )
    )
    private void cbpr$fadeAlpha(EntityModel<?> model, PoseStack pose, VertexConsumer buffer, int light, int overlay,
                                 int color, Operation<Void> original, @Local(argsOnly = true) LivingEntity entity) {
        original.call(model, pose, buffer, light, overlay, BattleObserver.renderColor(entity, color));
    }
}
