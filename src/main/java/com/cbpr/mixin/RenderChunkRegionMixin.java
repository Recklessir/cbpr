package com.cbpr.mixin;

import com.cbpr.render.OcclusionFade;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderChunkRegion.class)
public abstract class RenderChunkRegionMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void cbpr$hideBlock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (OcclusionFade.hides(pos.getX(), pos.getY(), pos.getZ())) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
