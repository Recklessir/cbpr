package com.cbpr.render

import com.cbpr.camera.Point
import com.cbpr.config.Settings
import com.cbpr.ui.DamagePopups
import com.cbpr.ui.DamageText
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

object DamageNumbers {
    private val popups = DamagePopups()

    private val texts = HashMap<String, Component>()

    fun spawn(lost: Double, anchor: Point) = popups.add(lost, anchor, Settings.current.reducedMotion)

    fun spawnCue(label: String, rgb: Int, anchor: Point) = popups.addCue(label, rgb, anchor, Settings.current.reducedMotion)

    fun reset() = popups.clear()

    fun render(graphics: GuiGraphics, dt: Double) {
        popups.step(dt)
        for (popup in popups.active) {
            val at = MenuHost.screenPoint(Point(popup.anchor.x, popup.anchor.y + popup.rise, popup.anchor.z)) ?: continue

            if (popup.alpha * 255 < MIN_ALPHA) continue
            val scale = (DamageText.scaleAt(at.distance) * popup.scale).toFloat()
            val x = (at.x + 1.0) / 2.0 * graphics.guiWidth() + (popup.slot - 1) * SLOT_SPREAD * scale
            val y = (1.0 - at.y) / 2.0 * graphics.guiHeight()
            if (texts.size > MAX_TEXTS) texts.clear()
            val text = texts.getOrPut(popup.label) { CinematicHud.styled(Component.literal(popup.label)) }
            Panel.text(graphics, text, null, x, y, scale, popup.rgb, popup.alpha, MARGIN)
        }
    }

    private const val MAX_TEXTS = 64
    private const val MIN_ALPHA = 8
    private const val MARGIN = 3
    private const val SLOT_SPREAD = 26
}
