package com.cbpr.ui

import com.cbpr.camera.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BannerLayoutTest {
    private val camera = Point(0.0, 0.0, 0.0)
    private val right = Point(1.0, 0.0, 0.0)
    private val up = Point(0.0, 1.0, 0.0)
    private val forward = Point(0.0, 0.0, -1.0)

    private fun subject(width: Double = 0.8, height: Double = 1.0) = Subject(Point(0.0, 0.0, -4.0), width, height)

    private fun arrange(kind: BannerKind, count: Int, subject: Subject = subject(), yaw: Double = 0.0) =
        BannerLayout.arrange(kind, count, subject, camera, right, up, forward, yaw)

    private fun rightOf(plane: MenuPlane) = plane.center.x - subject().centre.x

    @Test
    fun oneBannerPerSlot() {
        assertEquals(4, arrange(BannerKind.OPTION, 4).size)
        assertEquals(3, arrange(BannerKind.MOVE, 3).size)
        assertEquals(0, arrange(BannerKind.MOVE, 0).size)
    }

    @Test
    fun theDescriptionPanelSitsBesideTheHoveredCardAndInFrontOfIt() {
        val hovered = arrange(BannerKind.MOVE, 2)[0]
        val left = BannerLayout.previewPlane(hovered, subject(), camera, leftSide = true)
        val right = BannerLayout.previewPlane(hovered, subject(), camera, leftSide = false)

        val leftGap = (left.center - hovered.center) dot hovered.right
        val rightGap = (right.center - hovered.center) dot hovered.right
        assertTrue(leftGap < -(hovered.halfWidth + left.halfWidth), "left panel ends before the card")
        assertTrue(rightGap > hovered.halfWidth + right.halfWidth, "right panel starts after the card")
        assertTrue(kotlin.math.abs(left.center.y - hovered.center.y) < hovered.halfHeight, "level with the card, give or take the slant")
        assertEquals(hovered.right, left.right)
    }

    @Test
    fun partyBannersFormOneStackOnTheRightLikeMoves() {
        val planes = arrange(BannerKind.SWITCH, 6)
        assertEquals(6, planes.size)
        assertTrue(planes.all { rightOf(it) > 0 })
    }

    @Test
    fun menuBannersAllStackOnTheLeft() {
        arrange(BannerKind.OPTION, 4).forEach { assertTrue(rightOf(it) < 0.0, "banner at ${rightOf(it)}") }
    }

    @Test
    fun menuBannersDoNotOverlapThePokemon() {
        val s = subject()
        for (plane in arrange(BannerKind.OPTION, 4, s)) {
            assertTrue(plane.center.x + plane.halfWidth < s.centre.x - s.width / 2, "overlaps the Pokemon")
        }
    }

    @Test
    fun bannersStackTopToBottomWithoutOverlap() {
        val planes = arrange(BannerKind.OPTION, 4)
        for (i in 0 until planes.size - 1) {
            assertTrue(planes[i].center.y > planes[i + 1].center.y, "slot $i should sit above slot ${i + 1}")
            val gap = planes[i].center.y - planes[i + 1].center.y
            assertTrue(gap > planes[i].halfHeight + planes[i + 1].halfHeight, "slots $i and ${i + 1} overlap")
        }
    }

    @Test
    fun eachLowerBannerStepsFurtherOutAndNearerTheCamera() {
        val planes = arrange(BannerKind.OPTION, 4)
        for (i in 0 until planes.size - 1) {
            assertTrue(planes[i + 1].center.x < planes[i].center.x, "slot ${i + 1} should step further out, away from the Pokemon")
            assertTrue(planes[i + 1].center.z > planes[i].center.z, "slot ${i + 1} should be nearer (larger z toward the camera)")
        }
    }

    @Test
    fun fourMovesFormOneStackOnTheRight() {
        val planes = arrange(BannerKind.MOVE, 4)
        assertTrue(planes.all { rightOf(it) > 0 })
    }

    @Test
    fun aSingleMoveStillGetsAPlace() {
        val planes = arrange(BannerKind.MOVE, 1)
        assertEquals(1, planes.size)
        assertTrue(rightOf(planes[0]) > 0)
    }

    @Test
    fun biggerPokemonPushBannersFurtherOut() {
        val small = arrange(BannerKind.OPTION, 1, subject(width = 0.4))[0]
        val big = arrange(BannerKind.OPTION, 1, subject(width = 2.0))[0]
        assertTrue(big.center.x < small.center.x, "big ${big.center.x} small ${small.center.x}")
    }

    @Test
    fun menuBannersTurnTowardThePokemonOnTheLeftAndAwayFromItOnTheRight() {
        val left = arrange(BannerKind.OPTION, 2)[0]
        val rightSide = arrange(BannerKind.MOVE, 4)[3]
        assertTrue((left.right cross left.up).x > 0.0, "left banner should face right")
        assertTrue((rightSide.right cross rightSide.up).x < 0.0, "right banner should face left")
    }

    @Test
    fun bannersStayUnitLengthAndFacingTheViewer() {
        for (plane in arrange(BannerKind.MOVE, 4, yaw = 6.0)) {
            val normal = plane.right cross plane.up
            assertEquals(1.0, normal.length(), 1e-9)
            assertTrue(normal.z > 0.0, "should face the camera, normal z ${normal.z}")
        }
    }

    @Test
    fun everyBannerIsSlantedAndHittableThroughItsCentre() {
        for (plane in arrange(BannerKind.MOVE, 4)) {
            assertTrue(plane.skew != 0.0)
            val hit = assertNotNull(plane.hit(camera, plane.center - camera))
            assertEquals(0.5, hit.u, 1e-6)
            assertEquals(0.5, hit.v, 1e-6)
        }
    }

    @Test
    fun bannerSizeFollowsTheButtonPlusItsPadding() {
        val move = arrange(BannerKind.MOVE, 1)[0]
        val option = arrange(BannerKind.OPTION, 1)[0]
        assertEquals(BannerKind.MOVE.fullWidth.toDouble() / BannerKind.MOVE.fullHeight, move.halfWidth / move.halfHeight, 1e-9)
        assertEquals(BannerKind.OPTION.fullWidth.toDouble() / BannerKind.OPTION.fullHeight, option.halfWidth / option.halfHeight, 1e-9)
    }

    @Test
    fun paddingCoversWhatCobblemonDrawsOutsideAMoveButton() {

        assertTrue(BannerKind.MOVE.padLeft > BannerKind.MOVE.padRight)
        assertEquals(BannerKind.MOVE.artWidth + BannerKind.MOVE.padLeft + BannerKind.MOVE.padRight, BannerKind.MOVE.fullWidth)
        assertEquals(BannerKind.OPTION.artHeight + BannerKind.OPTION.padTop + BannerKind.OPTION.padBottom, BannerKind.OPTION.fullHeight)
    }

    private fun back(kind: BannerKind, count: Int, s: Subject = subject()) =
        BannerLayout.backPlane(kind, count, s, camera, right, up, forward, 0.0)

    @Test
    fun backButtonSitsBelowTheStackOnTheSameSide() {
        for ((kind, count) in listOf(BannerKind.OPTION to 4, BannerKind.MOVE to 4, BannerKind.MOVE to 3, BannerKind.MOVE to 1)) {
            val banners = arrange(kind, count)
            val lowest = banners.last()
            val plane = back(kind, count)
            assertTrue(plane.center.y < lowest.center.y, "$kind $count: back should sit below the stack")
            if (kind == BannerKind.OPTION) assertTrue(rightOf(plane) < 0.0, "$kind $count: back should be on the left")
            else assertTrue(rightOf(plane) > 0.0, "$kind $count: back should be on the right with the cards")
        }
    }

    @Test
    fun backButtonStaysCloseToTheMenuNotAtTheBottomOfTheScreen() {
        val banners = arrange(BannerKind.OPTION, 4)
        val plane = back(BannerKind.OPTION, 4)
        val rowHeight = banners[0].halfHeight * 2
        assertTrue(banners.last().center.y - plane.center.y < rowHeight * 2, "back should be about one row below the last banner")
    }

    @Test
    fun backButtonKeepsClearOfThePokemon() {
        val s = subject()
        val plane = back(BannerKind.OPTION, 4, s)
        assertTrue(plane.center.x + plane.halfWidth < s.centre.x - s.width / 2, "overlaps the Pokemon")
    }

    @Test
    fun backButtonIsHittableAndSmallerThanABanner() {
        val plane = back(BannerKind.MOVE, 4)
        val hit = assertNotNull(plane.hit(camera, plane.center - camera))
        assertEquals(0.5, hit.u, 1e-6)
        assertTrue(plane.halfWidth < arrange(BannerKind.MOVE, 4)[0].halfWidth)
    }
}
