package com.cbpr.render

import com.cbpr.battle.BattleObserver
import com.cbpr.config.Settings
import com.cbpr.ui.CalloutMotion
import com.cbpr.ui.LetterboxMotion
import com.cbpr.ui.TintMotion
import com.cbpr.ui.Vignette
import com.cbpr.ui.WashMotion
import com.cobblemon.mod.common.client.CobblemonResources
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.network.chat.Component
import org.joml.Matrix4f

object CinematicHud {
    private val letterbox = LetterboxMotion()
    private val callout = CalloutMotion()
    private val result = CalloutMotion()
    private val stamp = CalloutMotion()
    private val pokemonName = CalloutMotion()
    private val tint = TintMotion()
    private val wash = WashMotion()
    private var calloutText: Component? = null
    private var resultText: Component? = null
    private var stampText: Component? = null
    private var pokemonNameText: Component? = null
    private var pokemonNameDetail: Component? = null
    private var calloutRgb = WHITE
    private var stampRgb = WHITE

    fun showCallout(name: Component, rgb: Int = WHITE) {
        calloutText = styled(name)
        calloutRgb = rgb
        callout.show()
    }

    fun showPokemonName(text: Component, detail: Component) {
        pokemonNameText = styled(text)
        pokemonNameDetail = styled(detail)
        pokemonName.show(POKEMON_NAME_HOLD_SECONDS)
    }

    fun showStamp(text: Component, rgb: Int) {
        stampText = styled(text)
        stampRgb = rgb
        stamp.show(STAMP_HOLD_SECONDS)
    }

    fun showResult(text: Component) {
        resultText = styled(text)
        result.show(RESULT_HOLD_SECONDS)
    }

    fun resetWash() = wash.reset()

    fun reset() {
        letterbox.reset()
        callout.reset()
        result.reset()
        stamp.reset()
        pokemonName.reset()
        pokemonNameText = null
        pokemonNameDetail = null
        DamageNumbers.reset()
        tint.reset()
        calloutText = null
        resultText = null
        stampText = null
    }

    @JvmStatic
    fun render(graphics: GuiGraphics) {
        val dt = (Minecraft.getInstance().timer.realtimeDeltaTicks / 20.0).coerceIn(0.0, 0.1)
        val reduced = Settings.current.reducedMotion
        callout.reduced = reduced
        stamp.reduced = reduced
        pokemonName.reduced = reduced
        result.reduced = reduced
        letterbox.step(dt, Settings.current.letterbox && BattleObserver.wantsBars(), reduced)
        callout.step(dt)
        result.step(dt)
        stamp.step(dt)
        pokemonName.step(dt)
        tint.step(dt, BattleObserver.tintTarget())
        DamageNumbers.render(graphics, dt)
        TargetMarkers.render(graphics)
        if (!letterbox.hidden) drawBars(graphics.guiWidth().toFloat(), graphics.guiHeight().toFloat())
        if (callout.active) drawText(graphics, calloutText, callout, CALLOUT_HEIGHT, CALLOUT_SCALE, calloutRgb)
        if (pokemonName.active) drawText(graphics, pokemonNameText, pokemonName, POKEMON_NAME_HEIGHT, POKEMON_NAME_SCALE, WHITE, pokemonNameDetail)
        if (stamp.active) drawText(graphics, stampText, stamp, STAMP_HEIGHT, STAMP_SCALE, stampRgb)
        if (result.active) drawText(graphics, resultText, result, RESULT_HEIGHT, RESULT_SCALE, WHITE)
    }

    @JvmStatic
    fun bottomBarHeight(guiHeight: Double): Double = guiHeight * BAR_FRACTION * letterbox.fraction

    @JvmStatic
    fun tintWorld() {
        val flash = BattleObserver.flash()
        val alpha = if (tint.visible) (tint.amount * TINT_ALPHA * 255).toInt() else 0
        val flashAlpha = (flash * FLASH_ALPHA * 255).toInt()
        wash.step((Minecraft.getInstance().timer.realtimeDeltaTicks / 20.0).coerceIn(0.0, 0.1), BattleObserver.washRgb())
        val washAlpha = (wash.amount * VIGNETTE_ALPHA * 255).toInt()

        if (alpha <= 0 && flashAlpha <= 0 && washAlpha <= 0) return

        val saved = RenderPass.enterScreen()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorShader)
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            RenderSystem.disableDepthTest()

