package com.cbpr.ui

import com.cbpr.camera.Point
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class Subject(val centre: Point, val width: Double, val height: Double)

enum class BannerKind(
    val artWidth: Int, val artHeight: Int, val padLeft: Int, val padRight: Int, val padTop: Int, val padBottom: Int,
    val hang: Double = 0.0, val bottomInset: Double = 0.0, val expandsHit: Boolean = false, val locksHover: Boolean = false,
    val side: Double = 1.0,
) {
    OPTION(90, 26, 4, 4, 4, 4, side = -1.0),
    MOVE(92, 24, 16, 4, 4, 4, hang = 12.0, expandsHit = true, locksHover = true),
    PREVIEW(160, 92, 3, 3, 3, 3),
    SWITCH(94, 29, 4, 4, 4, 4, hang = 8.0, bottomInset = 5.0, expandsHit = true),
    BACK(29, 17, 3, 3, 3, 3),
    GIMMICK(18, 17, 3, 3, 3, 3),
    SHIFT(36, 17, 3, 3, 3, 3),
    ADDON(90, 26, 4, 4, 4, 4);

    val fullWidth: Int get() = artWidth + padLeft + padRight
    val fullHeight: Int get() = artHeight + padTop + padBottom
}

object BannerLayout {
    private const val MIN_GAP = 0.3
    private const val GAP_PER_POKEMON_WIDTH = 0.45
    private const val PITCH = 1.25
    private const val STEP_ALONG = 0.06
    private const val STEP_NEARER = 0.05
    private const val YAW_DEGREES = 8.0
    private const val ROLL_DEGREES = -2.0
    private const val SKEW = 0.06
    private const val SIZE_BOOST = 1.6

    private const val GIMMICK_HEIGHT_SHARE = 0.9
    private const val GIMMICK_GAP = 0.15

    private const val MIN_ROW_SHRINK = 0.25
    private const val BACK_HEIGHT_SHARE = 0.75
    private const val PREVIEW_GAP = 3.0
    private const val PREVIEW_LIFT = 0.06

    private const val NORMAL_SIZE = 2.5
    private const val SIZE_GROWTH_EXPONENT = 0.5
    private const val MAX_SIZE_FACTOR = 2.2

    fun sizeFactor(subject: Subject): Double {
        val size = max(subject.width, subject.height)
        return if (size <= NORMAL_SIZE) 1.0 else Math.pow(size / NORMAL_SIZE, SIZE_GROWTH_EXPONENT).coerceAtMost(MAX_SIZE_FACTOR)
    }

    private fun pixelSize(subject: Subject, camera: Point) =
        MenuLayout.scaleFor((subject.centre - camera).length()) * SIZE_BOOST * sizeFactor(subject)

    private val REFERENCE = BannerKind.OPTION
    private const val MIN_TILE_GAP = 1.1

    private fun innerPad(kind: BannerKind, side: Double) = if (side < 0) kind.padRight else kind.padLeft

    private fun pitch(kind: BannerKind, perPixel: Double) =
        max(REFERENCE.fullHeight * PITCH, kind.fullHeight * MIN_TILE_GAP) * perPixel

    private fun steps(side: Double, row: Int, count: Int) = if (side < 0) row else count - row

    private const val EXTRA_GAP = 1.0

    private fun along(item: BannerKind, side: Double, itemWidth: Double, stepCount: Double, hang: Double, outward: Double, clearance: Double, perPixel: Double): Double =
        side * (clearance + (REFERENCE.padRight - innerPad(item, side) - hang) * perPixel + itemWidth / 2 +
            stepCount * STEP_ALONG * REFERENCE.fullWidth * perPixel + outward)

    fun clearance(subject: Subject): Double = subject.width / 2 + max(MIN_GAP, subject.width * GAP_PER_POKEMON_WIDTH)

    fun hitExpansion(kind: BannerKind): Double =
        if (kind.expandsHit) max(1.0, max(REFERENCE.fullHeight * PITCH, kind.fullHeight * MIN_TILE_GAP) / kind.fullHeight) else 1.0

    fun hitPlane(plane: MenuPlane, kind: BannerKind): MenuPlane {
        val f = hitExpansion(kind)
        return if (f == 1.0) plane else MenuPlane(plane.center, plane.right, plane.up, plane.halfWidth, plane.halfHeight * f, plane.skew * f)
    }

    fun bannerV(hitV: Double, kind: BannerKind): Double = 0.5 - (0.5 - hitV) * hitExpansion(kind)

    fun stackHalfHeight(kind: BannerKind, count: Int, perPixel: Double): Double =
        max(count, 1) * pitch(kind, perPixel * SIZE_BOOST) / 2

    fun arrange(
        kind: BannerKind,
        count: Int,
        subject: Subject,
        camera: Point,
        right: Point,
        up: Point,
        forward: Point,
        extraYawDegrees: Double,
    ): List<MenuPlane> {
        val perPixel = pixelSize(subject, camera)
        val width = kind.fullWidth * perPixel
        val height = kind.fullHeight * perPixel
        val clearance = clearance(subject)
        val side = kind.side
        return List(count) { position ->
            val rowsFromMiddle = (count - 1) / 2.0 - position
            val center = subject.centre +
                right * along(kind, side, width, steps(side, position, count).toDouble(), 0.0, 0.0, clearance, perPixel) +
                up * (rowsFromMiddle * pitch(kind, perPixel)) +
                forward * (-position * STEP_NEARER * REFERENCE.fullHeight * perPixel)
            pose(center, right, up, forward, width, height, -side * YAW_DEGREES + extraYawDegrees)
        }
    }

