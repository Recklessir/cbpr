package com.cbpr.ui

import com.cbpr.camera.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MenuPointerTest {
    private val camera = Point(0.0, 0.0, 0.0)
    private val right = Point(1.0, 0.0, 0.0)
    private val up = Point(0.0, 1.0, 0.0)
    private val forward = Point(0.0, 0.0, 1.0)

    private val log = Rect(200.0, 200.0, 100.0, 60.0)
    private val menu = Rect(0.0, 100.0, 360.0, 200.0)
    private val real = Rect(20.0, 220.0, 92.0, 24.0)

    private fun plane(x: Double, y: Double, z: Double, halfWidth: Double = 2.0, halfHeight: Double = 1.0) =
        MenuPlane(Point(x, y, z), right, up, halfWidth, halfHeight)

    private fun project(p: Point) = ((p.x - camera.x) / (p.z - camera.z)) to ((p.y - camera.y) / (p.z - camera.z))

    private fun view(
        x: Double = 400.0,
        y: Double = 300.0,
        targets: List<PointerTarget> = emptyList(),
        leftover: MenuPlane? = null,
        logShift: Double = 0.0,
    ) = PointerView(
        rawX = x, rawY = y, windowWidth = 800.0, windowHeight = 600.0, guiWidth = 400.0, guiHeight = 300.0,
        camera = camera, right = right, up = up, forward = forward, tanHalfFovX = 1.0, tanHalfFovY = 1.0,
        log = log, logShift = logShift, menu = menu, targets = targets, leftover = leftover, ndc = ::project,
    )

    private fun move(index: Int, plane: MenuPlane) = PointerTarget(SlotHandle(BannerKind.MOVE, index), real, plane)

    @Test
    fun aButtonOverlappedByAnotherIsPointedAtOnItsFreePart() {

        val shift = Rect(42.0, 278.0, 36.0, 17.0)
        val gimmick = Rect(42.0, 278.0, 18.0, 17.0)
        val pointer = MenuPointer()
        val targets = listOf(
            PointerTarget(SlotHandle(BannerKind.SHIFT, 0), shift, plane(-3.0, 0.0, 10.0)),
            PointerTarget(SlotHandle(BannerKind.GIMMICK, 0), gimmick, plane(3.0, 0.0, 10.0)),
        )
        val onShift = pointer.map(view(x = 400.0 - 3.0 * 40.0, targets = targets))!!
        assertEquals(SlotHandle(BannerKind.SHIFT, 0), pointer.hovered)
        assertTrue(onShift.x in shift.x..shift.x + shift.width && onShift.x > gimmick.x + gimmick.width, "Shift: outside the gimmick button (${onShift.x})")
        val onGimmick = pointer.map(view(x = 400.0 + 3.0 * 40.0, targets = targets))!!
        assertEquals(SlotHandle(BannerKind.GIMMICK, 0), pointer.hovered)
        assertTrue(onGimmick.x in gimmick.x..gimmick.x + gimmick.width, "gimmick: inside its own rectangle")
    }

    @Test
    fun aButtonCoveredWholeKeepsItsCursor() {
        val inner = Rect(50.0, 280.0, 10.0, 10.0)
        val outer = Rect(40.0, 270.0, 40.0, 30.0)
        val cursor = MenuPointer().map(view(targets = listOf(PointerTarget(SlotHandle(BannerKind.GIMMICK, 0), inner, plane(0.0, 0.0, 10.0)), PointerTarget(SlotHandle(BannerKind.SHIFT, 0), outer, plane(50.0, 0.0, 10.0)))))!!
        assertTrue(cursor.x in inner.x..inner.x + inner.width)
    }

    @Test
    fun theLogReadsTheCursorWhereItIsDrawn() {
        val pointer = MenuPointer()

        val cursor = pointer.map(view(x = 500.0, y = 450.0, logShift = 20.0))
        assertEquals(250.0, cursor!!.x)
        assertEquals(245.0, cursor.y)
        assertNull(pointer.hovered)
    }

    @Test
    fun aBannerUnderTheCursorIsHoveredAndMapsIntoItsRealRectangle() {
        val pointer = MenuPointer()
        val cursor = pointer.map(view(targets = listOf(move(3, plane(0.0, 0.0, 10.0)))))
        assertEquals(SlotHandle(BannerKind.MOVE, 3), pointer.hovered)
        assertNotNull(cursor)
        assertTrue(cursor.x in real.x..real.x + real.width && cursor.y in real.y..real.y + real.height)
    }

    @Test
    fun theNearestOfTwoOverlappingBannersWins() {
        val pointer = MenuPointer()
        pointer.map(view(targets = listOf(move(1, plane(0.0, 0.0, 20.0)), move(2, plane(0.0, 0.0, 10.0)))))
        assertEquals(SlotHandle(BannerKind.MOVE, 2), pointer.hovered)
        pointer.map(view(targets = listOf(move(2, plane(0.0, 0.0, 10.0)), move(1, plane(0.0, 0.0, 20.0)))))
        assertEquals(SlotHandle(BannerKind.MOVE, 2), pointer.hovered)
    }

    @Test
    fun pointingAtThePaddingStillCountsAsInsideTheButton() {
        val pointer = MenuPointer()

        val left = pointer.map(view(x = 400.0 - 195.0, targets = listOf(move(0, plane(0.0, 0.0, 10.0, halfWidth = 10.0)))))
        assertEquals(SlotHandle(BannerKind.MOVE, 0), pointer.hovered)
        assertTrue(left!!.x >= real.x + real.width * 0.04 - 1e-9)
        assertTrue(left.x < real.x + real.width / 2)
    }

    @Test
    fun overNothingThereIsNoCursorAndNoHover() {
        val pointer = MenuPointer()
        assertNull(pointer.map(view(x = 50.0, y = 50.0, targets = listOf(move(0, plane(0.0, 0.0, 10.0))))))
        assertNull(pointer.hovered)
    }

    @Test
    fun theLeftoverPlaneMapsIntoTheMenuRegion() {
        val pointer = MenuPointer()
        val cursor = pointer.map(view(leftover = plane(0.0, 0.0, 10.0)))
        assertNull(pointer.hovered)
        assertEquals(menu.x + menu.width / 2, cursor!!.x, 1e-6)
        assertEquals(menu.y + menu.height / 2, cursor.y, 1e-6)
    }

    @Test
    fun aBannerBeatsTheLeftoverPlaneBehindIt() {
        val pointer = MenuPointer()
        pointer.map(view(targets = listOf(move(4, plane(0.0, 0.0, 10.0))), leftover = plane(0.0, 0.0, 8.0)))
        assertEquals(SlotHandle(BannerKind.MOVE, 4), pointer.hovered)
    }

    @Test
    fun aHoveredMoveStaysHoveredWhileTheCameraMovesItAwayFromTheCursor() {
        val pointer = MenuPointer()
        val here = move(5, plane(0.0, 0.0, 10.0))
        pointer.map(view(targets = listOf(here)))
        assertEquals(SlotHandle(BannerKind.MOVE, 5), pointer.hovered)

        val moved = move(5, plane(8.0, 0.0, 10.0))
        assertNotNull(pointer.map(view(targets = listOf(moved))))
        assertEquals(SlotHandle(BannerKind.MOVE, 5), pointer.hovered)
    }

    @Test
    fun theLockLetsGoWhenTheCursorLeavesTheRememberedShape() {
        val pointer = MenuPointer()
        pointer.map(view(targets = listOf(move(5, plane(0.0, 0.0, 10.0)))))

        assertNull(pointer.map(view(x = 600.0, y = 300.0, targets = emptyList())))
        assertNull(pointer.hovered)
        assertNull(pointer.map(view(targets = emptyList())))
    }

    @Test
    fun releasingTheLockOrResettingForgetsTheRememberedBanner() {
        val pointer = MenuPointer()
        pointer.map(view(targets = listOf(move(5, plane(0.0, 0.0, 10.0)))))
        pointer.releaseLock()
        assertNull(pointer.map(view(targets = emptyList())))
        pointer.map(view(targets = listOf(move(5, plane(0.0, 0.0, 10.0)))))
        pointer.reset()
        assertNull(pointer.hovered)
        assertNull(pointer.map(view(targets = emptyList())))
    }

    @Test
    fun onlyMovesLockTheHover() {
        val pointer = MenuPointer()
        pointer.map(view(targets = listOf(PointerTarget(SlotHandle(BannerKind.OPTION, 1), real, plane(0.0, 0.0, 10.0)))))
        assertEquals(SlotHandle(BannerKind.OPTION, 1), pointer.hovered)
        assertNull(pointer.map(view(targets = emptyList())))
    }
}
