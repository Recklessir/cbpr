package com.cbpr.ui

import com.cbpr.camera.Point
import kotlin.math.abs

class PointerTarget(val handle: SlotHandle, val real: Rect, val plane: MenuPlane) {
    val kind: BannerKind get() = handle.kind
}

class PointerView(
    val rawX: Double,
    val rawY: Double,
    val windowWidth: Double,
    val windowHeight: Double,
    val guiWidth: Double,
    val guiHeight: Double,
    val camera: Point,
    val right: Point,
    val up: Point,
    val forward: Point,
    val tanHalfFovX: Double,
    val tanHalfFovY: Double,

    val log: Rect,
    val logShift: Double,

    val menu: Rect,
    val targets: List<PointerTarget>,
    val leftover: MenuPlane?,
    val ndc: (Point) -> Pair<Double, Double>?,
)

class MenuPointer {
    class Cursor(val x: Double, val y: Double)

    private class HoverLock(val target: PointerTarget, val shape: ScreenQuad, val u: Double, val v: Double)

    private var hoverLock: HoverLock? = null

    var hovered: SlotHandle? = null
        private set

    fun map(view: PointerView): Cursor? {
        hovered = null
        val guiX = view.rawX * view.guiWidth / view.windowWidth
        val guiY = view.rawY * view.guiHeight / view.windowHeight

        val log = view.log
        if (Rect(log.x, log.y - view.logShift, log.width, log.height).contains(guiX, guiY)) return Cursor(guiX, guiY + view.logShift)

        val ndcX = view.rawX / view.windowWidth * 2.0 - 1.0
        val ndcY = 1.0 - view.rawY / view.windowHeight * 2.0

        val ray = MenuLayout.cursorRay(ndcX, ndcY, view.tanHalfFovX, view.tanHalfFovY, view.right, view.up, view.forward)
        val lock = hoverLock
        if (lock != null) {
            if (lock.shape.contains(ndcX, ndcY, HOVER_LOCK_GROW)) {
                hovered = lock.target.handle
                return insideButton(lock.target, lock.u, lock.v, view.targets)
            }
            hoverLock = null
        }

        var best: Cursor? = null
        var bestDistance = Double.MAX_VALUE
        var bestTarget: PointerTarget? = null
        var bestHit: PlaneHit? = null
        for (target in view.targets) {
            val hit = target.plane.hit(view.camera, ray) ?: continue
            if (hit.t >= bestDistance) continue
            bestDistance = hit.t
            hovered = target.handle
            best = insideButton(target, hit.u, BannerLayout.bannerV(hit.v, target.kind), view.targets)
            bestTarget = target
            bestHit = hit
        }
        if (best != null) {
            if (bestTarget != null && bestHit != null && bestTarget.kind.locksHover) {
                hoverLock = lockFor(bestTarget, bestHit, view)
            }
            return best
        }
        val hit = view.leftover?.hit(view.camera, ray) ?: return null
        return Cursor(view.menu.x + hit.u * view.menu.width, view.menu.y + hit.v * view.menu.height)
    }

    fun releaseLock() {
        hoverLock = null
    }

    fun reset() {
        hoverLock = null
        hovered = null
    }

    private fun lockFor(target: PointerTarget, hit: PlaneHit, view: PointerView): HoverLock? {
        val corners = listOf(1 to 1, -1 to 1, -1 to -1, 1 to -1).map { (sx, sy) -> view.ndc(target.plane.corner(sx, sy)) ?: return null }
        return HoverLock(target, ScreenQuad(DoubleArray(4) { corners[it].first }, DoubleArray(4) { corners[it].second }), hit.u, BannerLayout.bannerV(hit.v, target.kind))
    }

    private fun insideButton(target: PointerTarget, u: Double, v: Double, all: List<PointerTarget>): Cursor {
        val kind = target.kind
        val real = target.real
        val x = real.x - kind.padLeft + u * kind.fullWidth
        val y = real.y - kind.padTop + v * kind.fullHeight
        val insetX = real.width * EDGE_INSET
        val insetY = real.height * EDGE_INSET
        val cursor = Cursor(
            x.coerceIn(real.x + insetX, real.x + real.width - insetX),
            y.coerceIn(real.y + insetY, real.y + real.height - insetY),
        )
        return apart(real, cursor, all.filter { it !== target }.map { it.real })
    }

    private fun apart(real: Rect, cursor: Cursor, others: List<Rect>): Cursor {
        val row = others.filter { cursor.y >= it.y - MARGIN && cursor.y <= it.y + it.height + MARGIN }
        if (row.none { cursor.x >= it.x - MARGIN && cursor.x <= it.x + it.width + MARGIN }) return cursor
        var free = listOf(real.x to real.x + real.width)
        for (other in row) free = free.flatMap { (lo, hi) -> listOf(lo to minOf(hi, other.x - MARGIN), maxOf(lo, other.x + other.width + MARGIN) to hi) }
        val best = free.filter { (lo, hi) -> hi - lo > 2 * MARGIN }.minByOrNull { (lo, hi) -> abs(cursor.x.coerceIn(lo, hi) - cursor.x) } ?: return cursor
        return Cursor(cursor.x.coerceIn(best.first + MARGIN, best.second - MARGIN), cursor.y)
    }

    private companion object {

        const val HOVER_LOCK_GROW = 1.1
        const val EDGE_INSET = 0.04

        const val MARGIN = 0.5
    }
}
