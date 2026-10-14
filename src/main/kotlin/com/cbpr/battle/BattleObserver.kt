package com.cbpr.battle

import com.cbpr.audio.Sfx
import com.cbpr.audio.SoundSlot
import com.cbpr.camera.BattleCamera
import com.cbpr.camera.BeatPlan
import com.cbpr.camera.Clearance
import com.cbpr.camera.BattleSnapshot
import com.cbpr.camera.CameraSpace
import com.cbpr.camera.OcclusionSelector
import com.cbpr.camera.Point
import com.cbpr.camera.Pose
import com.cbpr.camera.Sight
import com.cbpr.config.Settings
import com.cbpr.render.CinematicHud
import com.cbpr.render.MenuHost
import com.cbpr.render.OcclusionFade
import com.cbpr.ui.StatusCue
import com.cbpr.ui.StatusWatch
import com.cbpr.ui.Subject
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.client.battle.ClientBattle
import com.cobblemon.mod.common.client.battle.ClientBattleSide
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.client.gui.battle.BattleGUI
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleTargetSelection
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor

object BattleObserver {
    private val log = LoggerFactory.getLogger("cbpr")
    private val camera = BattleCamera()

    private val session = BattleSession<PokemonEntity, ClientBattlePokemon>()
    private val mon get() = session.mon
    private val foe get() = session.foe

    private val monBattler get() = session.monBattler
    private val foeBattler get() = session.foeBattler
    private val field get() = session.field
    private val handback get() = session.handback
    private val menuHold get() = session.menuHold
    private val presentation = BattlePresentation(mon = { session.mon }, foe = { session.foe }, field = session.field, entityAt = { slot -> session.combatant(slot)?.entity })
    private val director = ShotDirector(
        camera = camera,
        profiles = { ProfileLoader.profiles },
        settings = { Settings.current },
        lookupMove = ::lookupMove,
        mons = { roster(session.monOwner, session.mons) },
        foes = { roster(session.foeOwner, session.foes) },
        presentation = presentation,
        sizeOf = { monSide, slot -> ((slot?.let { session.combatant(it)?.entity } ?: if (monSide) mon else foe)?.size()) ?: 1.0 },

        trainerPosition = { Minecraft.getInstance().player?.takeIf { !spectating }?.position()?.let { Point(it.x, it.y, it.z) } },
    )

    private val spectating get() = CobblemonClient.battle?.spectating == true
    private val spectatorSide = SpectatorSide()

    private var level: ClientLevel? = null
    private var levelSight: Sight? = null
    private var levelClearance: Clearance? = null
    private val statusWatch = StatusWatch()

    private var debugLast: DebugSample? = null
    private var debugLines = 0
    private var debugTicks = 0
    private var lastShot: Pose? = null

    private val capture = CaptureWatch<EmptyPokeBallEntity>(
        balls = { Minecraft.getInstance().level?.entitiesForRendering()?.filterIsInstance<EmptyPokeBallEntity>()?.filter { !it.isRemoved } ?: emptyList() },
        positionOf = { ball, partialTick -> ball.takeIf { !it.isRemoved }?.getPosition(partialTick)?.let { Point(it.x, it.y, it.z) } },
    )
    private var lastFrameNanos = 0L
    private var fovOffset = 0.0
    private var barsShown = false
    private var failureLogged = false
    private var lastBeatKind: BeatPlan.Kind? = null

    fun tick(mc: Minecraft) {
        try {
            observe(mc)
        } catch (e: Exception) {
            giveUp("tick", e)
        }
    }

    fun reset() {
        CinematicHud.resetWash()
        camera.abort()
        OcclusionFade.reset()
        MenuHost.close()
        clearEffects()
        session.levelChanged()
        failureLogged = false
    }

    private fun clearEffects() {
        director.reset()
        BattleEvents.clear()
        CinematicHud.reset()
        fovOffset = 0.0
        barsShown = false
        Sfx.reset()
        statusWatch.clear()
        capture.clear()
    }

    @JvmStatic
    fun hostsMenu(): Boolean = isHostingShot() && !spectating

    @JvmStatic
    fun isShotActive(): Boolean = camera.phase != BattleCamera.Phase.OFF

    @JvmStatic
    fun isHostingShot(): Boolean = camera.phase == BattleCamera.Phase.INTRO || camera.phase == BattleCamera.Phase.COMMAND

