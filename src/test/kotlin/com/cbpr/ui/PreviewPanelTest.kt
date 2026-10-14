package com.cbpr.ui

import com.cbpr.camera.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewPanelTest {
    private val width = { text: String -> text.length * 6 }

    @Test
    fun aShortDescriptionIsLeftAlone() {
        val lines = listOf("One line.", "Two lines.")
        assertEquals(lines, PreviewText.limit(lines, 6, width, 150))
    }

    @Test
    fun aLongOneIsCutToTheLimitWithAnEllipsisAndNothingBeyondTheWidth() {
        val lines = List(9) { "line number $it of text" }
        val kept = PreviewText.limit(lines, 4, width, 150)
        assertEquals(4, kept.size)
        assertEquals(lines.take(3), kept.take(3))
        assertTrue(kept.last().endsWith(PreviewText.ELLIPSIS))
        assertTrue(width(kept.last()) <= 150, "last line is ${width(kept.last())} wide")
    }

    @Test
    fun theEllipsisNeverOverflowsEvenOnAFullLastLine() {
        val full = "x".repeat(25)
        val kept = PreviewText.limit(listOf("a", full, "z"), 2, width, 150)
        assertTrue(width(kept.last()) <= 150)
        assertTrue(kept.last().endsWith(PreviewText.ELLIPSIS))
    }

    private val camera = Point(0.0, 0.0, 0.0)
    private val subject = Subject(Point(0.0, 0.0, -4.0), 1.0, 1.0)
    private val hovered = BannerLayout.arrange(BannerKind.MOVE, 4, subject, camera, Point(1.0, 0.0, 0.0), Point(0.0, 1.0, 0.0), Point(0.0, 0.0, -1.0), 0.0)[1]

    @Test
    fun thePanelIsAPlainRectangleNotSlantedLikeTheBanners() {
        val panel = BannerLayout.previewPlane(hovered, subject, camera, leftSide = false)
        assertTrue(hovered.skew != 0.0, "the banners themselves are slanted")
        assertEquals(0.0, panel.skew)
    }

    @Test
    fun thePanelIsAsTallAsItsTextAndNoTaller() {
        val short = BannerLayout.previewPlane(hovered, subject, camera, leftSide = false, artHeight = 40)
        val tall = BannerLayout.previewPlane(hovered, subject, camera, leftSide = false, artHeight = 90)
        assertEquals(short.halfHeight * 90 / 40, tall.halfHeight, 1e-9)
        assertEquals(short.halfWidth, tall.halfWidth, 1e-12)
    }

    @Test
    fun thePanelHasNoTurnToWaitForSoItAppearsAtOnceUnlikeALateSlot() {
        val motion = BannerMotion<Int>()
        motion.setVisible(8, true, 0)
        motion.setVisible(9, true, 9)
        repeat(6) { motion.step(1.0 / 60, -1) }
        assertTrue(motion.popScale(8) > 0.2, "the panel is already growing")
        assertEquals(0.0, motion.popScale(9), 1e-12, "the same slot with the old per-slot turn is still waiting")
    }
}
