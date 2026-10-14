package com.cbpr.client

import com.cbpr.battle.BattleObserver
import com.cbpr.battle.ProfileLoader
import com.cbpr.config.Settings
import com.cbpr.render.CinematicHud
import com.cbpr.render.GroundRing
import com.cbpr.render.MenuHost
import com.cbpr.render.OcclusionFade
import com.cbpr.ui.ConfigScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.minecraft.client.Minecraft
import net.minecraft.server.packs.PackType

object CbprClient : ClientModInitializer {

    private var openConfig = false

    override fun onInitializeClient() {
        Settings.load()
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(ProfileLoader)
        ClientTickEvents.END_CLIENT_TICK.register(BattleObserver::tick)
        ClientTickEvents.END_CLIENT_TICK.register(::openConfigIfAsked)
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal("cbpr").then(
                    ClientCommandManager.literal("config").executes {
                        openConfig = true
                        1
                    },
                ),
            )
        }
        WorldRenderEvents.START.register(MenuHost::capture)
        WorldRenderEvents.LAST.register(OcclusionFade::render)
        WorldRenderEvents.LAST.register(GroundRing::render)
        WorldRenderEvents.LAST.register { CinematicHud.tintWorld() }
        ScreenEvents.BEFORE_INIT.register { _, screen, _, _ -> MenuHost.attach(screen) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> BattleObserver.reset() }
    }

    private fun openConfigIfAsked(mc: Minecraft) {
        if (openConfig) {
            openConfig = false
            mc.setScreen(ConfigScreen(mc.screen))
        }
    }
}
