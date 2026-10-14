package com.cbpr.render

import com.cbpr.camera.OcclusionBlocks
import com.cbpr.camera.OcclusionPick
import com.cbpr.camera.OcclusionSelector
import com.cbpr.camera.Point
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.slf4j.LoggerFactory

object OcclusionFade {
    private val log = LoggerFactory.getLogger("cbpr")
    private val blocks = OcclusionBlocks(::markDirty)
    private val source: MultiBufferSource.BufferSource by lazy { MultiBufferSource.immediate(ByteBufferBuilder(RenderType.translucent().bufferSize())) }
    private val random = RandomSource.create()
    private var sinceChoice = CHOICE_INTERVAL

    @JvmStatic
    var failed = false
        private set

    class Want(val camera: Point, val targets: List<OcclusionSelector.Target>, val floorY: Int)

    @JvmStatic
    fun hides(x: Int, y: Int, z: Int): Boolean = blocks.hides(x, y, z)

    fun update(dt: Double, want: Want?) {
        if (failed) return
        try {
            sinceChoice += dt
            if (want == null) {
                blocks.releaseAll()
            } else if (sinceChoice >= CHOICE_INTERVAL) {
                sinceChoice = 0.0
                blocks.choose(select(want))
            }
            blocks.step(dt)
        } catch (e: Exception) {
            fail(e)
        }
    }

    fun reset() {
        failed = false
        sinceChoice = CHOICE_INTERVAL
        blocks.clear()
    }

    private fun markDirty(x: Int, y: Int, z: Int) {
        val mc = Minecraft.getInstance()
        if (mc.level == null) return
        try {
            mc.levelRenderer.setSectionDirtyWithNeighbors(x, y, z)
        } catch (e: NullPointerException) {

        }
    }

    private fun fail(e: Exception) {
        log.error("CBPR occlusion fading failure, the blocks are restored and the camera avoids walls instead", e)
        failed = true
        blocks.clear()
    }

    private fun select(want: Want): Map<Long, OcclusionPick> {
        val level = Minecraft.getInstance().level ?: return emptyMap()
        val pos = BlockPos.MutableBlockPos()
        return OcclusionSelector.select(want.camera, want.targets, want.floorY) { x, y, z ->
            val state = level.getBlockState(pos.set(x, y, z))
            !state.isAir && state.renderShape == RenderShape.MODEL && state.fluidState.isEmpty
        }
    }

    fun render(context: WorldRenderContext) {
        if (failed || !blocks.active) return
        try {
            draw(context.projectionMatrix(), context.positionMatrix(), context.camera().position)
        } catch (e: Exception) {
            fail(e)
        }
    }

    private fun draw(projection: Matrix4f, view: Matrix4f, camera: Vec3) {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val saved = RenderPass.enterWorld(projection, view)
        try {
            val stack = PoseStack()
            val buffer = source.getBuffer(RenderType.translucent())

            val pos = BlockPos.MutableBlockPos()
            val consumer = AlphaConsumer(buffer, 1f)
            for (entry in blocks.entries) {
                val alpha = entry.alpha.value.toFloat()
                if (alpha < MIN_DRAWN_ALPHA) continue
                pos.set(entry.x, entry.y, entry.z)
                val state = level.getBlockState(pos)
                if (state.isAir) continue
                consumer.alpha = alpha.coerceAtMost(1f)
                stack.pushPose()
                stack.translate(entry.x - camera.x, entry.y - camera.y, entry.z - camera.z)
                mc.blockRenderer.renderBatched(state, pos, level, stack, consumer, true, random)
                stack.popPose()
            }
            source.endBatch(RenderType.translucent())
        } finally {
            RenderPass.leave(saved)
        }
    }

    private const val CHOICE_INTERVAL = 0.1
    private const val MIN_DRAWN_ALPHA = 0.02f
}
