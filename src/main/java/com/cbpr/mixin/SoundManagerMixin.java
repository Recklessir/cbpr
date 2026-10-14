package com.cbpr.mixin;

import com.cbpr.render.MenuHost;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void cbpr$dropDoubledClick(SoundInstance sound, CallbackInfo ci) {
        if (MenuHost.dropsClickSound(sound.getLocation())) {
            ci.cancel();
        }
    }
}
