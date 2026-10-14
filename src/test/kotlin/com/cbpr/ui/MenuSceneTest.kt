package com.cbpr.ui

import com.cbpr.camera.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MenuSceneTest {
    private val position = Point(0.0, 0.0, 0.0)
    private val dt = 1.0 / 240
    private val real = Rect(20.0, 220.0, 90.0, 26.0)

    private val log = Rect(-500.0, -500.0, 1.0, 1.0)

    private fun project(p: Point): Pair<Double, Double>? {
        val depth = -(p.z - position.z)
        return if (depth <= 0.01) null else (p.x - position.x) / depth to (p.y - position.y) / depth
    }

    private fun camera(step: Double = dt) = CameraFrame(
        position, Point(1.0, 0.0, 0.0), Point(0.0, 1.0, 0.0), Point(0.0, 0.0, -1.0), 1.0, 1.0, step, ::project,
    )

    private fun rawAt(point: Point): Pair<Double, Double> {
        val (x, y) = project(point)!!
        return (x + 1.0) / 2.0 * 800.0 to (1.0 - y) / 2.0 * 600.0
    }

    private fun input(
        screen: BattleScreen,
        token: Any? = screen,
        subject: Subject = Subject(Point(0.0, 0.0, -4.0), 0.8, 1.0),
        step: Double = dt,
        raw: Pair<Double, Double> = 400.0 to 300.0,
    ) = SceneInput(
        screen = screen, screenToken = token, subject = subject, camera = camera(step), reducedMotion = false,
        rawX = raw.first, rawY = raw.second, windowWidth = 800.0, windowHeight = 600.0, guiWidth = 400.0, guiHeight = 300.0,
        log = log, logShift = 0.0,
    )

    private fun slots(screen: BattleScreen, vararg kinds: BannerKind): List<BannerSlot> {
        val intake = BannerSlots().also { it.begin(400.0, kinds.size, screen) }
        kinds.forEach { intake.register(it, real) }
        return intake.slots.toList()
    }

    private fun options(extras: List<BannerKind> = listOf(BannerKind.BACK), addons: Int = 0) =
        slots(BattleScreen.GENERAL, *(List(4) { BannerKind.OPTION } + List(addons) { BannerKind.ADDON } + extras).toTypedArray())
    private fun moves(gimmicks: Int, tiles: Int = 4, addons: Int = 0) =
        slots(BattleScreen.MOVES, *(List(tiles) { BannerKind.MOVE } + BannerKind.BACK + List(gimmicks) { BannerKind.GIMMICK } + List(addons) { BannerKind.ADDON }).toTypedArray())

    private class Frame(val opening: Opening, val placement: Placement) {
        fun quad(handle: SlotHandle) = placement.quads.firstOrNull { it.handle == handle }
        val events get() = opening.events + placement.events
    }

    private fun MenuScene.frame(input: SceneInput, slots: List<BannerSlot>): Frame {
        val opening = beginFrame(input)
        return Frame(opening, place(slots))
    }

    private fun MenuScene.settle(screen: BattleScreen, slots: List<BannerSlot>, token: Any? = screen, subject: Subject = Subject(Point(0.0, 0.0, -4.0), 0.8, 1.0)): Frame {
        var last: Frame? = null
        repeat(240) { last = frame(input(screen, token, subject, 1.0 / 60), slots) }
        return last!!
    }

    private fun MenuScene.appearances(screen: BattleScreen, token: Any?, slots: List<BannerSlot>, seconds: Double = 1.0): Map<SlotHandle, Double> {
        val first = HashMap<SlotHandle, Double>()
        var t = 0.0
        while (t < seconds) {
            val f = frame(input(screen, token), slots)
            t += dt
            for (quad in f.placement.quads) quad.handle?.let { first.putIfAbsent(it, t) }
        }
        return first
    }

    @Test
    fun theMainMenuPopsInOneButtonAfterAnotherAndTheBackButtonTakesTheRowAfterTheLastTile() {
        val scene = MenuScene()
        val slots = options()
        val seen = scene.appearances(BattleScreen.GENERAL, "menu", slots)
        val tiles = (0 until 4).map { seen.getValue(SlotHandle(BannerKind.OPTION, it)) }
        val back = seen.getValue(SlotHandle(BannerKind.BACK, 0))
        for (i in 0 until 3) assertEquals(BannerMotion.STAGGER_SECONDS, tiles[i + 1] - tiles[i], 0.01, "tile ${i + 1} follows tile $i")
        assertEquals(BannerMotion.STAGGER_SECONDS, back - tiles[3], 0.01, "back takes the turn after the last tile")
    }

    @Test
    fun aNewMenuPopsAgainWithTheBackRowAndTheGimmicksOnTheLastTilesTurn() {
        val scene = MenuScene()
        scene.settle(BattleScreen.GENERAL, options(), "menu")

        val slots = moves(2)
        val seen = HashMap<SlotHandle, Double>()
        val events = ArrayList<SceneEvent>()
        var t = 0.0
        while (t < 1.0) {
            val f = scene.frame(input(BattleScreen.MOVES, "moves"), slots)
            t += dt
            events += f.events
            for (quad in f.placement.quads) quad.handle?.let { seen.putIfAbsent(it, t) }
        }
        val lastTile = seen.getValue(SlotHandle(BannerKind.MOVE, 3))
        assertTrue(seen.getValue(SlotHandle(BannerKind.MOVE, 0)) < lastTile)
        for (extra in listOf(SlotHandle(BannerKind.BACK, 0), SlotHandle(BannerKind.GIMMICK, 0), SlotHandle(BannerKind.GIMMICK, 1))) {
            assertEquals(lastTile, seen.getValue(extra), 0.01, "$extra")
        }
        assertEquals(1, events.filterIsInstance<SceneEvent.FightMenuOpened>().size)
        assertEquals(7, events.filterIsInstance<SceneEvent.FightMenuOpened>().single().buttons)
    }

    @Test
    fun aClickDuringTheEntranceHitsTheButtonAtItsFinalPlace() {
        val scene = MenuScene()
        scene.settle(BattleScreen.GENERAL, options(), "menu")
        val slots = moves(0)
        val popping = scene.frame(input(BattleScreen.MOVES, "moves"), slots)
        val last = SlotHandle(BannerKind.MOVE, 3)
        assertTrue(last in popping.placement.entering, "still entering")
        assertNull(popping.quad(last), "nothing to draw yet")
        val target = popping.placement.hits.single { it.handle == last }
        val next = scene.frame(input(BattleScreen.MOVES, "moves", raw = rawAt(target.plane.center)), slots)
        assertEquals(last, scene.hovered)
        assertNotNull(next.opening.cursor)
    }

    @Test
    fun theBackAndGimmickRowHoldsZeroOneTwoOrFiveButtonsInOneLineWithoutOverlap() {
        val backAlone = MenuScene().settle(BattleScreen.MOVES, moves(0)).quad(SlotHandle(BannerKind.BACK, 0))!!.plane
        for (count in listOf(0, 1, 2, 5)) {
            val frame = MenuScene().settle(BattleScreen.MOVES, moves(count))
            val back = frame.quad(SlotHandle(BannerKind.BACK, 0))!!.plane
            val row = listOf(back) + (0 until count).map { frame.quad(SlotHandle(BannerKind.GIMMICK, it))!!.plane }
            assertEquals(count, frame.placement.quads.count { it.handle?.kind == BannerKind.GIMMICK }, "$count gimmick planes")
            for (plane in row) assertEquals(back.center.y, plane.center.y, back.halfHeight, "$count: one line")
            val byOutward = row.sortedBy { -it.center.x }
            for (i in 0 until byOutward.size - 1) {
                val gap = byOutward[i].center.x - byOutward[i + 1].center.x
                assertTrue(gap > byOutward[i].halfWidth + byOutward[i + 1].halfWidth, "$count: ${i + 1} clear of ${i + 2}")
            }
            if (count <= 2) assertEquals(backAlone.center.x, back.center.x, 1e-9, "$count: back stays put")
            assertOnScreen(frame, "$count gimmicks")
        }
    }

    private fun assertOnScreen(frame: Frame, label: String) {
        for (quad in frame.placement.quads.filter { it.handle != null }) for ((sx, sy) in listOf(1 to 1, -1 to 1, -1 to -1, 1 to -1)) {
            val (x, y) = project(quad.plane.corner(sx, sy))!!
            assertTrue(x in -BannerFit.LIMIT_X - 1e-6..BannerFit.LIMIT_X + 1e-6, "$label: x $x")
            assertTrue(y in -BannerFit.LIMIT_BOTTOM - 1e-6..BannerFit.LIMIT_TOP + 1e-6, "$label: y $y")
        }
    }

    @Test
    fun aCameraSoCloseThatTheStackRunsOffTheScreenGetsItBroughtBack() {
        val close = Subject(Point(0.0, 0.0, -0.8), 0.8, 1.0)
        val frame = MenuScene().settle(BattleScreen.MOVES, moves(2), subject = close)
        assertOnScreen(frame, "close")
    }

    @Test
    fun theLeftoverPlaneIsLevelWithTheSubjectAloneAndDropsBelowTheStackWhenThereAreBanners() {
        val subject = Subject(Point(0.0, 0.0, -4.0), 0.8, 1.0)
        val alone = MenuScene().settle(BattleScreen.TARGET, emptyList())
        val leftoverAlone = alone.placement.quads.single { it.handle == null }.plane
        assertEquals(subject.centre.y, leftoverAlone.center.y, 1e-9)

        val with = MenuScene().settle(BattleScreen.GENERAL, options())
        val leftover = with.placement.quads.single { it.handle == null }.plane
        val perPixel = MenuLayout.scaleFor(4.0) * BannerLayout.sizeFactor(subject)
        assertTrue(subject.centre.y - leftover.center.y > BannerLayout.stackHalfHeight(BannerKind.OPTION, 4, perPixel), "below the stack")
        assertTrue(leftover.halfWidth < leftoverAlone.halfWidth, "and smaller")
    }

    @Test
    fun aStackOfMixedKindsGivesEveryBannerItsOwnPlace() {
        val frame = MenuScene().settle(BattleScreen.GENERAL, slots(BattleScreen.GENERAL, BannerKind.OPTION, BannerKind.OPTION, BannerKind.ADDON, BannerKind.ADDON, BannerKind.BACK))
        val centres = frame.placement.quads.filter { it.handle != null && it.handle.kind != BannerKind.BACK }.map { it.plane.center }
        assertEquals(4, centres.size)
        assertEquals(4, centres.distinct().size, "no two share a place")
    }

    @Test
    fun theHostHearsAboutTheMenuThroughEventsAndNotThroughSounds() {
        val scene = MenuScene()
        val slots = options()
        assertTrue(SceneEvent.PanelOpened in scene.frame(input(BattleScreen.GENERAL, "menu"), slots).events)
        assertTrue(scene.frame(input(BattleScreen.GENERAL, "menu"), slots).events.none { it === SceneEvent.PanelOpened })
        scene.settle(BattleScreen.GENERAL, slots, "menu")
        val target = scene.frame(input(BattleScreen.GENERAL, "menu"), slots).placement.hits.first()
        val hovering = scene.frame(input(BattleScreen.GENERAL, "menu", raw = rawAt(target.plane.center)), slots)
        assertEquals(target.handle, hovering.events.filterIsInstance<SceneEvent.Hovered>().single().handle)
        assertTrue(scene.frame(input(BattleScreen.NONE, null), emptyList()).events.any { it === SceneEvent.PanelClosed })
    }

    @Test
    fun aMoveIsOnlyHoveredForTheCameraOnceTheCursorHasMovedSinceTheMenuOpened() {
        val slots = moves(0)

        val probe = MenuScene()
        probe.settle(BattleScreen.GENERAL, options(), "menu")
        val spot = rawAt(probe.settle(BattleScreen.MOVES, slots, "probe").placement.hits.first().plane.center)

        val scene = MenuScene()
        scene.settle(BattleScreen.GENERAL, options(), "menu")

        repeat(120) { scene.frame(input(BattleScreen.MOVES, "moves", step = 1.0 / 60, raw = spot), slots) }
        assertEquals(BannerKind.MOVE, scene.hovered?.kind)
        assertFalse(scene.hoveringMove(), "the cursor has not moved")
        scene.frame(input(BattleScreen.MOVES, "moves", raw = spot.first + 12.0 to spot.second), slots)
        assertTrue(scene.hoveringMove(), "now it has")
    }

    @Test
    fun anUnknownWidgetOnTheMainMenuContinuesTheStackUnderTheFourButtonsAndBackFollowsIt() {
        val frame = MenuScene().settle(BattleScreen.GENERAL, options(addons = 1))
        val tiles = (0 until 4).map { frame.quad(SlotHandle(BannerKind.OPTION, it))!!.plane } + frame.quad(SlotHandle(BannerKind.ADDON, 0))!!.plane
        val step = tiles[0].center.y - tiles[1].center.y
        for (i in 0 until 4) assertEquals(step, tiles[i].center.y - tiles[i + 1].center.y, 1e-9, "row ${i + 1} follows row $i")
        assertEquals(tiles[0].halfWidth, tiles[4].halfWidth, 1e-9, "same size")
        assertTrue(frame.quad(SlotHandle(BannerKind.BACK, 0))!!.plane.center.y < tiles[4].center.y, "Back is under the new last row")
        assertOnScreen(frame, "main menu")
    }

    @Test
    fun anUnknownWidgetOnTheMovesAndPartyScreensSitsInTheExtrasRowBesideBackAndKeepsTheMovesWhereTheyWere() {
        for (screen in listOf(BattleScreen.MOVES, BattleScreen.PARTY)) {
            val kind = if (screen == BattleScreen.MOVES) BannerKind.MOVE else BannerKind.SWITCH
            val without = MenuScene().settle(screen, slots(screen, *(List(4) { kind } + BannerKind.BACK).toTypedArray()))
            val with = MenuScene().settle(screen, slots(screen, *(List(4) { kind } + BannerKind.BACK + BannerKind.ADDON).toTypedArray()))
            for (i in 0 until 4) assertEquals(without.quad(SlotHandle(kind, i))!!.plane.center, with.quad(SlotHandle(kind, i))!!.plane.center, "$screen row $i")
            val back = with.quad(SlotHandle(BannerKind.BACK, 0))!!.plane
            val addon = with.quad(SlotHandle(BannerKind.ADDON, 0))!!.plane
            assertEquals(back.center.y, addon.center.y, back.halfHeight, "$screen: one line")

            val padding = addon.halfWidth * 2 * BannerKind.ADDON.padLeft / BannerKind.ADDON.fullWidth
            assertTrue(addon.center.x - addon.halfWidth + padding >= back.center.x + back.halfWidth - padding, "$screen: beside Back, outward, not overlapping")
            assertOnScreen(with, "$screen")
        }
    }

    @Test
    fun theTriplesShiftButtonSitsNextToBackInOneLineWithTheGimmickButtons() {
        for (gimmicks in listOf(0, 1, 2)) {
            val kinds = List(4) { BannerKind.MOVE } + BannerKind.BACK + List(gimmicks) { BannerKind.GIMMICK } + BannerKind.SHIFT
            val frame = MenuScene().settle(BattleScreen.MOVES, slots(BattleScreen.MOVES, *kinds.toTypedArray()))
            val back = frame.quad(SlotHandle(BannerKind.BACK, 0))!!.plane
            val shift = frame.quad(SlotHandle(BannerKind.SHIFT, 0))!!.plane
            val row = listOf(back) + (0 until gimmicks).map { frame.quad(SlotHandle(BannerKind.GIMMICK, it))!!.plane } + shift
            for (plane in row) assertEquals(back.center.y, plane.center.y, back.halfHeight, "$gimmicks gimmicks: one line")
            val reach = row.maxOf { kotlin.math.abs(it.center.x - back.center.x) }
            assertTrue(reach < back.halfWidth * 2 * (row.size + 2), "$gimmicks gimmicks: the row stays close to Back ($reach)")
        }
    }

    @Test
    fun theBackShiftAndGimmickButtonsFormOneStraightTiltedLineLikeTheBannersAbove() {
        val kinds = List(4) { BannerKind.MOVE } + BannerKind.BACK + BannerKind.SHIFT + BannerKind.GIMMICK + BannerKind.GIMMICK
        val frame = MenuScene().settle(BattleScreen.MOVES, slots(BattleScreen.MOVES, *kinds.toTypedArray()))
        fun bottom(handle: SlotHandle) = frame.quad(handle)!!.plane.let { project(it.corner(-1, -1))!! to project(it.corner(1, -1))!! }
        fun slope(edge: Pair<Pair<Double, Double>, Pair<Double, Double>>) = (edge.second.second - edge.first.second) / (edge.second.first - edge.first.first)
        val row = listOf(SlotHandle(BannerKind.BACK, 0), SlotHandle(BannerKind.SHIFT, 0), SlotHandle(BannerKind.GIMMICK, 0), SlotHandle(BannerKind.GIMMICK, 1)).map(::bottom)

        for (i in 0 until row.size - 1) {
            val line = row[i].second.second + slope(row[i]) * (row[i + 1].first.first - row[i].second.first)
            assertEquals(line, row[i + 1].first.second, 1.5e-3, "button ${i + 2} continues the line of button ${i + 1}")
        }

        val banner = slope(bottom(SlotHandle(BannerKind.MOVE, 3)))
        assertTrue(banner < 0 && row.all { slope(it) < 0 && kotlin.math.abs(slope(it) - banner) < 0.03 }, "row slopes ${row.map(::slope)} against the banner's $banner")
    }

    @Test
    fun theExtrasRowShrinksItsButtonsToFitAndBackStaysPut() {
        val one = MenuScene().settle(BattleScreen.MOVES, moves(1))
        val five = MenuScene().settle(BattleScreen.MOVES, moves(5))
        val lastTile = five.quad(SlotHandle(BannerKind.MOVE, 3))!!.plane
        val row = listOf(SlotHandle(BannerKind.BACK, 0)) + (0 until 5).map { SlotHandle(BannerKind.GIMMICK, it) }
        val planes = row.map { five.quad(it)!!.plane }
        assertTrue(five.quad(SlotHandle(BannerKind.GIMMICK, 0))!!.plane.halfWidth < one.quad(SlotHandle(BannerKind.GIMMICK, 0))!!.plane.halfWidth, "five are smaller than one")
        val extent = planes.maxOf { it.center.x + it.halfWidth } - planes.minOf { it.center.x - it.halfWidth }
        assertTrue(extent <= lastTile.halfWidth * 2 * 1.001, "the row is no wider than a banner")
        assertEquals(one.quad(SlotHandle(BannerKind.BACK, 0))!!.plane.center.x, planes[0].center.x, 1e-9, "Back stays put")
    }

    @Test
    fun moreWidgetsThanTheCeilingLeaveTheExcessUnhostedAndTheMenuIntact() {
        val count = BannerSlots.MAX_HOSTED + 10
        val screen = BattleScreen.MOVES
        val intake = BannerSlots().also { it.begin(400.0, 4 + 1 + count, screen) }
        repeat(4) { intake.register(BannerKind.MOVE, real) }
        intake.register(BannerKind.BACK, real)
        val results = List(count) { intake.register(BannerKind.ADDON, real) }
        assertEquals(BannerSlots.MAX_HOSTED - 5, results.count { it != null })
        val frame = MenuScene().settle(screen, intake.slots.toList())
        assertEquals(BannerSlots.MAX_HOSTED, frame.placement.quads.count { it.handle != null })
    }

    @Test
    fun aWidgetThatThrowsIsDroppedAndTheRestKeepFloating() {
        val intake = BannerSlots().also { it.begin(400.0, 7, BattleScreen.MOVES) }
        repeat(4) { intake.register(BannerKind.MOVE, real) }
        intake.register(BannerKind.BACK, real)
        val broken = intake.register(BannerKind.ADDON, real)!!
        val fine = intake.register(BannerKind.ADDON, real)!!
        intake.drop(broken)
        val frame = MenuScene().settle(BattleScreen.MOVES, intake.slots.toList())
        assertNull(frame.quad(broken.handle))
        assertNotNull(frame.quad(fine.handle))
        assertEquals(7 - 1, frame.placement.quads.count { it.handle != null })
        assertOnScreen(frame, "after the drop")
    }
}
