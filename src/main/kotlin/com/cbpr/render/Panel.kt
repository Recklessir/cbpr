package com.cbpr.render

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

object Panel {
    const val COLOR = 0xF0121212.toInt()
    const val BORDER = 0xFFB4B4B4.toInt()
    const val INNER_BORDER = 0xFF3C3C3C.toInt()
    const val RULE = 0xFF555555.toInt()
    const val STATS_COLOR = 0xFFD27F

    fun draw(graphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, fade: Double = 1.0) {
        graphics.fill(x, y, x + width, y + height, faded(COLOR, fade))
        graphics.renderOutline(x, y, width, height, faded(BORDER, fade))
        graphics.renderOutline(x + 1, y + 1, width - 2, height - 2, faded(INNER_BORDER, fade))
    }

    fun rule(graphics: GuiGraphics, x1: Int, x2: Int, y: Int, fade: Double = 1.0) {
        graphics.fill(x1, y, x2, y + 1, faded(RULE, fade))
    }

    fun text(graphics: GuiGraphics, message: Component, detail: Component?, centreX: Double, centreY: Double, scale: Float, rgb: Int, fade: Double, margin: Int) {
        val font = Minecraft.getInstance().font
        val small = scale * DETAIL_SHARE
        val mainHeight = font.lineHeight * scale
        val detailHeight = if (detail == null) 0f else font.lineHeight * small + RULE_GAP * 2 + 1
        val width = maxOf(font.width(message) * scale, if (detail == null) 0f else font.width(detail) * small) + 2 * margin
        val height = mainHeight + detailHeight + 2 * margin
        val left = centreX - width / 2
        val top = centreY - height / 2
        val argbFade = ((fade.coerceIn(0.0, 1.0) * 255).toInt() shl 24)
        val pose = graphics.pose()
        pose.pushPose()
        pose.translate(0.0, 0.0, DEPTH)
        draw(graphics, left.roundToInt(), top.roundToInt(), width.roundToInt(), height.roundToInt(), fade)
        scaled(graphics, message, centreX, top + margin, scale, argbFade or (rgb and 0xFFFFFF))
        if (detail != null) {
            val ruleY = (top + margin + mainHeight + RULE_GAP).roundToInt()
            rule(graphics, (left + margin).roundToInt(), (left + width - margin).roundToInt(), ruleY, fade)
            scaled(graphics, detail, centreX, ruleY + 1.0 + RULE_GAP, small, argbFade or STATS_COLOR)
        }
        pose.popPose()
    }

    private fun scaled(graphics: GuiGraphics, text: Component, centreX: Double, top: Double, scale: Float, argb: Int) {
        val font = Minecraft.getInstance().font
        val pose = graphics.pose()
        pose.pushPose()
        pose.translate(centreX, top, 0.0)
        pose.scale(scale, scale, 1f)
        graphics.drawString(font, text, -font.width(text) / 2, 0, argb, true)
        pose.popPose()
    }

    private const val DETAIL_SHARE = 0.55f
    private const val RULE_GAP = 3
    private const val DEPTH = 400.0

    fun faded(argb: Int, fade: Double): Int = (((argb ushr 24) * fade.coerceIn(0.0, 1.0)).toInt() shl 24) or (argb and 0xFFFFFF)
}
