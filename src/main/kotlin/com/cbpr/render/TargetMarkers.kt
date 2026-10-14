package com.cbpr.render

import com.cbpr.battle.BattleObserver
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

object TargetMarkers {
    private const val HOVERED = 0xFFD25A
    private const val OTHER = 0xE6E6E6
    private const val HOVERED_SCALE = 1.25f
    private const val OTHER_SCALE = 0.75f
    private const val OTHER_FADE = 0.8
    private const val MARGIN = 3

    fun render(graphics: GuiGraphics) {
        val partial = Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(false)
        val markers = BattleObserver.targetMarkers(partial)
        if (markers.isEmpty()) return
        val hint = CinematicHud.styled(Component.translatable("cbpr.target.click"))

        for (marker in markers) {
            val at = MenuHost.screenPoint(marker.point) ?: continue
            val x = (at.x + 1.0) / 2.0 * graphics.guiWidth()
            val y = (1.0 - at.y) / 2.0 * graphics.guiHeight()
            val name = CinematicHud.styled(Component.literal(marker.label))
            if (marker.hovered) Panel.text(graphics, name, hint, x, y, HOVERED_SCALE, HOVERED, 1.0, MARGIN)
            else Panel.text(graphics, name, null, x, y, OTHER_SCALE, OTHER, OTHER_FADE, MARGIN)
        }
    }
}