    private fun commandedSlot(): String? =
        (Minecraft.getInstance().screen as? BattleGUI)?.getCurrentActionSelection()?.request?.activePokemon?.getPNX()

    private var lastCommandedSlot: String? = null
    private var commandedHoldSeconds = 0.0

    private fun heldCommanded(dt: Double): Combatant<PokemonEntity, ClientBattlePokemon>? {
        val now = commanded()
        if (now != null) {
            lastCommandedSlot = now.slot
            commandedHoldSeconds = COMMANDED_HOLD_SECONDS
            return now
        }
        commandedHoldSeconds = (commandedHoldSeconds - dt).coerceAtLeast(0.0)

        if (camera.acting || (commandedHoldSeconds <= 0.0 && !menuHold.held)) return null
        return lastCommandedSlot?.let(session::combatant)?.takeIf { it in session.mons && it.entity != null }
    }

    private val targetStep = TargetStep(session)

    private fun targetSelection(): BattleTargetSelection? = if (isHostingShot()) CobblemonScreen.selection() as? BattleTargetSelection else null

    @JvmStatic
    fun targetMarkers(partialTick: Float): List<TargetMarker> = targetStep.markers(targetSelection(), partialTick)

    @JvmStatic
    fun hoveredTarget(): String? = targetStep.hovered

    @JvmStatic
    fun clickHoveredTarget(): Boolean = targetStep.click(targetSelection(), MenuHost.cursorRay())

    private fun commanded(): Combatant<PokemonEntity, ClientBattlePokemon>? {
        val slot = commandedSlot() ?: return null
        return session.combatant(slot)?.takeIf { it in session.mons && it.entity != null }
    }

    @JvmStatic
    fun menuSubject(partialTick: Float): Subject? {
        val entity = commanded()?.entity ?: mon ?: return null
        val centre = entity.centre(partialTick) ?: return null
        return Subject(centre, entity.bbWidth.toDouble(), entity.bbHeight.toDouble())
    }

    class RingTarget(val slot: String, val own: Boolean, val feet: Point, val radius: Double, val statusRgb: Int?, val low: Boolean)

    @JvmStatic
    fun ringTargets(partialTick: Float): List<RingTarget> {
        val settings = Settings.current
        fun side(members: List<Combatant<PokemonEntity, ClientBattlePokemon>>, own: Boolean) = members.mapNotNull { member ->
            val entity = member.entity?.takeIf { !it.isRemoved } ?: return@mapNotNull null
            val feet = entity.getPosition(partialTick)
            val battler = member.battler
            val status = battler.takeIf { settings.statusCues }?.status?.name?.path?.let(StatusCue::rgb)
            val share = HpShare.of(battler.isHpFlat, battler.hpValue, battler.maxHp) ?: 1.0
            RingTarget(member.slot, own, Point(feet.x, feet.y, feet.z), (entity.bbWidth * 0.8).coerceIn(0.6, 3.5), status, settings.healthCue && share <= LOW_HEALTH)
        }
        return side(session.mons, true) + side(session.foes, false)
    }

    @JvmStatic
    fun isCommanded(slot: String): Boolean = commandedSlot()?.let { it == slot } ?: true

    @JvmStatic
    fun menuEngaged(): Boolean = camera.menuView

    @JvmStatic
    fun isGhosting(entity: Entity): Boolean = isShotActive() && entity === Minecraft.getInstance().player

    @JvmStatic
    fun fadeAlpha(entity: Entity): Float {
        if (session.mons.none { it.entity === entity } || !isHostingShot() || !Settings.current.environmentAdaptation) return 1f
        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position
        val middle = entity.position().add(0.0, entity.bbHeight / 2.0, 0.0)
        return CameraSpace.fadeAlpha(camera.distanceTo(middle), maxOf(entity.bbWidth, entity.bbHeight).toDouble()).toFloat()
    }

    @JvmStatic
    fun isTranslucent(entity: Entity): Boolean = fadeAlpha(entity) < 1f

    @JvmStatic
    fun renderColor(entity: Entity, color: Int): Int {
        val alpha = fadeAlpha(entity)
        return if (alpha < 1f) ((alpha * 255).toInt() shl 24) or (color and 0xFFFFFF) else color
    }

