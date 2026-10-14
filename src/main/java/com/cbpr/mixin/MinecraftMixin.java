package com.cbpr.mixin;

import com.cbpr.battle.BattleObserver;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @ModifyReturnValue(method = "shouldEntityAppearGlowing", at = @At("RETURN"))
    private boolean cbpr$outlineThroughTerrain(boolean glowing, Entity entity) {
        return glowing || BattleObserver.glowOutline(entity);
    }
}
