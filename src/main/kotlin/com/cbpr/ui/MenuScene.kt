package com.cbpr.ui

import com.cbpr.camera.Point
import com.cbpr.camera.Spring
import kotlin.math.max

enum class BattleScreen {
    NONE, GENERAL, MOVES, PARTY, TARGET, FORFEIT, OTHER;

    val floats: Boolean get() = this == GENERAL || this == MOVES || this == PARTY

    val sub: Boolean get() = this != NONE && this != GENERAL
}

class CameraFrame(
    val position: Point,
    val right: Point,
    val up: Point,
    val forward: Point,
    val tanHalfFovX: Double,
    val tanHalfFovY: Double,
    val dt: Double,
    val ndc: (Point) -> Pair<Double, Double>?,
)

class SceneInput(
    val screen: BattleScreen,
    val screenToken: Any?,
    val subject: Subject,
    val camera: CameraFrame,
    val reducedMotion: Boolean,
    val rawX: Double,
    val rawY: Double,
    val windowWidth: Double,
    val windowHeight: Double,
    val guiWidth: Double,
    val guiHeight: Double,
    val log: Rect,
    val logShift: Double,
    val targetBack: Rect? = null,
)

sealed interface SceneEvent {
    object PanelOpened : SceneEvent
    object PanelClosed : SceneEvent
    class Hovered(val handle: SlotHandle) : SceneEvent

    class FightMenuOpened(val buttons: Int) : SceneEvent
}

class Opening(val cursor: MenuPointer.Cursor?, val events: List<SceneEvent>)

class SceneQuad(val handle: SlotHandle?, val plane: MenuPlane, val region: Rect, val alpha: Double)

class Placement(
    val quads: List<SceneQuad>,
    val hits: List<PointerTarget>,
    val entering: Set<SlotHandle>,
    val menu: Rect,
    val events: List<SceneEvent>,
)

class MenuScene {
    private val motion = MenuMotion()

    private val banners = BannerMotion<Any>()
    private object Leftover

    private val pointer = MenuPointer()

    private val hoverArm = HoverArm()
    private val previewAnchor = Array(3) { Spring(0.0, PREVIEW_FOLLOW_STIFFNESS, Spring.dampingFor(PREVIEW_FOLLOW_STIFFNESS, 1.0)) }
    private var previewShown = false

    private val basePlanes = HashMap<SlotHandle, MenuPlane>()
    private var hits: List<PointerTarget> = emptyList()
    private var leftover: MenuPlane? = null
    private var lastScreen = BattleScreen.NONE
    private var lastToken: Any? = null
    private var wasMoves = false
    private var lastHovered: SlotHandle? = null
    private var panelOpen = false
    private var entranceDue = false
    private var input: SceneInput? = null
    private var lagged = Subject(Point(0.0, 0.0, 0.0), 0.0, 0.0)
    private var tilt = 0.0
    private var menu = Rect(0.0, 0.0, 0.0, 0.0)

    val hovered: SlotHandle? get() = pointer.hovered

    fun hoveringMove(): Boolean = hoverArm.armed && hovered?.kind == BannerKind.MOVE && hits.any { it.handle == hovered }

