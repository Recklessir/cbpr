package com.cbpr.render

import com.cbpr.audio.Sfx
import com.cbpr.audio.SoundSlot
import com.cbpr.battle.BattleObserver
import com.cbpr.battle.CobblemonScreen
import com.cbpr.camera.Point
import com.cbpr.config.Settings
import com.cbpr.ui.BannerKind
import com.cbpr.ui.BattleScreen
import com.cbpr.ui.CameraFrame
import com.cbpr.ui.BannerSlot
import com.cbpr.ui.BannerSlots
import com.cbpr.ui.MenuLayout
import com.cbpr.ui.PreviewText
import com.cbpr.ui.MenuPointer
import com.cbpr.ui.MenuScene
import com.cbpr.ui.MoveView
import com.cbpr.ui.PartyPreview
import com.cbpr.ui.PpState
import com.cbpr.ui.MenuRegions
import com.cbpr.ui.Rect
import com.cbpr.ui.SceneEvent
import com.cbpr.ui.SceneInput
import com.cbpr.ui.SceneQuad
import com.cbpr.ui.Subject
import com.cobblemon.mod.common.api.gui.ParentWidget
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.gui.battle.BattleGUI
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleBackButton
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGimmickButton
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleShiftButton
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleTargetSelection
import com.cobblemon.mod.common.client.gui.battle.widgets.BattleOptionTile
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import org.joml.Vector4f
import kotlin.math.roundToInt
import net.minecraft.client.gui.screens.Screen
import net.minecraft.resources.ResourceLocation
import net.minecraft.client.renderer.GameRenderer
import org.joml.Matrix4f
import org.slf4j.LoggerFactory

fun interface ScreenDrawer {
    fun draw(graphics: GuiGraphics, mouseX: Int, mouseY: Int)
}

object MenuHost {
    private class WorldFrame {
        val projection = Matrix4f()
        val view = Matrix4f()
        var cameraPos = Point(0.0, 0.0, 0.0)
        var right = Point(1.0, 0.0, 0.0)
        var up = Point(0.0, 1.0, 0.0)
        var forward = Point(0.0, 0.0, -1.0)
        var dt = 0.0
        var partialTick = 0f
        var valid = false
    }

    private val log = LoggerFactory.getLogger("cbpr")
    private val world = WorldFrame()
    private val canvas = ScreenCanvas()
    private val intake = BannerSlots()

    private var scene = MenuScene()
    private var battleScreen = BattleScreen.NONE
    private val hoveredSlot get() = scene.hovered
    private var lastOptionClickNanos = 0L
    private var clickSoundAt = 0L
    private var canvasGuiWidth = 0.0
    private var guiHeight = 0.0

    private var drawing = false
    private var inTile = false
    private var previewMove: MoveTemplate? = null

    private var previewParty: Pokemon? = null
    private var previewArtWidth = BannerKind.PREVIEW.fullWidth

    private var previewArtHeight = BannerKind.PREVIEW.fullHeight
    private var hosting = false
    private var failed = false
    private var cursor: MenuPointer.Cursor? = null
    private var pendingLog: Rect? = null
    private var logShift = 0.0
    private var pendingQuads: List<SceneQuad> = emptyList()
    private var ceilingLogged = false
    private var noSubjectLogged = false

    private var addons: Map<AbstractWidget, Rect> = emptyMap()
    private val hostedAddons = HashSet<AbstractWidget>()
    private var shiftHosted = false
    private var lastHostedKinds = ""

    private val failedAddons = HashSet<AbstractWidget>()
    private val loggedClasses = HashSet<Class<*>>()

