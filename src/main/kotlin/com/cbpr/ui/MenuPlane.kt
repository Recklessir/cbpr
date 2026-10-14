package com.cbpr.ui

import com.cbpr.camera.Point
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class PlaneHit(val u: Double, val v: Double, val t: Double)

class MenuPlane(
    val center: Point,
    val right: Point,
    val up: Point,
    val halfWidth: Double,
    val halfHeight: Double,
    val skew: Double = 0.0,
) {

    fun corner(sx: Int, sy: Int): Point =
        center + right * (sx * halfWidth + sy * skew * halfWidth) + up * (sy * halfHeight)

    fun hit(origin: Point, direction: Point): PlaneHit? {
        if (halfWidth <= 0.0 || halfHeight <= 0.0) return null
        val normal = right cross up
        val facing = direction dot normal
        if (abs(facing) < PARALLEL_EPSILON) return null
        val t = ((center - origin) dot normal) / facing
        if (t <= 0.0) return null
        val offset = origin + direction * t - center
        val localX = offset dot right
        val localY = offset dot up
        val v = 0.5 - localY / (2 * halfHeight)

        val slant = skew * halfWidth * (localY / halfHeight)
        val u = (localX - slant) / (2 * halfWidth) + 0.5
        return if (u in 0.0..1.0 && v in 0.0..1.0) PlaneHit(u, v, t) else null
    }

    private companion object {
        const val PARALLEL_EPSILON = 1e-9
    }
}

object MenuLayout {
    private const val SCALE_PER_BLOCK = 0.0016

    private const val MIN_SCALE = 0.0025
    private const val MAX_SCALE = 0.03
    private const val BASE_TILT_DEGREES = 10.0

    fun scaleFor(distance: Double): Double = (distance * SCALE_PER_BLOCK).coerceIn(MIN_SCALE, MAX_SCALE)

    fun cursorRay(ndcX: Double, ndcY: Double, tanHalfFovX: Double, tanHalfFovY: Double, right: Point, up: Point, forward: Point): Point =
        right * (ndcX * tanHalfFovX) + up * (ndcY * tanHalfFovY) + forward

    fun place(
        anchor: Point,
        camera: Point,
        right: Point,
        up: Point,
        forward: Point,
        menuWidth: Double,
        menuHeight: Double,
        sizeFactor: Double,
        extraTiltDegrees: Double,
        clearance: Double,
        drop: Double,
    ): MenuPlane {
        val perPixel = scaleFor((anchor - camera).length())
        val halfWidth = menuWidth * perPixel / 2 * sizeFactor
        val halfHeight = menuHeight * perPixel / 2 * sizeFactor
        val center = anchor - right * (halfWidth + clearance) - up * drop
        val tilt = Math.toRadians(BASE_TILT_DEGREES + extraTiltDegrees)

        val tiltedRight = right * cos(tilt) + forward * sin(tilt)
        return MenuPlane(center, tiltedRight, up, halfWidth, halfHeight)
    }
}
