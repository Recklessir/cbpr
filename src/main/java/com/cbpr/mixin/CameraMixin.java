package com.cbpr.mixin;

import com.cbpr.battle.BattleObserver;
import com.cbpr.camera.Pose;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    @Final
    private Quaternionf rotation;

    @Shadow
    @Final
    private Vector3f forwards;

    @Shadow
    @Final
    private Vector3f up;

    @Shadow
    @Final
    private Vector3f left;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void cbpr$applyBattleShot(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
                                      float partialTick, CallbackInfo ci) {
        Camera self = (Camera) (Object) this;
        Vec3 pos = self.getPosition();
        Pose vanilla = new Pose(pos.x, pos.y, pos.z, self.getYRot(), self.getXRot(), 0.0, 0.0);
        Pose shot = BattleObserver.shot(vanilla, partialTick);
        if (shot == null) {
            return;
        }
        setPosition(shot.getX(), shot.getY(), shot.getZ());
        setRotation((float) shot.getYaw(), (float) shot.getPitch());
        if (shot.getRoll() != 0.0) {

            rotation.rotateZ((float) Math.toRadians(shot.getRoll()));
            forwards.set(0.0F, 0.0F, -1.0F).rotate(rotation);
            up.set(0.0F, 1.0F, 0.0F).rotate(rotation);
            left.set(-1.0F, 0.0F, 0.0F).rotate(rotation);
        }

        this.detached = true;
    }
}
