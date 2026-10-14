package com.cbpr.camera

import kotlin.math.cos
import kotlin.math.sin

fun interface Sight {
    fun clear(from: Point, to: Point): Boolean
}

class CameraAvoidance(private val sight: Sight) {
    data class Offset(val pull: Double, val raise: Double, val swingDegrees: Double)

    var monHidden = false
        private set
    var foeHidden = false
        private set
    var blockedSeconds = 0.0
        private set
    val released: Boolean get() = blockedSeconds >= RELEASE_SECONDS

    var offset = NONE
        private set
    private var blocked = false
    private var sinceCheck = CHECK_INTERVAL
    private var plainClearSeconds = 0.0
    private var rays = 0

    private class Result(val pose: Pose, val monClear: Boolean, val foeClear: Boolean) {
        val clear get() = monClear && foeClear
    }

    fun adjust(target: Pose, mon: Point, foe: Point, dt: Double, mons: List<Point> = listOf(mon), foes: List<Point> = listOf(foe)): Pose {
        sinceCheck += dt
        if (sinceCheck >= CHECK_INTERVAL) {
            recheck(target, mon, foe, mons, foes, sinceCheck)
            sinceCheck = 0.0
        }
        blockedSeconds = if (blocked) blockedSeconds + dt else 0.0
        return apply(offset, target, focus(mon, foe))
    }

    private fun recheck(target: Pose, mon: Point, foe: Point, mons: List<Point>, foes: List<Point>, elapsed: Double) {
        val perTest = mons.size + foes.size
        rays = 0
        val focus = focus(mon, foe)
        fun test(candidate: Offset): Result {
            val pose = apply(candidate, target, focus)
            val here = Point(pose.x, pose.y, pose.z)
            rays += perTest
            return Result(pose, mons.map { sight.clear(here, it) }.all { it }, foes.map { sight.clear(here, it) }.all { it })
        }
        val plain = test(NONE)
        if (plain.clear) {
            plainClearSeconds += elapsed

            if (offset != NONE && plainClearSeconds < RELEASE_HYSTERESIS && test(offset).clear) return settle(offset)
            return settle(NONE)
        }
        plainClearSeconds = 0.0
        if (offset != NONE && test(offset).clear) return settle(offset)
        for (candidate in CANDIDATES) {
            if (rays + perTest > MAX_RAYS) break
            if (test(candidate).clear) return settle(candidate)
        }
        offset = NONE
        blocked = true
        monHidden = !plain.monClear
        foeHidden = !plain.foeClear
    }

    private fun settle(chosen: Offset) {
        offset = chosen
        blocked = false
        monHidden = false
        foeHidden = false
    }

    fun reset() {
        offset = NONE
        blocked = false
        blockedSeconds = 0.0
        monHidden = false
        foeHidden = false
        sinceCheck = CHECK_INTERVAL
        plainClearSeconds = 0.0
    }

    companion object {
        val NONE = Offset(1.0, 0.0, 0.0)
        const val CHECK_INTERVAL = 0.1
        const val RELEASE_HYSTERESIS = 0.4
        const val RELEASE_SECONDS = 1.5
        const val MAX_RAYS = 24

        val CANDIDATES = listOf(
            Offset(0.8, 0.0, 0.0), Offset(0.6, 0.0, 0.0), Offset(0.4, 0.0, 0.0),
            Offset(0.8, 1.0, 0.0), Offset(0.7, 2.0, 0.0),
            Offset(1.0, 0.0, 30.0), Offset(1.0, 0.0, -30.0),
            Offset(0.8, 0.5, 60.0), Offset(0.8, 0.5, -60.0),
        )

        fun focus(mon: Point, foe: Point) = (mon + foe) * 0.5

        fun apply(offset: Offset, target: Pose, focus: Point): Pose {
            if (offset == NONE) return target
            val rel = Point(target.x - focus.x, target.y - focus.y, target.z - focus.z)
            val a = Math.toRadians(offset.swingDegrees)
            val swung = Point(rel.x * cos(a) + rel.z * sin(a), rel.y, -rel.x * sin(a) + rel.z * cos(a)) * offset.pull
            val pos = Point(focus.x + swung.x, focus.y + swung.y + offset.raise, focus.z + swung.z)
            if (offset.raise == 0.0 && offset.swingDegrees == 0.0) return target.copy(x = pos.x, y = pos.y, z = pos.z)
            val aim = Shots.lookAt(pos, focus)
            return target.copy(x = pos.x, y = pos.y, z = pos.z, yaw = aim.yaw, pitch = aim.pitch)
        }
    }
}
