package com.cbpr.camera

import java.util.Random
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

class OcclusionPerformanceTest {
    private fun reference(camera: Point, targets: List<OcclusionSelector.Target>, floorY: Int, isSolid: (Int, Int, Int) -> Boolean): Map<Long, OcclusionPick> {
        val picks = LinkedHashMap<Long, OcclusionPick>()
        val solid = HashMap<Long, Boolean>()
        fun solid(x: Int, y: Int, z: Int) = solid.getOrPut(OcclusionSelector.key(x, y, z)) { isSolid(x, y, z) }
        val lowest = if (camera.y < floorY) Int.MIN_VALUE else floorY
        val cx = floor(camera.x).toInt()
        val cy = floor(camera.y).toInt()
        val cz = floor(camera.z).toInt()
        fun distanceToBlock(p: Point, x: Int, y: Int, z: Int): Double {
            val dx = max(max(x - p.x, p.x - (x + 1)), 0.0)
            val dy = max(max(y - p.y, p.y - (y + 1)), 0.0)
            val dz = max(max(z - p.z, p.z - (z + 1)), 0.0)
            return Point(dx, dy, dz).length()
        }
        for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
            val x = cx + dx
            val y = cy + dy
            val z = cz + dz
            if (distanceToBlock(camera, x, y, z) <= OcclusionSelector.NEAR_RANGE && solid(x, y, z)) picks[OcclusionSelector.key(x, y, z)] = OcclusionPick(x, y, z, true)
        }
        for (target in targets) {
            val along = target.point - camera
            val length = along.length()
            if (length < 1e-6) continue
            var s = 0.0
            while (s <= length - target.clearEnd && picks.size < OcclusionSelector.MAX_BLOCKS) {
                val radius = (OcclusionSelector.NEAR_RADIUS + (target.farRadius - OcclusionSelector.NEAR_RADIUS) * (s / length)).roundToInt().coerceAtLeast(OcclusionSelector.NEAR_RADIUS)
                val p = camera + along * (s / length)
                val bx = floor(p.x).toInt()
                val by = floor(p.y).toInt()
                val bz = floor(p.z).toInt()
                for (dx in -radius..radius) for (dy in -radius..radius) for (dz in -radius..radius) {
                    val x = bx + dx
                    val y = by + dy
                    val z = bz + dz
                    if (y >= lowest && solid(x, y, z)) picks.putIfAbsent(OcclusionSelector.key(x, y, z), OcclusionPick(x, y, z, false))
                }
                s += OcclusionSelector.STEP
            }
        }
        return picks
    }

    private fun summary(picks: Map<Long, OcclusionPick>) = picks.map { (k, v) -> Triple(k, v.near, Triple(v.x, v.y, v.z)) }

    @Test
    fun theFasterSelectorPicksExactlyWhatTheFirstVersionDid() {
        val random = Random(12345)
        repeat(300) {
            val seed = random.nextLong()
            val solidity = random.nextDouble(0.1, 0.9)
            val world = { x: Int, y: Int, z: Int -> Random(seed xor (x * 73856093L) xor (y * 19349663L) xor (z * 83492791L)).nextDouble() < solidity }
            val camera = Point(random.nextDouble(-8.0, 8.0), random.nextDouble(60.0, 70.0), random.nextDouble(-8.0, 8.0))
            val targets = listOf(
                OcclusionSelector.Target(Point(random.nextDouble(-8.0, 8.0), random.nextDouble(60.0, 70.0), random.nextDouble(-8.0, 8.0)), 1.0, 1 + random.nextInt(3)),
                OcclusionSelector.Target(Point(random.nextDouble(-8.0, 8.0), random.nextDouble(60.0, 70.0), random.nextDouble(-8.0, 8.0)), 2.0, 1 + random.nextInt(3)),
            )
            val floorY = 62 + random.nextInt(4)
            assertEquals(summary(reference(camera, targets, floorY, world)), summary(OcclusionSelector.select(camera, targets, floorY, world)))
        }
    }

    @Test
    fun timesTheTwoOnTheSameWork() {
        val world = { x: Int, y: Int, z: Int -> (x * 31 + y * 17 + z * 13) % 3 == 0 }
        val camera = Point(0.5, 64.5, 0.5)
        val targets = listOf(
            OcclusionSelector.Target(Point(10.0, 64.0, 6.0), 1.0, 3),
            OcclusionSelector.Target(Point(-6.0, 64.0, 12.0), 1.0, 3),
        )
        fun time(run: () -> Unit): Double {
            repeat(200) { run() }
            val start = System.nanoTime()
            repeat(1000) { run() }
            return (System.nanoTime() - start) / 1000.0 / 1_000_000.0
        }
        val before = time { reference(camera, targets, 63, world) }
        val after = time { OcclusionSelector.select(camera, targets, 63, world) }
        println("PERF selector: first version %.3f ms per choice, now %.3f ms per choice (selecting only; mean of 1000 runs after 200 warm-up runs; a cheap stand-in for the world, so reading real blocks is not included)".format(before, after))
    }
}
