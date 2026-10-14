package com.cbpr.mixin;

import com.cbpr.render.OcclusionFade;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class SodiumLevelSliceMixin {
    @Inject(method = "getBlockState(III)Lnet/minecraft/class_2680;", at = @At("HEAD"), cancellable = true, require = 0)
    private void cbpr$hideBlockAt(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (OcclusionFade.hides(x, y, z)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(method = "method_8320(Lnet/minecraft/class_2338;)Lnet/minecraft/class_2680;", at = @At("HEAD"), cancellable = true, require = 0)
    private void cbpr$hideBlock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (OcclusionFade.hides(pos.getX(), pos.getY(), pos.getZ())) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