    @JvmStatic
    fun fovOffset(): Double = fovOffset

    @JvmStatic
    fun glowOutline(entity: Entity): Boolean {
        val s = Settings.current
        if (!s.experimentalRendering || !s.occlusionOutline || !isHostingShot()) return false
        return (session.mons.any { it.entity === entity } && camera.monHidden) || (session.foes.any { it.entity === entity } && camera.foeHidden)
    }

    @JvmStatic
    fun menuOpened() {
        Sfx.play(SoundSlot.MENU_OPEN)
    }

    @JvmStatic
    fun wantsBars(): Boolean = camera.wantsBars

    @JvmStatic
    fun tintTarget(): Double = camera.tint

    @JvmStatic
    fun flash(): Double = camera.flash

    @JvmStatic
    fun flashRgb(): Int = camera.flashRgb

    @JvmStatic
    fun washRgb(): Int? = if (Settings.current.enabled && Settings.current.weatherWash) field.current?.let(FieldWash::rgb) else null

    @JvmStatic
    fun shot(vanilla: Pose, partialTick: Float): Pose? {
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0.0 else ((now - lastFrameNanos) / 1e9).coerceIn(0.0, MAX_FRAME_SECONDS)
        lastFrameNanos = now
        return try {

            val screen = CobblemonScreen.of(CobblemonScreen.selection())
            val focus = Settings.current.menuCamera && menuHold.update(screen, camera.acting, dt)
            if (focus != camera.menuView) log.info("CBPR menu camera {} (screen {})", if (focus) "in" else "out", screen)
            camera.menuView = focus

            if (!menuHold.held) camera.hoverView = focus && MenuHost.hoveringMove() && !Settings.current.reducedMotion
            camera.parallax = Settings.current.parallax && !Settings.current.reducedMotion
            camera.reducedMotion = Settings.current.reducedMotion
            cursorToCamera(Minecraft.getInstance())

            val fading = Settings.current.occlusionFade && !OcclusionFade.failed
            camera.useSight(if (!fading && Settings.current.cameraAvoidance) levelSight else null)
            camera.useSpace(if (fading) null else levelClearance, Settings.current.environmentAdaptation)
            val owner = heldCommanded(dt)

            val targetSelection = targetSelection()
            targetStep.track(targetSelection, MenuHost.cursorRay())
            val battle = snapshot(partialTick, owner?.slot, targetSelection != null)
            camera.useBattle(battle)
            val events = BattleEvents.drain()
            if (isHostingShot()) director.handle(events, dt)
            val spots = capture.spots(partialTick)
            camera.update(dt, vanilla, spots?.first ?: battle.monCentre, spots?.second ?: battle.foeCentre).also {
                lastShot = it
                capture.settle(camera.beatKind == BeatPlan.Kind.CAPTURE)
                camera.beatKind.let { kind ->
                    if (kind != lastBeatKind) {
                        lastBeatKind = kind
                        if (kind != null) log.info("CBPR {} shot started", kind)
                    }
                }
                OcclusionFade.update(dt, if (fading && it != null) fadeWant(it, partialTick) else null)
                fovOffset = it?.fov ?: 0.0

                if (camera.wantsBars != barsShown) {
                    barsShown = camera.wantsBars
                    Sfx.play(SoundSlot.TRANSITION)
                }

                if (camera.released) session.ignoreTracked()
            }
        } catch (e: Exception) {
            giveUp("camera", e)
            null
        }
    }

    private fun snapshot(partialTick: Float, commandedSlot: String? = null, targetStep: Boolean = false): BattleSnapshot {
        fun side(members: List<Combatant<PokemonEntity, ClientBattlePokemon>>) = members.mapNotNull { combatant ->
            combatant.entity.centre(partialTick)?.let { BattleSnapshot.Member(combatant.slot, it, combatant.entity!!.size()) }
        }
        return BattleSnapshot(side(session.mons), side(session.foes), commandedSlot, targetStep)
    }