    fun beginFrame(input: SceneInput): Opening {
        this.input = input
        val events = ArrayList<SceneEvent>()
        val screen = input.screen
        val moves = screen == BattleScreen.MOVES
        if (!moves) pointer.releaseLock()

        entranceDue = screen.floats && lastScreen.floats && input.screenToken !== lastToken
        lastScreen = screen
        lastToken = input.screenToken
        if (moves && !wasMoves) hoverArm.begin(input.rawX, input.rawY)
        if (moves) hoverArm.update(input.rawX, input.rawY) else hoverArm.disarm()
        wasMoves = moves
        val active = screen != BattleScreen.NONE
        if (active != panelOpen) {
            panelOpen = active
            events += if (active) SceneEvent.PanelOpened else SceneEvent.PanelClosed
        }

        menu = when (screen) {
            BattleScreen.FORFEIT -> MenuRegions.forfeit(input.guiWidth, input.guiHeight)
            BattleScreen.TARGET -> input.targetBack?.let(::backRegion) ?: MenuRegions.menu(input.guiHeight)
            else -> MenuRegions.menu(input.guiHeight)
        }

        val frame = input.camera
        motion.step(frame.dt, input.subject.centre)
        lagged = Subject(motion.position, input.subject.width, input.subject.height)
        banners.reduced = input.reducedMotion

        tilt = if (input.reducedMotion) 0.0 else motion.tiltDegrees(frame.right.x, frame.right.y, frame.right.z)

        val cursor = pointer.map(
            PointerView(
                rawX = input.rawX, rawY = input.rawY, windowWidth = input.windowWidth, windowHeight = input.windowHeight,
                guiWidth = input.guiWidth, guiHeight = input.guiHeight,
                camera = frame.position, right = frame.right, up = frame.up, forward = frame.forward,
                tanHalfFovX = frame.tanHalfFovX, tanHalfFovY = frame.tanHalfFovY,
                log = input.log, logShift = input.logShift, menu = menu,
                targets = hits, leftover = leftover, ndc = frame.ndc,
            ),
        )
        if (hovered != lastHovered) {
            lastHovered = hovered
            hovered?.let { events += SceneEvent.Hovered(it) }
        }
        return Opening(cursor, events)
    }

    fun place(slots: List<BannerSlot>, previewWidth: Int = BannerKind.PREVIEW.fullWidth, previewHeight: Int = BannerKind.PREVIEW.fullHeight): Placement {
        val input = checkNotNull(input) { "place before beginFrame" }
        val frame = input.camera
        val events = ArrayList<SceneEvent>()
        val active = input.screen != BattleScreen.NONE
        val tiles = slots.filter { it.role == SlotRole.TILE }

        val turns = HashMap<SlotHandle, Int>()
        slots.forEach { turns[it.handle] = when (it.role) { SlotRole.PANEL -> 0; SlotRole.TILE -> tiles.indexOf(it); SlotRole.EXTRA -> tiles.size } }
        for (handle in banners.keys.filterIsInstance<SlotHandle>() + turns.keys) banners.setVisible(handle, active && handle in turns, turns[handle] ?: 0)
        banners.setVisible(Leftover, active)
        if (entranceDue) startEntrance(slots, tiles, input.screen, events)
        banners.step(frame.dt, hovered)

        val placed = placeBanners(frame, slots, tiles)

        leftover = placeLeftover(frame, tiles, placed.isNotEmpty())
            ?.let { if (input.screen == BattleScreen.FORFEIT || input.screen == BattleScreen.TARGET) keepOnScreen(frame, it) else it }

        val quads = ArrayList<SceneQuad>()
        leftover?.let { quads += SceneQuad(null, it, menu, banners.popScale(Leftover).coerceIn(0.0, 1.0)) }
        placed.forEach { quads += SceneQuad(it.slot.handle, it.plane, it.slot.art, banners.popScale(it.slot.handle).coerceIn(0.0, 1.0)) }

        quads.sortByDescending { (it.plane.center - frame.position).length() }

        placePreview(frame, slots, previewWidth, previewHeight)?.let { quads += it }
        return Placement(quads, hits, slots.map { it.handle }.filter(banners::isEntering).toSet(), menu, events)
    }

    private fun backRegion(button: Rect) = Rect(
        button.x - BACK_REGION_MARGIN, button.y - BACK_REGION_MARGIN,
        button.width + 2 * BACK_REGION_MARGIN, button.height + 2 * BACK_REGION_MARGIN,
    )