    fun capture(context: WorldRenderContext) {
        val camera = context.camera()
        world.projection.set(context.projectionMatrix())
        world.view.set(context.positionMatrix())
        val pos = camera.position
        world.cameraPos = Point(pos.x, pos.y, pos.z)
        val left = camera.leftVector
        world.right = Point(-left.x.toDouble(), -left.y.toDouble(), -left.z.toDouble())
        val up = camera.upVector
        world.up = Point(up.x.toDouble(), up.y.toDouble(), up.z.toDouble())
        val look = camera.lookVector
        world.forward = Point(look.x.toDouble(), look.y.toDouble(), look.z.toDouble())
        world.dt = (context.tickCounter().realtimeDeltaTicks / 20.0).coerceIn(0.0, 0.1)
        world.partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false)
        world.valid = true
        if (!BattleObserver.isHostingShot()) {
            failed = false
            if (hosting) stopHosting()
        }
    }

    class CursorRay(val origin: Point, val direction: Point)

    @JvmStatic
    fun cursorRay(): CursorRay? {
        if (!world.valid) return null
        val mc = Minecraft.getInstance()
        val window = mc.window
        val ndcX = mc.mouseHandler.xpos() / window.screenWidth * 2.0 - 1.0
        val ndcY = 1.0 - mc.mouseHandler.ypos() / window.screenHeight * 2.0
        val direction = MenuLayout.cursorRay(ndcX, ndcY, 1.0 / world.projection.m00(), 1.0 / world.projection.m11(), world.right, world.up, world.forward)
        return CursorRay(world.cameraPos, direction)
    }

    @JvmStatic
    fun hoveringMove(): Boolean = hosting && scene.hoveringMove()

    @JvmStatic
    fun optionClickedRecently(): Boolean = System.nanoTime() - lastOptionClickNanos < OPTION_CLICK_WINDOW_NANOS

    @JvmStatic
    fun dropsClickSound(sound: ResourceLocation): Boolean =
        System.nanoTime() - clickSoundAt < CLICK_SOUND_WINDOW_NANOS &&
            (sound == CLICK_COBBLEMON || sound == CLICK_VANILLA)

    fun attach(screen: Screen) {
        if (screen !is BattleGUI) return
        ScreenMouseEvents.allowMouseClick(screen).register { s, _, _, button ->

            if (button == 0 && hosting && BattleObserver.clickHoveredTarget()) return@register false
            if (button == 0 && hosting && hoveredSlot?.kind == BannerKind.OPTION && CobblemonScreen.of((s as? BattleGUI)?.getCurrentActionSelection()) == BattleScreen.GENERAL) {
                lastOptionClickNanos = System.nanoTime()
            }
            if (button == 0 && hosting && hoveredSlot != null && Sfx.play(if (hoveredSlot?.kind == BannerKind.BACK) SoundSlot.UI_BACK else SoundSlot.UI_SELECT)) {
                clickSoundAt = System.nanoTime()
            }
            forward { c -> s.mouseClicked(c.x, c.y, button) }
        }
        ScreenMouseEvents.allowMouseRelease(screen).register { s, _, _, button -> forward { c -> s.mouseReleased(c.x, c.y, button) } }
        ScreenMouseEvents.allowMouseScroll(screen).register { s, _, _, h, v -> forward { c -> s.mouseScrolled(c.x, c.y, h, v) } }
    }

    fun close() {
        stopHosting()
        canvas.close()
    }

    @JvmStatic
    fun compose() {
        val logRect = pendingLog ?: return
        val quads = pendingQuads
        pendingLog = null
        pendingQuads = emptyList()
        try {

            logShift = CinematicHud.bottomBarHeight(guiHeight)
            drawLog(logRect, logShift)
            drawWorld(quads)
        } catch (e: Exception) {
            log.error("CBPR menu drawing failed, falling back to the vanilla battle screen", e)
            failed = true
            stopHosting()
        }
    }

    @JvmStatic
    fun render(screen: Screen, graphics: GuiGraphics, draw: ScreenDrawer): Boolean {
        pendingLog = null
        pendingQuads = emptyList()
        if (screen !is BattleGUI || failed || !world.valid || !BattleObserver.hostsMenu()) return notHosted()

        val subject = BattleObserver.menuSubject(world.partialTick)
        if (subject == null) {

            if (!noSubjectLogged) log.info("CBPR menu not hosted on {}: there is no Pokemon to place it around", CobblemonScreen.of(screen.getCurrentActionSelection()))
            noSubjectLogged = true
            return notHosted()
        }
        noSubjectLogged = false
        return try {
            hosted(screen, graphics, subject, draw)
            true
        } catch (e: Exception) {

            log.error("CBPR menu hosting failed, falling back to the vanilla battle screen", e)
            failed = true
            stopHosting()
            false
        }
    }

    private fun notHosted(): Boolean {
        hosting = false
        cursor = null
        return false
    }

    @JvmStatic
    fun relocateMove(tile: BattleMoveSelection.MoveTile, graphics: GuiGraphics, original: Runnable) {
        val real = Rect(tile.x.toDouble(), tile.y.toDouble(), BattleMoveSelection.MOVE_WIDTH.toDouble(), BattleMoveSelection.MOVE_HEIGHT.toDouble())
        val slot = host(BannerKind.MOVE, real, drawing && tile in tile.moveSelection.moveTiles, graphics.pose(), original) ?: return
        if (slot.handle == hoveredSlot) previewMove = tile.moveTemplate
    }

    @JvmStatic
    fun relocateSwitch(tile: BattleSwitchPokemonSelection.SwitchTile, graphics: GuiGraphics, original: Runnable) {
        val real = Rect(tile.x.toDouble(), tile.y.toDouble(), BattleSwitchPokemonSelection.SwitchTile.SELECT_WIDTH.toDouble(), BattleSwitchPokemonSelection.SwitchTile.SELECT_HEIGHT.toDouble())
        val slot = host(BannerKind.SWITCH, real, drawing && tile in tile.selection.tiles, graphics.pose(), original) ?: return
        if (slot.handle == hoveredSlot) previewParty = tile.pokemon
    }

    @JvmStatic
    fun skipsPartyPanel(texture: ResourceLocation): Boolean =
        drawing && (texture.path == "textures/gui/battle/selection_underlay.png" || (!inTile && texture.path == "textures/gui/battle/party_select_disabled.png"))

    @JvmStatic
    fun hidesExternalPreview(): Boolean = drawing

    @JvmStatic
    fun relocateOption(tile: BattleOptionTile, graphics: GuiGraphics, original: Runnable) {
        val real = Rect(tile.x.toDouble(), tile.y.toDouble(), BattleOptionTile.OPTION_WIDTH.toDouble(), BattleOptionTile.OPTION_HEIGHT.toDouble())
        val listed = drawing && (tile.battleGUI.getCurrentActionSelection() as? BattleGeneralActionSelection)?.tiles?.contains(tile) == true
        host(BannerKind.OPTION, real, listed, graphics.pose(), original)
    }

    @JvmStatic
    fun relocateBack(button: BattleBackButton, graphics: GuiGraphics, original: Runnable) {
        host(BannerKind.BACK, backRect(button), drawing && battleScreen.floats, graphics.pose(), original)
    }

    private fun moveSelection() = if (drawing) (Minecraft.getInstance().screen as? BattleGUI)?.getCurrentActionSelection() as? BattleMoveSelection else null

    @JvmStatic
    fun relocateShift(button: BattleShiftButton, graphics: GuiGraphics, original: Runnable) = relocateShift(button.x, button.y, graphics, original)

    @JvmStatic
    fun drawShiftInstead(modButton: Any, graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float, original: Runnable) {
        val shift = moveSelection()?.shiftButton
        val enabled = try {
            modButton.javaClass.getMethod("getEnabled").invoke(modButton) as Boolean
        } catch (e: ReflectiveOperationException) {
            false
        }
        if (shift == null || !enabled) original.run() else shift.render(graphics, mouseX, mouseY, partialTicks)
    }

    private fun relocateShift(x: Float, y: Float, graphics: GuiGraphics, original: Runnable) {
        if (moveSelection() == null) {
            original.run()
            return
        }
        if (shiftHosted) return
        val real = Rect(x.toDouble(), y.toDouble(), BattleShiftButton.WIDTH * BattleShiftButton.SCALE.toDouble(), BattleShiftButton.HEIGHT * BattleShiftButton.SCALE.toDouble())
        shiftHosted = host(BannerKind.SHIFT, real, true, graphics.pose(), original) != null
    }

    @JvmStatic
    fun relocateGimmick(button: BattleGimmickButton, pose: PoseStack, original: Runnable) {
        val selection = moveSelection()
        val real = Rect(button.x.toDouble(), button.y.toDouble(), BattleGimmickButton.WIDTH * BattleGimmickButton.SCALE.toDouble(), BattleGimmickButton.HEIGHT * BattleGimmickButton.SCALE.toDouble())
        host(BannerKind.GIMMICK, real, selection?.gimmickButtons?.contains(button) == true, pose, original)
    }

    @JvmStatic
    fun relocatesWidget(widget: AbstractWidget): Boolean = drawing && widget in addons && widget !in failedAddons

    @JvmStatic
    fun relocateWidget(widget: AbstractWidget, graphics: GuiGraphics, original: Runnable) {
        val slot = addons[widget]?.let { intake.register(BannerKind.ADDON, it) }
        if (slot == null) {
            leftOnLeftoverPlane(widget)
            original.run()
            return
        }
        try {
            draw(slot, graphics.pose(), original)
            hostedAddons += widget
        } catch (e: Exception) {
            intake.drop(slot)
            failedAddons += widget
            log.error("CBPR could not draw {} as a Banner, it stays on the Leftover plane", widget.javaClass.name, e)
            original.run()
        }
    }

    private fun leftOnLeftoverPlane(widget: AbstractWidget) {
        if (loggedClasses.add(widget.javaClass)) log.warn("CBPR left {} on the Leftover plane, it cannot be shown as a Banner", widget.javaClass.name)
    }

    private fun host(kind: BannerKind, real: Rect, hostable: Boolean, pose: PoseStack, original: Runnable): BannerSlot? {
        val slot = if (hostable) intake.register(kind, real) else null
        if (slot == null) {
            original.run()
            return null
        }
        draw(slot, pose, original)
        return slot
    }

    private fun draw(slot: BannerSlot, pose: PoseStack, original: Runnable) {
        pose.pushPose()
        pose.translate(slot.art.x + slot.kind.padLeft - slot.real.x, slot.art.y + slot.kind.padTop - slot.real.y, 0.0)
        inTile = true
        try {
            original.run()
        } finally {
            inTile = false
            pose.popPose()
        }
    }

    private fun inTriples() = CobblemonClient.battle?.battleFormat?.battleType?.slotsPerActor == 3

    private fun interactiveChildren(selection: Any?): Int = addons.size + when (selection) {

        is BattleGeneralActionSelection -> selection.tiles.size + (if (selection.lastAnwseredRequest != null) 1 else 0)
        is BattleMoveSelection -> selection.moveTiles.size + selection.gimmickButtons.size + 1 + (if (inTriples()) 1 else 0)
        is BattleSwitchPokemonSelection -> selection.tiles.size + 1
        else -> 0
    }

    private fun findAddons(selection: Any?): Map<AbstractWidget, Rect> {
        if (!battleScreen.floats || selection !is ParentWidget) return emptyMap()
        return selection.children.filterIsInstance<AbstractWidget>().filter { it.active && it.visible && it !in failedAddons }
            .associateWith { Rect(it.x.toDouble(), it.y.toDouble(), it.width.toDouble(), it.height.toDouble()) }
    }

    private fun hosted(screen: BattleGUI, graphics: GuiGraphics, subject: Subject, draw: ScreenDrawer) {
        val mc = Minecraft.getInstance()
        val guiWidth = mc.window.guiScaledWidth.toDouble()
        guiHeight = mc.window.guiScaledHeight.toDouble()
        val logRect = MenuRegions.log(guiWidth, guiHeight)

        hosting = true
        val selection = screen.getCurrentActionSelection()
        battleScreen = CobblemonScreen.of(selection)
        val window = mc.window
        val opening = scene.beginFrame(
            SceneInput(
                screen = battleScreen, screenToken = selection, subject = subject,
                camera = CameraFrame(
                    world.cameraPos, world.right, world.up, world.forward,

                    1.0 / world.projection.m00(), 1.0 / world.projection.m11(), world.dt, ::ndc,
                ),
                reducedMotion = Settings.current.reducedMotion,
                rawX = mc.mouseHandler.xpos(), rawY = mc.mouseHandler.ypos(),
                windowWidth = window.screenWidth.toDouble(), windowHeight = window.screenHeight.toDouble(),
                guiWidth = guiWidth, guiHeight = guiHeight, log = logRect, logShift = logShift,
                targetBack = (selection as? BattleTargetSelection)?.backButton?.let(::backRect),
            ),
        )
        play(opening.events)
        cursor = opening.cursor

        graphics.flush()
        addons = findAddons(selection)
        hostedAddons.clear()
        shiftHosted = false
        intake.begin(guiWidth, interactiveChildren(selection), battleScreen)
        if (intake.overCeiling && !ceilingLogged) {
            ceilingLogged = true
            log.warn("CBPR hosts at most {} widgets on a Battle screen, the rest stay on the Leftover plane", BannerSlots.MAX_HOSTED)
        }
        val mouseX = cursor?.x?.toInt() ?: OFF_SCREEN
        val mouseY = cursor?.y?.toInt() ?: OFF_SCREEN
        drawing = true
        previewMove = null
        previewParty = null
        try {
            canvas.render(mc, intake.stripWidth) { offscreen ->
                draw.draw(offscreen, mouseX, mouseY)
                previewMove?.let { drawPreviewPanel(offscreen, it) }
                previewParty?.let { drawPartyPanel(offscreen, it) }
            }
        } finally {
            drawing = false
        }

        addons.keys.filter { it !in hostedAddons && it !in failedAddons }.forEach(::leftOnLeftoverPlane)
        canvasGuiWidth = guiWidth + intake.stripWidth

        val placement = scene.place(intake.slots, previewArtWidth, previewArtHeight)

        val hostedKinds = intake.slots.groupingBy { it.kind }.eachCount().toString()
        if (hostedKinds != lastHostedKinds) {
            lastHostedKinds = hostedKinds
            log.info("CBPR hosting {} on {}", hostedKinds, battleScreen)
        }
        play(placement.events)

        pendingLog = logRect
        pendingQuads = placement.quads
    }

    private fun backRect(button: BattleBackButton) =
        Rect(button.x.toDouble(), button.y.toDouble(), BattleBackButton.WIDTH * BattleBackButton.SCALE.toDouble(), BattleBackButton.HEIGHT * BattleBackButton.SCALE.toDouble())

    private fun play(events: List<SceneEvent>) {
        for (event in events) when (event) {
            SceneEvent.PanelOpened -> Sfx.play(SoundSlot.PANEL_OPEN)
            SceneEvent.PanelClosed -> Sfx.play(SoundSlot.PANEL_CLOSE)
            is SceneEvent.Hovered -> Sfx.play(SoundSlot.UI_HOVER)
            is SceneEvent.FightMenuOpened -> {
                log.info("CBPR Fight menu popped in for {} buttons", event.buttons)
                BattleObserver.menuOpened()
            }
        }
    }

    private fun drawPreviewPanel(graphics: GuiGraphics, move: MoveTemplate) {
        val slot = intake.register(BannerKind.PREVIEW, Rect(0.0, 0.0, 0.0, 0.0)) ?: return
        val kind = slot.kind
        val x = slot.art.x.toInt() + kind.padLeft
        val y = slot.art.y.toInt() + kind.padTop
        val width = kind.artWidth
        previewArtWidth = kind.fullWidth
        val font = Minecraft.getInstance().font
        val textWidth = width - 2 * PANEL_MARGIN
        val lines = PreviewText.limit(
            font.splitter.splitLines(move.description.string, textWidth, Style.EMPTY).map { it.string },
            PANEL_LINES, { font.width(it) }, textWidth,
        )
        val lineHeight = font.lineHeight
        val height = PANEL_MARGIN + lines.size * lineHeight + PANEL_RULE_GAP * 2 + 1 + lineHeight + PANEL_MARGIN
        previewArtHeight = height + kind.padTop + kind.padBottom
        Panel.draw(graphics, x, y, width, height)
        var textY = y + PANEL_MARGIN
        for (line in lines) {
            graphics.drawString(font, line, x + PANEL_MARGIN, textY, 0xFFFFFF, false)
            textY += lineHeight
        }
        val ruleY = textY + PANEL_RULE_GAP
        graphics.fill(x + PANEL_MARGIN, ruleY, x + width - PANEL_MARGIN, ruleY + 1, PANEL_RULE)
        val power = if (move.power > 0) move.power.roundToInt().toString() else "-"
        val accuracy = if (move.accuracy > 0 && move.accuracy <= 100) "${move.accuracy.roundToInt()}%" else "-"
        graphics.drawString(font, Component.translatable("cbpr.preview.stats", power, accuracy), x + PANEL_MARGIN, ruleY + 1 + PANEL_RULE_GAP, STATS_COLOR, false)
    }

    private fun drawPartyPanel(graphics: GuiGraphics, pokemon: Pokemon) {
        val slot = intake.register(BannerKind.PREVIEW, Rect(0.0, 0.0, 0.0, 0.0)) ?: return
        val kind = slot.kind
        val font = Minecraft.getInstance().font
        val x = slot.art.x.toInt() + kind.padLeft
        val y = slot.art.y.toInt() + kind.padTop
        val width = PARTY_PANEL_WIDTH
        previewArtWidth = width + kind.padLeft + kind.padRight
        val textWidth = width - 2 * PANEL_MARGIN
        val powerLabel = Component.translatable("cbpr.preview.power_short").string
        val accuracyLabel = Component.translatable("cbpr.preview.accuracy_short").string
        val moves = pokemon.moveSet.getMoves().take(MAX_PARTY_MOVES)
        val rows = moves.map { move ->
            PartyPreview.row(
                MoveView(
                    move.displayName.string, move.type.primaryColor and 0xFFFFFF,
                    move.damageCategory.displayName.string, move.power, move.accuracy, move.currentPp, move.maxPp,
                ),
                powerLabel, accuracyLabel,
            )
        }
        val lineHeight = font.lineHeight
        val body = if (rows.isEmpty()) lineHeight else rows.size * (2 * lineHeight) + (rows.size - 1) * PARTY_ROW_GAP
        val height = PANEL_MARGIN * 2 + body
        previewArtHeight = height + kind.padTop + kind.padBottom
        Panel.draw(graphics, x, y, width, height)
        var textY = y + PANEL_MARGIN
        if (rows.isEmpty()) {
            graphics.drawString(font, Component.translatable("cbpr.preview.no_moves"), x + PANEL_MARGIN, textY, PARTY_DETAIL_COLOR, false)
        }
        for ((index, row) in rows.withIndex()) {
            if (index > 0) {
                graphics.fill(x + PANEL_MARGIN, textY - PARTY_ROW_GAP / 2, x + width - PANEL_MARGIN, textY - PARTY_ROW_GAP / 2 + 1, PANEL_RULE)
            }
            val ppColor = when (row.ppState) {
                PpState.FULL -> STATS_COLOR
                PpState.LOW -> PP_LOW_COLOR
                PpState.EMPTY -> PP_EMPTY_COLOR
            }
            val ppText = Component.translatable("cbpr.preview.pp", row.pp.substringBefore('/'), row.pp.substringAfter('/')).string
            val ppWidth = font.width(ppText)
            graphics.drawString(font, ppText, x + width - PANEL_MARGIN - ppWidth, textY, ppColor, false)

            TypeIcon(x + PANEL_MARGIN, textY, moves[index].type, null, false, true, 0f, 0f, 1f).render(graphics)
            val nameX = x + PANEL_MARGIN + TYPE_ICON_SIZE + TYPE_ICON_GAP
            val name = PreviewText.limit(listOf(row.name), 1, { font.width(it) }, textWidth - ppWidth - PANEL_MARGIN - TYPE_ICON_SIZE - TYPE_ICON_GAP).first()
            graphics.drawString(font, name, nameX, textY, row.color, false)

            graphics.drawString(font, PartyPreview.fit(row.details, { font.width(it) }, textWidth), x + PANEL_MARGIN, textY + lineHeight, PARTY_DETAIL_COLOR, false)
            textY += 2 * lineHeight + PARTY_ROW_GAP
        }
    }

    class ScreenPoint(val x: Double, val y: Double, val distance: Double)

    fun screenPoint(point: Point): ScreenPoint? {
        if (!world.valid) return null
        val (x, y) = ndc(point) ?: return null
        return ScreenPoint(x, y, (point - world.cameraPos).length())
    }

    private fun ndc(point: Point): Pair<Double, Double>? {
        val rel = point - world.cameraPos
        val v = Vector4f(rel.x.toFloat(), rel.y.toFloat(), rel.z.toFloat(), 1f).mul(world.view).mul(world.projection)
        return if (v.w <= 1e-4f) null else (v.x / v.w).toDouble() to (v.y / v.w).toDouble()
    }

    private fun drawWorld(quads: List<SceneQuad>) {
        if (quads.isEmpty()) return
        val saved = RenderPass.enterWorld(world.projection, world.view)
        try {
            beginTextured()
            val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
            for (quad in quads) addQuad(buffer, quad)
            BufferUploader.drawWithShader(buffer.buildOrThrow())
        } finally {
            RenderPass.leave(saved)
            endTextured()
        }
    }

    private fun addQuad(buffer: VertexConsumer, quad: SceneQuad) {
        val u0 = (quad.region.x / canvasGuiWidth).toFloat()
        val u1 = ((quad.region.x + quad.region.width) / canvasGuiWidth).toFloat()

        val vTop = (1.0 - quad.region.y / guiHeight).toFloat()
        val vBottom = (1.0 - (quad.region.y + quad.region.height) / guiHeight).toFloat()

        val a = (quad.alpha * 255).toInt()
        vertex(buffer, quad.plane.corner(-1, 1), u0, vTop, a)
        vertex(buffer, quad.plane.corner(-1, -1), u0, vBottom, a)
        vertex(buffer, quad.plane.corner(1, -1), u1, vBottom, a)
        vertex(buffer, quad.plane.corner(1, 1), u1, vTop, a)
    }

    private fun vertex(buffer: VertexConsumer, corner: Point, u: Float, v: Float, alpha: Int) {
        val at = corner - world.cameraPos
        buffer.addVertex(at.x.toFloat(), at.y.toFloat(), at.z.toFloat()).setUv(u, v).setColor(alpha, alpha, alpha, alpha)
    }

    private fun drawLog(log: Rect, shift: Double) {
        val u0 = (log.x / canvasGuiWidth).toFloat()
        val u1 = ((log.x + log.width) / canvasGuiWidth).toFloat()
        val vTop = (1.0 - log.y / guiHeight).toFloat()
        val vBottom = (1.0 - (log.y + log.height) / guiHeight).toFloat()
        val left = log.x.toFloat()
        val right = (log.x + log.width).toFloat()
        val top = (log.y - shift).toFloat()
        val bottom = (log.y + log.height - shift).toFloat()
        val identity = Matrix4f()
        try {
            beginTextured()
            val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
            buffer.addVertex(identity, left, top, 0f).setUv(u0, vTop).setColor(255, 255, 255, 255)
            buffer.addVertex(identity, left, bottom, 0f).setUv(u0, vBottom).setColor(255, 255, 255, 255)
            buffer.addVertex(identity, right, bottom, 0f).setUv(u1, vBottom).setColor(255, 255, 255, 255)
            buffer.addVertex(identity, right, top, 0f).setUv(u1, vTop).setColor(255, 255, 255, 255)
            BufferUploader.drawWithShader(buffer.buildOrThrow())
        } finally {
            endTextured()
        }
    }

    private fun beginTextured() {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader)
        RenderSystem.setShaderTexture(0, canvas.textureId)
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)
        RenderSystem.disableCull()
        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
    }

    private fun endTextured() {
        RenderSystem.depthMask(true)
        RenderSystem.enableDepthTest()
        RenderSystem.enableCull()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
    }

    private fun forward(send: (MenuPointer.Cursor) -> Unit): Boolean {
        if (!hosting) return true
        cursor?.let(send)
        return false
    }

    private fun stopHosting() {
        hosting = false
        logShift = 0.0
        pendingLog = null
        pendingQuads = emptyList()
        cursor = null
        ceilingLogged = false
        noSubjectLogged = false
        battleScreen = BattleScreen.NONE
        intake.clear()
        addons = emptyMap()
        hostedAddons.clear()
        failedAddons.clear()
        loggedClasses.clear()
        scene = MenuScene()
    }

    private const val OFF_SCREEN = -1000
    private const val CLICK_SOUND_WINDOW_NANOS = 300_000_000L
    private const val OPTION_CLICK_WINDOW_NANOS = 800_000_000L
    private val CLICK_COBBLEMON = ResourceLocation.fromNamespaceAndPath("cobblemon", "gui.click")
    private val CLICK_VANILLA = ResourceLocation.withDefaultNamespace("ui.button.click")

    private const val PANEL_RULE = Panel.RULE
    private const val STATS_COLOR = Panel.STATS_COLOR
    private const val PANEL_LINES = 6
    private const val PARTY_PANEL_WIDTH = 200
    private const val MAX_PARTY_MOVES = 4
    private const val PARTY_ROW_GAP = 5

    private const val TYPE_ICON_SIZE = 9
    private const val TYPE_ICON_GAP = 3
    private const val PARTY_DETAIL_COLOR = 0xFFAAAAAA.toInt()
    private const val PP_LOW_COLOR = 0xFFFFAA55.toInt()
    private const val PP_EMPTY_COLOR = 0xFFFF5555.toInt()
    private const val PANEL_MARGIN = 5
    private const val PANEL_RULE_GAP = 3
}