    private fun fadeWant(pose: Pose, partialTick: Float): OcclusionFade.Want? {
        fun targets(side: List<Combatant<PokemonEntity, ClientBattlePokemon>>) = side.mapNotNull { member ->
            val entity = member.entity ?: return@mapNotNull null
            val point = entity.centre(partialTick) ?: return@mapNotNull null
            OcclusionSelector.Target(point, entity.size() * 0.6 + 0.5, ceil(entity.size() / 3.0).toInt().coerceIn(1, 3)) to entity.y
        }
        val mine = targets(session.mons)
        val theirs = targets(session.foes)
        if (mine.isEmpty() || theirs.isEmpty()) return null
        val all = mine + theirs
        return OcclusionFade.Want(Point(pose.x, pose.y, pose.z), all.map { it.first }, floor(all.minOf { it.second }).toInt())
    }

    private fun cursorToCamera(mc: Minecraft) {
        if (mc.screen == null) {
            camera.cursorX = 0.0
            camera.cursorY = 0.0
            return
        }
        camera.cursorX = (mc.mouseHandler.xpos() / mc.window.screenWidth * 2.0 - 1.0).coerceIn(-1.0, 1.0)
        camera.cursorY = (mc.mouseHandler.ypos() / mc.window.screenHeight * 2.0 - 1.0).coerceIn(-1.0, 1.0)
    }

    private fun lookupMove(id: String): MoveInfo? =
        Moves.getByName(id)?.let { MoveInfo(id, it.damageCategory.name, it.elementalType.name, it.target.name) }

    private fun roster(owner: String, side: List<Combatant<PokemonEntity, ClientBattlePokemon>>) =
        MessageSubject.Roster(owner, side.map { MessageSubject.Named(it.slot, it.battler.displayName.string) })

    private fun titlePokemonNames(): Pair<Component, Component>? {
        val pokemon = (if (spectating) session.mons + session.foes else session.foes).map { it.battler }
        if (pokemon.isEmpty()) return null
        return joined(pokemon.map { it.displayName }, " & ") to joined(pokemon.map { Component.translatable("cobblemon.label.lv", it.level) }, "  ·  ")
    }

    private fun joined(parts: List<Component>, separator: String): Component =
        parts.drop(1).fold(parts.first().copy()) { all, next -> all.append(separator).append(next) }

    private class DebugSample(val mine: Vec3?, val foe: Vec3?, val trainer: Vec3?, val ids: String, val copies: String)

    private fun debugTrack(mc: Minecraft) {
        if (debugLines >= DEBUG_MAX_LINES || ++debugTicks % DEBUG_EVERY_TICKS != 0) return
        val level = mc.level ?: return
        val sample = DebugSample(
            mon?.position(), foe?.position(), mc.player?.position(),
            "mine=${mon?.id} foe=${foe?.id}",
            "mine=${copies(level, monBattler?.uuid)} foe=${copies(level, foeBattler?.uuid)}",
        )
        val before = debugLast
        fun moved(a: Vec3?, b: Vec3?) = (a == null) != (b == null) || (a != null && b != null && a.distanceTo(b) > DEBUG_MOVED)
        if (before != null && !moved(before.mine, sample.mine) && !moved(before.foe, sample.foe) && !moved(before.trainer, sample.trainer) &&
            before.ids == sample.ids && before.copies == sample.copies
        ) return
        debugLast = sample
        debugLines++
        fun fmt(v: Vec3?) = v?.let { "%.2f,%.2f,%.2f".format(it.x, it.y, it.z) } ?: "none"
        val cam = lastShot?.let { "%.2f,%.2f,%.2f yaw %.0f".format(it.x, it.y, it.z, it.yaw) } ?: "none"
        log.info("CBPR debug: mine {} foe {} trainer {} camera {} entities {} copies {} phase {} beat {}", fmt(sample.mine), fmt(sample.foe), fmt(sample.trainer), cam, sample.ids, sample.copies, camera.phase, camera.beatKind)
    }

    private fun copies(level: ClientLevel, id: UUID?): Int =
        if (id == null) 0 else level.entitiesForRendering().count { it is PokemonEntity && it.pokemon.uuid == id }

    private fun watchStatus(battler: ClientBattlePokemon?, monSide: Boolean, slot: String? = null) {
        battler ?: return
        val path = statusWatch.update(battler.uuid, battler.status?.name?.path) ?: return
        if (!Settings.current.statusCues || !isHostingShot()) return
        val rgb = StatusCue.rgb(path) ?: return
        val key = StatusCue.langKey(path)
        if (!Language.getInstance().has(key)) return
        presentation.cue(Component.translatable(key).string, rgb, monSide, slot)
    }

