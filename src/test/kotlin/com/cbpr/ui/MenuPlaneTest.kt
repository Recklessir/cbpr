package com.cbpr.ui

import com.cbpr.camera.Point
import kotlin.math.abs
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MenuPlaneTest {

    private val camera = Point(0.0, 0.0, 0.0)
    private val right = Point(1.0, 0.0, 0.0)
    private val up = Point(0.0, 1.0, 0.0)
    private val forward = Point(0.0, 0.0, -1.0)
    private val tanY = tan(Math.toRadians(35.0))
    private val tanX = tanY * 16.0 / 9.0

    private fun facing(center: Point, skew: Double = 0.0) = MenuPlane(center, right, up, 1.0, 0.5, skew)

    @Test
    fun centerRayHitsTheMiddle() {
        val hit = assertNotNull(facing(Point(0.0, 0.0, -4.0)).hit(camera, forward))
        assertEquals(0.5, hit.u, 1e-9)
        assertEquals(0.5, hit.v, 1e-9)
        assertEquals(4.0, hit.t, 1e-9)
    }

    @Test
    fun rayToTheRightHitsTheRightHalf() {
        val hit = assertNotNull(facing(Point(0.0, 0.0, -4.0)).hit(camera, Point(0.2, 0.0, -1.0)))
        assertTrue(hit.u > 0.5, "u ${hit.u}")
        assertEquals(0.5, hit.v, 1e-9)
    }

    @Test
    fun rayAboveHitsTheTopHalfWithVCountingDown() {
        val hit = assertNotNull(facing(Point(0.0, 0.0, -4.0)).hit(camera, Point(0.0, 0.05, -1.0)))
        assertTrue(hit.v < 0.5, "v ${hit.v}")
    }

    @Test
    fun missesOutsideTheEdges() {
        val plane = facing(Point(0.0, 0.0, -4.0))
        assertNull(plane.hit(camera, Point(1.0, 0.0, -1.0)))
        assertNull(plane.hit(camera, Point(0.0, 1.0, -1.0)))
    }

    @Test
    fun planeBehindTheCameraIsNotHit() {
        assertNull(facing(Point(0.0, 0.0, 4.0)).hit(camera, forward))
    }

    @Test
    fun rayParallelToThePlaneIsNotHit() {
        assertNull(facing(Point(0.0, 0.0, -4.0)).hit(camera, Point(1.0, 0.0, 0.0)))
    }

    @Test
    fun zeroSizedPlaneNeverHits() {
        assertNull(MenuPlane(Point(0.0, 0.0, -4.0), right, up, 0.0, 0.0).hit(camera, forward))
    }

    @Test
    fun slantedCornersShiftTheTopRightAndTheBottomLeft() {
        val plane = facing(Point(0.0, 0.0, -4.0), skew = 0.25)
        assertEquals(1.25, plane.corner(1, 1).x, 1e-9)
        assertEquals(0.75, plane.corner(1, -1).x, 1e-9)
        assertEquals(-0.75, plane.corner(-1, 1).x, 1e-9)
        assertEquals(-1.25, plane.corner(-1, -1).x, 1e-9)
        assertEquals(0.5, plane.corner(1, 1).y, 1e-9)
        assertEquals(-0.5, plane.corner(1, -1).y, 1e-9)
    }

    @Test
    fun everyCornerOfASlantedPlaneReadsBackAsAnUnslantedCorner() {
        val plane = facing(Point(0.0, 0.0, -4.0), skew = 0.25)
        val expected = mapOf((-1 to 1) to (0.0 to 0.0), (1 to 1) to (1.0 to 0.0), (1 to -1) to (1.0 to 1.0), (-1 to -1) to (0.0 to 1.0))
        for ((corner, uv) in expected) {
            val target = plane.corner(corner.first, corner.second)

            val inside = plane.center + (target - plane.center) * 0.999
            val hit = assertNotNull(plane.hit(camera, inside - camera), "corner $corner")
            assertEquals(uv.first, hit.u, 2e-3, "u at $corner")
            assertEquals(uv.second, hit.v, 2e-3, "v at $corner")
        }
    }

    @Test
    fun slantedPlaneMissesWhereTheSlantLeavesAnEmptyCorner() {
        val plane = facing(Point(0.0, 0.0, -4.0), skew = 0.25)

        val emptyTopLeft = Point(-1.2, 0.45, -4.0)
        assertNull(plane.hit(camera, emptyTopLeft))
        val filledTopRight = Point(1.2, 0.45, -4.0)
        assertNotNull(plane.hit(camera, filledTopRight))
    }

    @Test
    fun cursorAtScreenCentreFollowsForward() {
        val ray = MenuLayout.cursorRay(0.0, 0.0, tanX, tanY, right, up, forward)
        assertEquals(forward.x, ray.x, 1e-9)
        assertEquals(forward.z, ray.z, 1e-9)
    }

    @Test
    fun cursorAtScreenCornerTouchesTheFrustumEdge() {
        val ray = MenuLayout.cursorRay(1.0, 1.0, tanX, tanY, right, up, forward)
        assertEquals(tanX, ray.x / -ray.z, 1e-9)
        assertEquals(tanY, ray.y / -ray.z, 1e-9)
    }

    @Test
    fun scaleGrowsWithDistanceWithinClamps() {
        assertTrue(MenuLayout.scaleFor(8.0) > MenuLayout.scaleFor(4.0))
        assertEquals(MenuLayout.scaleFor(0.0), MenuLayout.scaleFor(1.0))
        assertEquals(MenuLayout.scaleFor(500.0), MenuLayout.scaleFor(1000.0))
    }

    @Test
    fun placedPlaneSitsLeftOfTheAnchorWithTheGivenClearanceAndDrop() {
        val anchor = Point(0.0, 0.0, -4.0)
        val plane = MenuLayout.place(anchor, camera, right, up, forward, 360.0, 210.0, 1.0, 0.0, clearance = 0.5, drop = 0.4)
        assertTrue(plane.center.x < anchor.x, "centre ${plane.center.x}")
        assertEquals(anchor.y - 0.4, plane.center.y, 1e-9)
        val rightEdge = plane.center.x + plane.halfWidth
        assertEquals(anchor.x - 0.5, rightEdge, 0.05)
    }

    @Test
    fun placedPlaneIsHitThroughItsOwnCentre() {
        val plane = MenuLayout.place(Point(0.5, -0.3, -4.0), camera, right, up, forward, 360.0, 210.0, 1.0, 0.0, 0.5, 0.0)
        val hit = assertNotNull(plane.hit(camera, plane.center - camera))
        assertEquals(0.5, hit.u, 1e-9)
        assertEquals(0.5, hit.v, 1e-9)
    }

    @Test
    fun baseTiltTurnsTheNormalTowardTheViewersRight() {
        val plane = MenuLayout.place(Point(0.0, 0.0, -4.0), camera, right, up, forward, 360.0, 210.0, 1.0, 0.0, 0.5, 0.0)
        val normal = plane.right cross plane.up
        assertTrue(normal.x > 0.0, "normal x ${normal.x}")
        assertTrue(normal.z > 0.0, "should still face the camera, normal z ${normal.z}")
        assertEquals(1.0, normal.length(), 1e-9)
    }

    @Test
    fun extraTiltAddsToTheBaseTilt() {
        val flat = MenuLayout.place(Point(0.0, 0.0, -4.0), camera, right, up, forward, 360.0, 210.0, 1.0, 0.0, 0.5, 0.0)
        val leaning = MenuLayout.place(Point(0.0, 0.0, -4.0), camera, right, up, forward, 360.0, 210.0, 1.0, 8.0, 0.5, 0.0)
        assertTrue(abs((leaning.right cross leaning.up).x) > abs((flat.right cross flat.up).x))
    }

    @Test
    fun regionsSitInTheCornersAndStayOnScreen() {
        val menu = MenuRegions.menu(480.0)
        val log = MenuRegions.log(960.0, 480.0)
        assertEquals(0.0, menu.x)
        assertEquals(480.0, menu.y + menu.height)
        assertEquals(960.0, log.x + log.width)
        assertEquals(480.0, log.y + log.height)
        assertTrue(menu.contains(12.0, 400.0))
        assertTrue(log.contains(900.0, 450.0))
        assertTrue(!menu.contains(500.0, 400.0))
        assertTrue(!log.contains(100.0, 450.0))
    }
}