    private fun startEntrance(slots: List<BannerSlot>, tiles: List<BannerSlot>, screen: BattleScreen, events: MutableList<SceneEvent>) {
        val extras = slots.filter { it.role == SlotRole.EXTRA }
        val last = max(tiles.size - 1, 0)
        tiles.forEachIndexed { index, slot -> banners.replay(slot.handle, index) }
        for (slot in extras) banners.replay(slot.handle, last)
        if (screen == BattleScreen.MOVES) events += SceneEvent.FightMenuOpened(tiles.size + extras.size)
    }

    private class Placed(val slot: BannerSlot, val plane: MenuPlane)

    private fun placeBanners(frame: CameraFrame, slots: List<BannerSlot>, tiles: List<BannerSlot>): List<Placed> {
        val kind = tiles.firstOrNull()?.kind ?: return emptyList()
        val count = tiles.size
        val beside = slots.filter { it.role == SlotRole.EXTRA && it.kind != BannerKind.BACK }
        val planes = BannerLayout.arrange(kind, count, lagged, frame.position, frame.right, frame.up, frame.forward, tilt)
        val backPlane = BannerLayout.backPlane(kind, count, lagged, frame.position, frame.right, frame.up, frame.forward, tilt)
        val besidePlanes = BannerLayout.extraPlanes(kind, count, beside.map { it.kind }, lagged, frame.position, frame.right, frame.up, frame.forward, tilt)
        val shown = slots.filter { it.role != SlotRole.PANEL }

        val wanted = shown.associate { slot ->
            slot.handle to when (slot.role) {
                SlotRole.TILE -> planes[tiles.indexOf(slot)]
                else -> if (slot.kind == BannerKind.BACK) backPlane else besidePlanes[beside.indexOf(slot)]
            }
        }
        val resting = fitToScreen(frame, wanted)
        val targets = ArrayList<PointerTarget>()
        basePlanes.clear()
        val placed = shown.mapNotNull { slot ->
            val size = banners.scale(slot.handle)
            val entering = banners.isEntering(slot.handle)
            val plane = resting.getValue(slot.handle)
            basePlanes[slot.handle] = plane

            if (entering) targets += PointerTarget(slot.handle, slot.real, BannerLayout.hitPlane(plane, slot.kind))
            if (size <= MIN_SIZE) return@mapNotNull null
            val scaled = MenuPlane(plane.center, plane.right, plane.up, plane.halfWidth * size, plane.halfHeight * size, plane.skew)
            Placed(slot, scaled).also { if (!entering) targets += PointerTarget(slot.handle, slot.real, BannerLayout.hitPlane(scaled, slot.kind)) }
        }
        hits = targets
        return placed
    }

    private fun fitToScreen(frame: CameraFrame, planes: Map<SlotHandle, MenuPlane>): Map<SlotHandle, MenuPlane> {
        var current = planes
        repeat(2) {
            val bounds = screenBounds(frame, current.values) ?: return current
            val fit = BannerFit.compute(bounds)
            if (fit.unchanged) return current
            current = transformed(frame, current, fit)
        }
        return current
    }

    private fun keepOnScreen(frame: CameraFrame, plane: MenuPlane): MenuPlane {
        val bounds = screenBounds(frame, listOf(plane)) ?: return plane
        val fit = BannerFit.compute(bounds, allowScale = false)
        return if (fit.unchanged) plane else transformed(frame, mapOf(0 to plane), fit).getValue(0)
    }

    private fun screenBounds(frame: CameraFrame, planes: Collection<MenuPlane>): BannerFit.Bounds? {
        val points = ArrayList<Pair<Double, Double>>()
        for (plane in planes) for ((sx, sy) in CORNERS) points += frame.ndc(plane.corner(sx, sy)) ?: return null
        return BannerFit.boundsOf(points)
    }