    private fun observe(mc: Minecraft) {
        val currentLevel = mc.level
        if (currentLevel !== level) {
            reset()
            level = currentLevel
            levelClearance = currentLevel?.let { lvl ->
                val viewer = mc.player
                Clearance { a, b ->
                    val from = Vec3(a.x, a.y, a.z)
                    val to = Vec3(b.x, b.y, b.z)
                    val hit = lvl.clip(ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer!!))
                    val total = from.distanceTo(to)
                    if (hit.type == HitResult.Type.MISS || total < 1e-6) 1.0 else from.distanceTo(hit.location) / total
                }
            }
            levelSight = currentLevel?.let { lvl ->
                val viewer = mc.player
                Sight { a, b -> lvl.clip(ClipContext(Vec3(a.x, a.y, a.z), Vec3(b.x, b.y, b.z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer!!)).type == HitResult.Type.MISS }
            }
        }
        val battle = CobblemonClient.battle
        if (battle == null) {
            if (session.tracked != null) {

                if (capture.conclude()) camera.concludeCapture()

                director.handle(BattleEvents.drain(), 0.0)
                camera.end()
            }
            director.reset()
            BattleEvents.clear()
            statusWatch.clear()

            session.battleEnded(keepSubjects = camera.busy)
            return
        }

        if (mc.player == null) return
        val view = CobblemonView(battle, mc, spectatorSide)
        when (session.admit(view)) {
            BattleSession.Admit.REFUSED -> return
            BattleSession.Admit.NEW -> {
                debugLines = 0
                debugLast = null
            }
            BattleSession.Admit.TRACKED -> Unit
        }

        if (!Settings.current.enabled) {
            if (camera.phase == BattleCamera.Phase.INTRO || camera.phase == BattleCamera.Phase.COMMAND) {
                camera.end()
                clearEffects()
            }
            session.framed = false
            return
        }

        if (handback.update(CobblemonScreen.of(CobblemonScreen.selection()), MenuHost.optionClickedRecently())) {
            log.info("CBPR handing the view back to the Trainer (Catch or Run)")
            camera.end()
            clearEffects()
        }
        if (Settings.current.debugLog) debugTrack(mc)
        if (handback.handedBack || capture.capturing) watchCapture(battle, mc)
        if (handback.handedBack) return
        if (handback.consumeResumed()) session.resuming = true
        if (session.findSubjects(view)) {
            session.mons.forEach { watchStatus(it.battler, true, it.slot) }
            session.foes.forEach { watchStatus(it.battler, false, it.slot) }
        }
        if (camera.phase == BattleCamera.Phase.OFF && mon != null && foe != null) {
            camera.begin(vanillaPose(mc))

            if (session.framed) session.resuming = true
            session.framed = true
            barsShown = camera.wantsBars
            if (!session.resuming && Settings.current.titleCard) titlePokemonNames()?.let { (name, level) -> CinematicHud.showPokemonName(name, level) }
            session.resuming = false
        } else if (camera.phase == BattleCamera.Phase.OFF) {
            if (session.waitedTooLong(TICK_SECONDS)) {
                log.info("CBPR skipped battle {}: Pokemon entities were not found in the client level", battle.battleId)
            }
        }
    }

    private fun watchCapture(battle: ClientBattle, mc: Minecraft) {
        val shaking = battle.sides.any { side -> side.activeClientBattlePokemon.any { it.ballCapturing != null } }
        when (capture.update(shaking)) {
            CaptureWatch.Change.WANTS_START -> startCapture(mc, battle.sides.flatMap { it.activeClientBattlePokemon }.firstOrNull { it.ballCapturing != null }?.getPNX())

            CaptureWatch.Change.ENDED -> {
                camera.concludeCapture()
                handback.resume()
            }
            CaptureWatch.Change.NONE -> Unit
        }
    }

