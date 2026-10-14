package com.cbpr.render

import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.VertexSorting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.MultiBufferSource
import org.joml.Matrix4f

class ScreenCanvas {
    private var target: TextureTarget? = null

    val textureId: Int get() = checkNotNull(target).colorTextureId

    fun render(mc: Minecraft, extraGuiWidth: Int, draw: (GuiGraphics) -> Unit) {
        val window = mc.window
        val guiScale = window.guiScale
        val width = window.width + (extraGuiWidth * guiScale).toInt()
        val canvas = target ?: TextureTarget(width, window.height, true, Minecraft.ON_OSX).also { target = it }
        if (canvas.width != width || canvas.height != window.height) canvas.resize(width, window.height, Minecraft.ON_OSX)

        val savedProjection = Matrix4f(RenderSystem.getProjectionMatrix())
        val savedSorting = RenderSystem.getVertexSorting()
        val modelView = RenderSystem.getModelViewStack()

        canvas.setClearColor(0f, 0f, 0f, 0f)
        canvas.clear(Minecraft.ON_OSX)
        canvas.bindWrite(true)
        RenderSystem.setProjectionMatrix(
            Matrix4f().setOrtho(0f, (width / guiScale).toFloat(), (window.height / guiScale).toFloat(), 0f, 1000f, 21000f),
            VertexSorting.ORTHOGRAPHIC_Z,
        )
        modelView.pushMatrix()
        modelView.translation(0f, 0f, -11000f)
        RenderSystem.applyModelViewMatrix()
        try {
            val graphics = GuiGraphics(mc, buffers)
            draw(graphics)
            graphics.flush()
        } finally {
            modelView.popMatrix()
            RenderSystem.applyModelViewMatrix()
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting)
            mc.mainRenderTarget.bindWrite(true)
        }
    }

    fun close() {
        target?.destroyBuffers()
        target = null
    }

    private companion object {

        val buffers: MultiBufferSource.BufferSource by lazy { MultiBufferSource.immediate(ByteBufferBuilder(786432)) }
    }
}