    private fun <K> transformed(frame: CameraFrame, planes: Map<K, MenuPlane>, fit: BannerFit.Fit): Map<K, MenuPlane> {
        val all = planes.values
        val centroid = all.fold(Point(0.0, 0.0, 0.0)) { sum, plane -> sum + plane.center } * (1.0 / all.size)
        val depth = ((centroid - frame.position) dot frame.forward).coerceAtLeast(0.5)
        val shift = frame.right * (fit.shiftX * frame.tanHalfFovX * depth) + frame.up * (fit.shiftY * frame.tanHalfFovY * depth)
        return planes.mapValues { (_, plane) ->
            MenuPlane(centroid + (plane.center - centroid) * fit.scale + shift, plane.right, plane.up, plane.halfWidth * fit.scale, plane.halfHeight * fit.scale, plane.skew)
        }
    }

    private fun placeLeftover(frame: CameraFrame, tiles: List<BannerSlot>, bannersShown: Boolean): MenuPlane? {
        val pop = banners.popScale(Leftover)
        if (pop <= MIN_SIZE) return null
        val perPixel = MenuLayout.scaleFor((lagged.centre - frame.position).length()) * BannerLayout.sizeFactor(lagged)

        val boost = (SMALL_REGION_REFERENCE / menu.width).coerceIn(1.0, MAX_REGION_BOOST)
        val size = (if (bannersShown) LEFTOVER_SIZE else 1.0) * BannerLayout.sizeFactor(lagged) * boost
        val kind = tiles.firstOrNull()?.kind
        val drop = if (bannersShown && kind != null) {
            BannerLayout.stackHalfHeight(kind, tiles.size, perPixel) + menu.height * perPixel * size / 2 + LEFTOVER_GAP
        } else {
            0.0
        }
        return MenuLayout.place(
            lagged.centre, frame.position, frame.right, frame.up, frame.forward,
            menu.width, menu.height, size * pop, tilt, BannerLayout.clearance(lagged), drop,
        )
    }

    private fun placePreview(frame: CameraFrame, slots: List<BannerSlot>, artWidth: Int, artHeight: Int): SceneQuad? {
        val slot = slots.firstOrNull { it.role == SlotRole.PANEL }
        val base = hovered?.let { basePlanes[it] }
        val size = slot?.let { banners.scale(it.handle) } ?: 0.0
        if (slot == null || base == null || size <= MIN_SIZE) {
            previewShown = false
            return null
        }
        var plane = BannerLayout.previewPlane(base, lagged, frame.position, leftSide = false, artHeight, artWidth)
        val edge = frame.ndc(plane.center + plane.right * plane.halfWidth)?.first
        if (edge != null && edge > OFF_RIGHT) plane = BannerLayout.previewPlane(base, lagged, frame.position, leftSide = true, artHeight, artWidth)
        plane = keepOnScreen(frame, plane)
        val target = doubleArrayOf(plane.center.x, plane.center.y, plane.center.z)
        for (axis in 0..2) {

            if (!previewShown || input?.reducedMotion == true) previewAnchor[axis].snap(target[axis]) else previewAnchor[axis].step(target[axis], frame.dt)
        }
        previewShown = true
        val centre = Point(previewAnchor[0].value, previewAnchor[1].value, previewAnchor[2].value)
        val scaled = MenuPlane(centre, plane.right, plane.up, plane.halfWidth * size, plane.halfHeight * size, 0.0)
        val art = slot.art
        return SceneQuad(slot.handle, scaled, Rect(art.x, art.y, artWidth.toDouble(), artHeight.toDouble()), banners.popScale(slot.handle).coerceIn(0.0, 1.0))
    }

    private companion object {
        const val PREVIEW_FOLLOW_STIFFNESS = 180.0
        const val OFF_RIGHT = 0.92
        const val LEFTOVER_SIZE = 0.6
        const val SMALL_REGION_REFERENCE = 300.0
        const val MAX_REGION_BOOST = 3.0
        const val BACK_REGION_MARGIN = 4.0
        const val LEFTOVER_GAP = 0.1
        const val MIN_SIZE = 0.02
        val CORNERS = listOf(1 to 1, -1 to 1, -1 to -1, 1 to -1)
    }
}
