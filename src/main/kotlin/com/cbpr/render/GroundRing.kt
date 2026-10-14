package com.cbpr.render

import com.cbpr.battle.BattleObserver
import com.cbpr.camera.Point
import com.cbpr.config.Settings
import com.cbpr.ui.RingMotion
import com.cbpr.ui.Rgb
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.minecraft.client.renderer.GameRenderer
import org.joml.Matrix4f
import org.slf4j.LoggerFactory
import kotlin.math.cos
import kotlin.math.sin

object GroundRing {
    private val log = LoggerFactory.getLogger("cbpr")

    private val motions = HashMap<String, RingMotion>()
    private val pickedMotion = RingMotion()
    private var lastPicked: BattleObserver.RingTarget? = null
    private var failed = false

    fun render(context: WorldRenderContext) {
        val settings = Settings.current
        val partial = context.tickCounter().getGameTimeDeltaPartialTick(false)
        val hosting = !failed && BattleObserver.isHostingShot()
        val all = if (hosting) BattleObserver.ringTargets(partial) else emptyList()
        val targets = all.filter { if (it.own) settings.groundRing else settings.foeRing }

        motions.keys.retainAll(targets.map { it.slot }.toSet())
        val picked = BattleObserver.hoveredTarget()?.let { slot -> all.firstOrNull { it.slot == slot } }
        if (picked != null) lastPicked = picked
        if (targets.isEmpty() && lastPicked == null) return
        try {
            val dt = (context.tickCounter().realtimeDeltaTicks / 20.0).coerceIn(0.0, 0.1)
            val camera = context.camera().position
            val eye = Point(camera.x, camera.y, camera.z)
            for (target in targets) {
                val motion = motions.getOrPut(target.slot) { RingMotion() }
                if (target.own) {
                    motion.step(dt, BattleObserver.menuEngaged() && BattleObserver.isCommanded(target.slot), settings.reducedMotion, target.low)
                    draw(context.projectionMatrix(), context.positionMatrix(), eye, target.feet, target.radius, motion, colour(target, GOLD, motion.danger), 1.0)
                } else {

                    motion.step(dt, false, settings.reducedMotion, target.low)
                    draw(context.projectionMatrix(), context.positionMatrix(), eye, target.feet, target.radius, motion, colour(target, PALE_RED, motion.danger), -1.0)
                }
            }

            pickedMotion.step(dt, picked != null, settings.reducedMotion)
            val shown = lastPicked
            if (shown != null) {
                if (pickedMotion.engaged > PICKED_VISIBLE) {
                    draw(context.projectionMatrix(), context.positionMatrix(), eye, shown.feet, shown.radius * PICKED_RING_SCALE, pickedMotion, PICKED_GOLD, 1.0)
                } else if (picked == null) {
                    lastPicked = null
                }
            }
        } catch (e: Exception) {
            log.error("CBPR ground ring failure, it is off until restart", e)
            failed = true
        }
    }

    private fun colour(target: BattleObserver.RingTarget, base: Int, danger: Double): Int =
        Rgb.mix(target.statusRgb ?: base, DANGER_RED, danger)

    private fun draw(projection: Matrix4f, view: Matrix4f, camera: Point, feet: Point, radius: Double, ring: RingMotion, rgb: Int, direction: Double) {
        val saved = RenderPass.enterWorld(projection, view)
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.depthMask(false)
        try {
            val centre = Point(feet.x - camera.x, feet.y + LIFT - camera.y, feet.z - camera.z)
            val outer = radius * ring.radiusScale
            val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
            arc(buffer, centre, outer - THIN * radius, outer, 0.0, 360.0, ring.alpha * THIN_ALPHA, rgb)
            for (i in 0 until ARCS) {
                arc(buffer, centre, outer - THICK * radius, outer + THICK * radius * 0.4, direction * ring.spin + i * 360.0 / ARCS, ARC_DEGREES, ring.alpha, rgb)
            }
            BufferUploader.drawWithShader(buffer.buildOrThrow())
        } finally {
            RenderSystem.depthMask(true)
            RenderSystem.enableCull()
            RenderSystem.disableBlend()
            RenderPass.leave(saved)
        }
    }

    private fun arc(buffer: VertexConsumer, centre: Point, inner: Double, outer: Double, startDegrees: Double, sweepDegrees: Double, alpha: Double, rgb: Int) {
        val steps = maxOf(2, (sweepDegrees / STEP_DEGREES).toInt())
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        var from = Math.toRadians(startDegrees)
        var cosFrom = cos(from)
        var sinFrom = sin(from)
        for (i in 0 until steps) {
            val to = Math.toRadians(startDegrees + sweepDegrees * (i + 1) / steps)
            val cosTo = cos(to)
            val sinTo = sin(to)
            vertex(buffer, centre, inner, cosFrom, sinFrom, a, rgb)
            vertex(buffer, centre, outer, cosFrom, sinFrom, a, rgb)
            vertex(buffer, centre, outer, cosTo, sinTo, a, rgb)
            vertex(buffer, centre, inner, cosTo, sinTo, a, rgb)
            from = to
            cosFrom = cosTo
            sinFrom = sinTo
        }
    }

    private fun vertex(buffer: VertexConsumer, centre: Point, radius: Double, cosAngle: Double, sinAngle: Double, alpha: Int, rgb: Int) {
        buffer.addVertex((centre.x + cosAngle * radius).toFloat(), centre.y.toFloat(), (centre.z + sinAngle * radius).toFloat())
            .setColor((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, alpha)
    }

    private const val LIFT = 0.04
    private const val PICKED_RING_SCALE = 1.35
    private const val PICKED_VISIBLE = 0.02
    private const val PICKED_GOLD = 0xFFE066
    private const val ARCS = 6
    private const val ARC_DEGREES = 38.0
    private const val STEP_DEGREES = 6.0
    private const val THIN = 0.03
    private const val THICK = 0.07
    private const val THIN_ALPHA = 0.4

    private const val GOLD = 0xFFDC8C
    private const val PALE_RED = 0xEB7D73
    private const val DANGER_RED = 0xFF4A3D
}