    private fun startCapture(mc: Minecraft, targetSlot: String?) {
        val settings = Settings.current
        if (!settings.captureShot || !settings.cinematicShots || camera.phase == BattleCamera.Phase.INTRO || camera.phase == BattleCamera.Phase.COMMAND) return
        val mine = snapshot(0f).monCentre ?: return
        val aimed = targetSlot?.let { session.combatant(it)?.takeIf { c -> c in session.foes }?.entity } ?: foe
        val target = aimed?.takeIf { !it.isRemoved }?.getPosition(0f) ?: return
        capture.begin(mine, Point(target.x, target.y + CAPTURE_SPOT_LIFT, target.z))
        camera.beginCapture(vanillaPose(mc), BeatPlan(BeatPlan.Kind.CAPTURE, subjectIsMon = false, reducedMotion = settings.reducedMotion))
        barsShown = camera.wantsBars
        log.info("CBPR capture shot started")
    }

    private class CobblemonView(private val battle: ClientBattle, private val mc: Minecraft, private val spectatorSide: SpectatorSide) : BattleView<PokemonEntity, ClientBattlePokemon> {
        override val id: UUID get() = battle.battleId
        private val actor by lazy { battle.getParticipatingActor(mc.player!!.uuid) }

        private val monSide: ClientBattleSide? by lazy {
            if (!battle.spectating) actor?.side
            else spectatorSide.firstIsMon(id) { nearest(battle.side1) to nearest(battle.side2) }?.let { if (it) battle.side1 else battle.side2 }
        }
        private val foeSide get() = monSide?.let { mine -> if (battle.side1 === mine) battle.side2 else battle.side1 }

        override fun supported(): Boolean {
            val type = battle.battleFormat.battleType
            val format = SupportedFormats.supports(type.name, type.actorsPerSide, type.slotsPerActor, Settings.current.doublesBattles, Settings.current.triplesBattles)
            return format && if (battle.spectating) Settings.current.spectatorCamera else actor != null
        }

        override fun available(): Boolean = mc.level != null && monSide != null

        override fun mons(): List<BattleMember<ClientBattlePokemon>> = members(monSide?.activeClientBattlePokemon ?: emptyList())

        override fun foes(): List<BattleMember<ClientBattlePokemon>> = members(foeSide?.activeClientBattlePokemon ?: emptyList())

        override fun monOwner(): String = if (battle.spectating) ownerOf(monSide) else mc.player?.name?.string.orEmpty()

        override fun foeOwner(): String = ownerOf(foeSide)

        private fun ownerOf(side: ClientBattleSide?) = side?.actors?.firstOrNull()?.displayName?.string.orEmpty()

        private fun nearest(side: ClientBattleSide): Double? = members(side.activeClientBattlePokemon)
            .mapNotNull { entity(it.battler, null) }.minOfOrNull { it.distanceTo(mc.player!!).toDouble() }

        private fun members(slots: Iterable<ActiveClientBattlePokemon>): List<BattleMember<ClientBattlePokemon>> =
            slots.filter { it.hasPokemon() }.mapNotNull { slot -> slot.battlePokemon?.let { BattleMember(slot.getPNX(), it) } }

        override fun entity(battler: ClientBattlePokemon, current: PokemonEntity?): PokemonEntity? {
            val pokemonId = battler.uuid
            if (current != null && !current.isRemoved && current.pokemon.uuid == pokemonId) return current
            val level = mc.level ?: return null
            return level.entitiesForRendering().firstOrNull { it is PokemonEntity && it.pokemon.uuid == pokemonId } as PokemonEntity?
        }
    }

    private fun vanillaPose(mc: Minecraft): Pose {
        val cam = mc.gameRenderer.mainCamera
        val pos = cam.position
        return Pose(pos.x, pos.y, pos.z, cam.yRot.toDouble(), cam.xRot.toDouble())
    }

    private fun PokemonEntity.size() = maxOf(bbWidth, bbHeight).toDouble()

    private fun giveUp(where: String, e: Exception) {

        if (!failureLogged) log.error("CBPR {} failure, falling back to the vanilla view for battle {}", where, session.tracked, e)
        failureLogged = true
        session.fail()
        camera.abort()
        OcclusionFade.reset()
        clearEffects()
        session.clearSubjects()
    }

    private const val DEBUG_MAX_LINES = 300
    private const val DEBUG_EVERY_TICKS = 5
    private const val DEBUG_MOVED = 0.2
    private const val CAPTURE_SPOT_LIFT = 0.3

    private const val COMMANDED_HOLD_SECONDS = 0.8

    private const val LOW_HEALTH = 0.25
    private const val TICK_SECONDS = 0.05
    private const val MAX_FRAME_SECONDS = 0.1
}