    fun previewPlane(
        hovered: MenuPlane, subject: Subject, camera: Point, leftSide: Boolean,
        artHeight: Int = BannerKind.PREVIEW.fullHeight, artWidth: Int = BannerKind.PREVIEW.fullWidth,
    ): MenuPlane {
        val perPixel = pixelSize(subject, camera)
        val halfWidth = artWidth * perPixel / 2
        val halfHeight = artHeight * perPixel / 2
        val offset = (hovered.halfWidth + halfWidth + PREVIEW_GAP * perPixel) * if (leftSide) -1.0 else 1.0
        val toCamera = camera - hovered.center
        val center = hovered.center + hovered.right * offset + toCamera * (PREVIEW_LIFT / max(toCamera.length(), 1e-6))
        return MenuPlane(center, hovered.right, hovered.up, halfWidth, halfHeight, 0.0)
    }

    private fun extraRow(
        kind: BannerKind,
        count: Int,
        item: BannerKind,
        itemWidth: Double,
        itemHeight: Double,
        outward: Double,
        subject: Subject,
        camera: Point,
        right: Point,
        up: Point,
        forward: Point,
        extraYawDegrees: Double,
    ): MenuPlane {
        val perPixel = pixelSize(subject, camera)
        val side = kind.side
        val pitch = pitch(kind, perPixel)

        val drop = kind.fullHeight * perPixel * (1 + GIMMICK_HEIGHT_SHARE) / 2 + (EXTRA_GAP - kind.bottomInset) * perPixel

        val lower = (kind.fullHeight * perPixel * BACK_HEIGHT_SHARE - itemHeight) / 2
        val pitches = drop / pitch
        val lastRow = count - 1
        val stepCount = if (side < 0) lastRow + pitches else 1 - pitches
        val center = subject.centre +
            right * along(item, side, itemWidth, stepCount, kind.hang, outward, clearance(subject), perPixel) +
            up * (-lastRow / 2.0 * pitch - drop - lower) +
            forward * (-(lastRow + pitches) * STEP_NEARER * REFERENCE.fullHeight * perPixel)
        val yaw = -side * YAW_DEGREES + extraYawDegrees

        val lean = pose(center, right, up, forward, itemWidth, itemHeight, yaw).right
        val backWidth = BannerKind.BACK.fullWidth.toDouble() / BannerKind.BACK.fullHeight * kind.fullHeight * perPixel * BACK_HEIGHT_SHARE
        val backAlong = along(BannerKind.BACK, side, backWidth, stepCount, kind.hang, 0.0, clearance(subject), perPixel)
        val sideways = ((center - subject.centre) dot right) - backAlong
        return pose(center - right * sideways + lean * (sideways / (lean dot right)), right, up, forward, itemWidth, itemHeight, yaw)
    }

    fun backPlane(
        kind: BannerKind,
        count: Int,
        subject: Subject,
        camera: Point,
        right: Point,
        up: Point,
        forward: Point,
        extraYawDegrees: Double,
    ): MenuPlane {
        val perPixel = pixelSize(subject, camera)
        val height = kind.fullHeight * perPixel * BACK_HEIGHT_SHARE
        val width = BannerKind.BACK.fullWidth.toDouble() / BannerKind.BACK.fullHeight * height
        return extraRow(kind, count, BannerKind.BACK, width, height, 0.0, subject, camera, right, up, forward, extraYawDegrees)
    }

    fun extraPlanes(
        kind: BannerKind,
        count: Int,
        items: List<BannerKind>,
        subject: Subject,
        camera: Point,
        right: Point,
        up: Point,
        forward: Point,
        extraYawDegrees: Double,
    ): List<MenuPlane> {
        val perPixel = pixelSize(subject, camera)
        val height = kind.fullHeight * perPixel * GIMMICK_HEIGHT_SHARE
        val gap = GIMMICK_GAP * perPixel
        val backWidth = BannerKind.BACK.fullWidth.toDouble() / BannerKind.BACK.fullHeight * kind.fullHeight * perPixel * BACK_HEIGHT_SHARE
        val widths = items.map { it.fullWidth.toDouble() / it.fullHeight * height }
        val room = kind.fullWidth * perPixel - backWidth - gap * items.size
        val shrink = if (widths.isEmpty()) 1.0 else (room / widths.sum()).coerceIn(MIN_ROW_SHRINK, 1.0)
        var outward = backWidth + gap
        return items.mapIndexed { index, item ->
            val width = widths[index] * shrink
            extraRow(kind, count, item, width, height * shrink, outward, subject, camera, right, up, forward, extraYawDegrees)
                .also { outward += width + gap }
        }
    }

    private fun pose(
        center: Point, right: Point, up: Point, forward: Point, width: Double, height: Double, yawDegrees: Double,
        rollDegrees: Double = ROLL_DEGREES, skew: Double = SKEW,
    ): MenuPlane {
        val yaw = Math.toRadians(yawDegrees)

        val turnedRight = right * cos(yaw) + forward * sin(yaw)
        val roll = Math.toRadians(rollDegrees)
        val rolledRight = turnedRight * cos(roll) + up * sin(roll)
        val rolledUp = up * cos(roll) - turnedRight * sin(roll)
        return MenuPlane(center, rolledRight, rolledUp, width / 2, height / 2, skew)
    }
}
