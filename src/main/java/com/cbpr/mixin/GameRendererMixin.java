package com.cbpr.mixin;

import com.cbpr.battle.BattleObserver;
import com.cbpr.render.CinematicHud;
import com.cbpr.render.MenuHost;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void cbpr$hideHandDuringShot(Camera camera, float partialTick, Matrix4f projection, CallbackInfo ci) {
        if (BattleObserver.isShotActive()) {
            ci.cancel();
        }
    }

    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private double cbpr$impactFov(double fov) {
        return fov + BattleObserver.fovOffset();
    }

    @WrapOperation(
        method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V"
        )
    )
    private void cbpr$cinematicLayer(GuiGraphics graphics, Operation<Void> original) {
        original.call(graphics);
        CinematicHud.render(graphics);
        MenuHost.compose();
    }

    @WrapOperation(
        method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;renderWithTooltip(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
        )
    )
    private void cbpr$hostBattleMenu(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                     Operation<Void> original) {
        boolean hosted = MenuHost.render(screen, graphics, (offscreen, x, y) -> original.call(screen, offscreen, x, y, partialTick));
        if (!hosted) {
            original.call(screen, graphics, mouseX, mouseY, partialTick);
        }
    }
}
