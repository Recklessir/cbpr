package com.cbpr.mixin;

import com.cbpr.battle.BattleEvents;
import com.cobblemon.mod.common.client.net.battle.BattleMessageHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMessagePacket;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BattleMessageHandler.class)
public abstract class BattleMessageHandlerMixin {

    @Inject(
        method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleMessagePacket;Lnet/minecraft/client/Minecraft;)V",
        at = @At("HEAD"),
        require = 0
    )
    private void cbpr$readMessages(BattleMessagePacket packet, Minecraft client, CallbackInfo ci) {
        BattleEvents.INSTANCE.messages(packet.getMessages());
    }
}
