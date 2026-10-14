package com.cbpr.mixin;

import com.cbpr.battle.BattleEvents;
import com.cobblemon.mod.common.client.net.battle.BattleSwitchPokemonHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleSwitchPokemonPacket;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BattleSwitchPokemonHandler.class)
public abstract class BattleSwitchPokemonHandlerMixin {

    @Inject(
        method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleSwitchPokemonPacket;Lnet/minecraft/client/Minecraft;)V",
        at = @At("HEAD"),
        require = 0
    )
    private void cbpr$readSwitch(BattleSwitchPokemonPacket packet, Minecraft client, CallbackInfo ci) {
        BattleEvents.INSTANCE.sentOut(packet.isAlly(), packet.getPnx());
    }
}
