package com.cbpr.ui

import com.cbpr.camera.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BannerFanLayoutTest {
    private val camera = Point(0.0, 0.0, 0.0)
    private val right = Point(1.0, 0.0, 0.0)
    private val up = Point(0.0, 1.0, 0.0)
    private val forward = Point(0.0, 0.0, -1.0)
    private val subject = Subject(Point(0.0, 0.0, -4.0), 0.8, 1.0)

    private fun arrange(kind: BannerKind, count: Int) = BannerLayout.arrange(kind, count, subject, camera, right, up, forward, 0.0)
    private fun back(count: Int) = BannerLayout.backPlane(BannerKind.MOVE, count, subject, camera, right, up, forward, 0.0)
    private fun gimmick(count: Int, index: Int, total: Int) = extras(count, List(total) { BannerKind.GIMMICK })[index]
    private fun extras(count: Int, items: List<BannerKind>) = BannerLayout.extraPlanes(BannerKind.MOVE, count, items, subject, camera, right, up, forward, 0.0)

    private fun innerEdge(plane: MenuPlane) = plane.center.x - plane.halfWidth

    @Test
    fun movesAreTheSameSizeAsTheMainMenuBanners() {
        val move = arrange(BannerKind.MOVE, 1)[0]
        val option = arrange(BannerKind.OPTION, 1)[0]
        assertEquals(move.halfHeight / BannerKind.MOVE.fullHeight, option.halfHeight / BannerKind.OPTION.fullHeight, 1e-9)
    }

    @Test
    fun everyKindStacksOnItsOwnSideSteppingOutwardAndNearer() {
        for (kind in BannerKind.entries) {
            val planes = arrange(kind, 4)
            for (plane in planes) assertTrue((plane.center.x - subject.centre.x) * kind.side > 0, "$kind is on its side")
            for (i in 0 until 3) {
                assertTrue(planes[i + 1].center.x < planes[i].center.x, "$kind row ${i + 1} is left of row $i")
                assertTrue(planes[i + 1].center.z > planes[i].center.z, "$kind row ${i + 1} is nearer the camera")
                assertTrue(planes[i].center.y > planes[i + 1].center.y, "$kind row ${i + 1} is below row $i")
            }
            assertEquals(kind.expandsHit, BannerLayout.hitExpansion(kind) > 1.0, "$kind hit expansion")
        }
    }

    @Test
    fun theyAreOneSpacedStackOnTheRightWithNoOverlap() {
        val planes = arrange(BannerKind.MOVE, 4)
        for (plane in planes) assertTrue(plane.center.x - plane.halfWidth > subject.centre.x, "clear of the middle")
        for (i in 0 until 3) {
            val gap = planes[i].center.y - planes[i + 1].center.y
            assertTrue(gap > planes[i].halfHeight + planes[i + 1].halfHeight, "card ${i + 1} sits clear below card $i")
        }
    }

    @Test
    fun everyStackStepsDownAndToTheLeftOnScreenAndEachRowIsNearer() {
        for (kind in listOf(BannerKind.OPTION, BannerKind.MOVE, BannerKind.SWITCH)) {
            val planes = arrange(kind, 6)
            for (i in 0 until 5) {
                assertTrue(planes[i + 1].center.x < planes[i].center.x, "$kind row ${i + 1} is left of row $i, like the main menu")
                assertTrue(planes[i + 1].center.z > planes[i].center.z, "$kind row ${i + 1} is nearer the camera")
            }
        }
    }

    @Test
    fun theRightEdgesOfAStackAreOneStraightLineJustAsTheMainMenusLeftEdgesAre() {
        val options = arrange(BannerKind.OPTION, 4)
        val moves = arrange(BannerKind.MOVE, 4)
        val optionStep = options[1].center.x - options[0].center.x
        for (i in 0 until 3) {
            assertEquals(optionStep, options[i + 1].center.x - options[i].center.x, 1e-9, "main menu rows step evenly")
            assertEquals(optionStep, moves[i + 1].center.x - moves[i].center.x, 1e-9, "move rows step the same amount, the same way")
            assertEquals(moves[1].center.x + moves[1].halfWidth - (moves[0].center.x + moves[0].halfWidth),
                moves[i + 1].center.x + moves[i + 1].halfWidth - (moves[i].center.x + moves[i].halfWidth), 1e-9, "outer edges in line")
        }
    }

    @Test
    fun theStackIsCentredVerticallyOnThePoint() {
        val planes = arrange(BannerKind.MOVE, 4)
        assertEquals(subject.centre.y, (planes.first().center.y + planes.last().center.y) / 2, 1e-9)
    }

    @Test
    fun theBackButtonIsTheRowUnderTheLastBannerWithOrWithoutGimmickButtons() {
        val banners = arrange(BannerKind.MOVE, 4)
        val back = back(4)
        assertTrue(back.center.y + back.halfHeight < banners.last().center.y - banners.last().halfHeight, "its own row under the stack")
    }

    @Test
    fun gimmickButtonsAreBesideTheBackButtonOutwardOfItAndAsTallAsABanner() {
        val banners = arrange(BannerKind.MOVE, 4)
        val back = back(4)
        val gimmick = gimmick(4, 0, 1)

        assertEquals(back.center.y - back.halfHeight, gimmick.center.y - gimmick.halfHeight, back.halfHeight * 0.3, "one bottom edge")
        assertTrue(gimmick.center.x - gimmick.halfWidth >= back.center.x + back.halfWidth, "beside it, outward, not overlapping")
        assertTrue(gimmick.halfHeight <= banners[0].halfHeight && gimmick.halfHeight > banners[0].halfHeight * 0.7, "about the height of a banner")
        assertEquals(1.0, gimmick.halfWidth / gimmick.halfHeight * BannerKind.GIMMICK.fullHeight / BannerKind.GIMMICK.fullWidth, 1e-9, "keeps its square shape")
    }

    private fun bodyEdge(plane: MenuPlane, kind: BannerKind, side: Double): Double {
        val perPixel = 2 * plane.halfWidth / kind.fullWidth
        return if (side < 0) -(plane.center.x + plane.halfWidth - kind.padRight * perPixel) else plane.center.x - plane.halfWidth + kind.padLeft * perPixel
    }

    @Test
    fun movesAndPartyKeepTheMainMenusSpacingAndDistanceFromThePokemon() {
        val options = arrange(BannerKind.OPTION, 8)
        for (kind in listOf(BannerKind.MOVE, BannerKind.SWITCH)) {
            val planes = arrange(kind, 4)
            for (i in 0 until 4) {

                assertEquals(bodyEdge(options[4 - i], BannerKind.OPTION, -1.0), bodyEdge(planes[i], kind, 1.0), 1e-9, "$kind row $i body edge")
                assertEquals(arrange(BannerKind.OPTION, 4)[i].center.y, planes[i].center.y, 1e-9, "$kind row $i height")
                assertEquals(arrange(BannerKind.OPTION, 4)[i].center.z, planes[i].center.z, 1e-9, "$kind row $i depth")
            }
        }
    }

    private fun visibleEdge(plane: MenuPlane, kind: BannerKind): Double {
        val perPixel = 2 * plane.halfWidth / kind.fullWidth
        return bodyEdge(plane, kind, 1.0) - kind.hang * perPixel
    }

    @Test
    fun theBackButtonSitsOnTheLineTheStacksVisibleEdgeMakes() {
        for ((kind, count) in listOf(BannerKind.MOVE to 4, BannerKind.SWITCH to 5)) {
            val banners = arrange(kind, count)
            val n = count - 1
            val slope = (visibleEdge(banners[n], kind) - visibleEdge(banners[n - 1], kind)) / (banners[n].center.y - banners[n - 1].center.y)
            val back = BannerLayout.backPlane(kind, count, subject, camera, right, up, forward, 0.0)
            val expected = visibleEdge(banners[n], kind) + slope * (back.center.y - banners[n].center.y)
            val perPixel = banners[0].halfWidth * 2 / kind.fullWidth

            assertEquals(expected, bodyEdge(back, BannerKind.BACK, 1.0), perPixel * 1.01, "$kind")
        }
    }

    @Test
    fun theBackButtonIsMuchCloserToTheLastBannerThanTheBannersAreToEachOther() {
        val banners = arrange(BannerKind.MOVE, 4)
        val back = back(4)
        val gapBetweenBanners = banners[2].center.y - banners[2].halfHeight - (banners[3].center.y + banners[3].halfHeight)
        val gapToBack = banners[3].center.y - banners[3].halfHeight - (back.center.y + back.halfHeight)
        assertTrue(gapToBack > 0.0, "does not overlap")
        assertTrue(gapToBack < gapBetweenBanners * 0.5, "gap $gapToBack against $gapBetweenBanners")
    }

    @Test
    fun aSecondGimmickButtonSitsFurtherOutNotOnTopOfTheFirst() {
        val first = gimmick(4, 0, 2)
        val second = gimmick(4, 1, 2)
        assertTrue(innerEdge(second) >= first.center.x + first.halfWidth - 1e-9, "next to the first, outward of it")
        assertEquals(first.center.y, second.center.y, first.halfHeight * 0.2)
    }

    @Test
    fun smallAndMediumPokemonKeepTheDefaultSizeAndBigOnesGetSomewhatBigger() {
        fun factor(size: Double) = BannerLayout.sizeFactor(Subject(subject.centre, size, size))
        assertEquals(1.0, factor(0.5), 1e-12)
        assertEquals(1.0, factor(1.5), 1e-12)
        assertEquals(1.0, factor(2.5), 1e-12)
        assertTrue(factor(5.0) > 1.2 && factor(5.0) < factor(10.0), "grows with size")
        assertTrue(factor(10.0) > 1.8, "a giant's buttons are clearly bigger")
        assertEquals(factor(1000.0), factor(100.0), 1e-12, "but capped")
        assertTrue(factor(1000.0) <= 2.5)
    }

    @Test
    fun theBannersAreBiggerAroundAGiantAndTheSameAroundANormalPokemon() {
        val normal = BannerLayout.arrange(BannerKind.MOVE, 4, Subject(subject.centre, 1.5, 1.5), camera, right, up, forward, 0.0)[0]
        val small = BannerLayout.arrange(BannerKind.MOVE, 4, Subject(subject.centre, 0.5, 0.5), camera, right, up, forward, 0.0)[0]
        val giant = BannerLayout.arrange(BannerKind.MOVE, 4, Subject(subject.centre, 8.0, 8.0), camera, right, up, forward, 0.0)[0]
        assertEquals(normal.halfWidth, small.halfWidth, 1e-12)
        assertTrue(giant.halfWidth > normal.halfWidth * 1.5)
        assertTrue(giant.halfHeight > normal.halfHeight * 1.5)
    }

    @Test
    fun theMovesAndPartyHitAreasTouchSoSweepingAcrossThemNeverLeavesTheStack() {
        for ((kind, count) in listOf(BannerKind.MOVE to 4, BannerKind.SWITCH to 6)) checkTouching(kind, count)
    }

    private fun checkTouching(kind: BannerKind, count: Int) {
        val planes = arrange(kind, count)
        val areas = planes.map { BannerLayout.hitPlane(it, kind) }
        assertTrue(BannerLayout.hitExpansion(kind) > 1.1, "$kind is stretched")
        for (i in 0 until count - 1) {

            for (t in listOf(0.2, 0.35, 0.45, 0.55, 0.65, 0.8)) {
                val point = planes[i].center + (planes[i + 1].center - planes[i].center) * t
                val ray = point - camera
                assertTrue(areas[i].hit(camera, ray) != null || areas[i + 1].hit(camera, ray) != null, "$kind gap between row $i and ${i + 1} at $t")
            }
        }
    }

    @Test
    fun hitAreasAreOnlyStretchedForMovesAndKeepTheirSidesAndMapBackToTheBanner() {
        assertEquals(1.0, BannerLayout.hitExpansion(BannerKind.OPTION))
        assertEquals(1.0, BannerLayout.hitExpansion(BannerKind.BACK))
        val plane = arrange(BannerKind.MOVE, 2)[0]
        val area = BannerLayout.hitPlane(plane, BannerKind.MOVE)
        assertEquals(plane.halfWidth, area.halfWidth)
        assertEquals(plane.center, area.center)
        val hit = area.hit(camera, plane.center - camera)!!
        assertEquals(0.5, BannerLayout.bannerV(hit.v, BannerKind.MOVE), 1e-9)

        val top = plane.center + plane.up * plane.halfHeight
        val topHit = area.hit(camera, top - camera)!!
        assertEquals(0.0, BannerLayout.bannerV(topHit.v, BannerKind.MOVE), 1e-6)

        val edgePoint = plane.corner(1, 1)
        val onPlane = plane.hit(camera, edgePoint - camera)
        val onArea = area.hit(camera, edgePoint - camera)
        if (onPlane != null && onArea != null) assertEquals(onPlane.u, onArea.u, 1e-6)
    }
}