            RenderSystem.disableCull()
            val identity = Matrix4f()
            val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)

            if (washAlpha > 0) vignette(buffer, identity, wash.rgb, washAlpha)
            if (alpha > 0) colouredRectangle(buffer, identity, -1f, -1f, 1f, 1f, (alpha shl 24) or TINT_RGB)

            if (flashAlpha > 0) colouredRectangle(buffer, identity, -1f, -1f, 1f, 1f, (flashAlpha shl 24) or BattleObserver.flashRgb())
            BufferUploader.drawWithShader(buffer.buildOrThrow())
        } finally {
            RenderPass.leave(saved)
            RenderSystem.enableCull()
            RenderSystem.enableDepthTest()
            RenderSystem.disableBlend()
        }
    }

    fun styled(text: Component): Component =
        text.copy().withStyle { it.withFont(CobblemonResources.DEFAULT_LARGE).withBold(true) }

    private fun drawBars(width: Float, height: Float) {
        val bar = height * (BAR_FRACTION * letterbox.fraction).toFloat()
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableDepthTest()
        try {
            val identity = Matrix4f()
            val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
            colouredRectangle(buffer, identity, 0f, 0f, width, bar, BLACK)
            colouredRectangle(buffer, identity, 0f, height - bar, width, height, BLACK)
            BufferUploader.drawWithShader(buffer.buildOrThrow())
        } finally {
            RenderSystem.enableDepthTest()
            RenderSystem.disableBlend()
        }
    }

    private fun vignette(buffer: VertexConsumer, matrix: Matrix4f, rgb: Int, maxAlpha: Int) {
        for (ring in 0 until Vignette.RINGS) {
            val innerRadius = Vignette.radius(ring).toFloat()
            val outerRadius = Vignette.radius(ring + 1).toFloat()
            val innerColour = (((maxAlpha * Vignette.strength(ring.toDouble() / Vignette.RINGS)).toInt()) shl 24) or (rgb and 0xFFFFFF)
            val outerColour = (((maxAlpha * Vignette.strength((ring + 1).toDouble() / Vignette.RINGS)).toInt()) shl 24) or (rgb and 0xFFFFFF)
            for (segment in 0 until Vignette.SEGMENTS) {
                val c1 = Vignette.COS[segment]
                val s1 = Vignette.SIN[segment]
                val c2 = Vignette.COS[segment + 1]
                val s2 = Vignette.SIN[segment + 1]
                buffer.addVertex(matrix, innerRadius * c1, innerRadius * s1, 0f).setColor(innerColour)
                buffer.addVertex(matrix, outerRadius * c1, outerRadius * s1, 0f).setColor(outerColour)
                buffer.addVertex(matrix, outerRadius * c2, outerRadius * s2, 0f).setColor(outerColour)
                buffer.addVertex(matrix, innerRadius * c2, innerRadius * s2, 0f).setColor(innerColour)
            }
        }
    }

    private fun colouredRectangle(buffer: VertexConsumer, matrix: Matrix4f, x1: Float, y1: Float, x2: Float, y2: Float, argb: Int) {
        buffer.addVertex(matrix, x1, y1, 0f).setColor(argb)
        buffer.addVertex(matrix, x1, y2, 0f).setColor(argb)
        buffer.addVertex(matrix, x2, y2, 0f).setColor(argb)
        buffer.addVertex(matrix, x2, y1, 0f).setColor(argb)
    }

    private fun drawText(graphics: GuiGraphics, message: Component?, motion: CalloutMotion, heightFraction: Double, textScale: Double, rgb: Int, detail: Component? = null) {
        if (message == null) return

        if (motion.alpha * 255 < MIN_ALPHA) return
        Panel.text(graphics, message, detail, graphics.guiWidth() / 2.0, graphics.guiHeight() * heightFraction, (textScale * motion.scale).toFloat(), rgb, motion.alpha, TEXT_MARGIN)
    }

    private const val BAR_FRACTION = 0.11
    private const val BLACK = 0xFF000000.toInt()
    private const val TINT_RGB = 0x0A0E20
    private const val TINT_ALPHA = 0.5
    private const val FLASH_ALPHA = 0.6
    private const val VIGNETTE_ALPHA = 0.28
    private const val CALLOUT_SCALE = 2.0
    private const val CALLOUT_HEIGHT = 0.26
    private const val POKEMON_NAME_SCALE = 2.2
    private const val POKEMON_NAME_HEIGHT = 0.3
    private const val POKEMON_NAME_HOLD_SECONDS = 2.0
    private const val STAMP_SCALE = 1.4
    private const val STAMP_HEIGHT = 0.52
    private const val STAMP_HOLD_SECONDS = 0.8
    private const val WHITE = 0xFFFFFF
    private const val TEXT_MARGIN = 4
    private const val RESULT_SCALE = 2.6
    private const val RESULT_HEIGHT = 0.4
    private const val RESULT_HOLD_SECONDS = 3.2
    private const val MIN_ALPHA = 8
}
