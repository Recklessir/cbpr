package com.cbpr.mixin;

import com.cbpr.battle.BattleEvents;
import com.cobblemon.mod.common.client.net.battle.BattleHealthChangeHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleHealthChangePacket;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BattleHealthChangeHandler.class)
public abstract class BattleHealthChangeHandlerMixin {

    @Inject(
        method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleHealthChangePacket;Lnet/minecraft/client/Minecraft;)V",
        at = @At("HEAD"),
        require = 0
    )
    private void cbpr$readDamage(BattleHealthChangePacket packet, Minecraft client, CallbackInfo ci) {
        BattleEvents.INSTANCE.health(packet.getPnx(), packet.getNewHealth(), packet.getNewMaxHealth());
    }
}
