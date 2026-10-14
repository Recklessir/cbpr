package com.cbpr.camera

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

class OcclusionPick(val x: Int, val y: Int, val z: Int, val near: Boolean)

object OcclusionSelector {

    class Target(val point: Point, val clearEnd: Double, val farRadius: Int = 2)

    const val NEAR_RANGE = 1.2
    const val NEAR_RADIUS = 1
    const val STEP = 0.5
    const val MAX_BLOCKS = 400

    fun key(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and 0x3FFFFFF) shl 38) or ((z.toLong() and 0x3FFFFFF) shl 12) or (y.toLong() and 0xFFF)

    fun select(camera: Point, targets: List<Target>, floorY: Int, isSolid: (Int, Int, Int) -> Boolean): Map<Long, OcclusionPick> {
        val picks = LinkedHashMap<Long, OcclusionPick>()

        val asked = Long2ByteOpenHashMap()
        fun solid(x: Int, y: Int, z: Int): Boolean {
            val key = key(x, y, z)
            var known = asked.get(key)
            if (known.toInt() == 0) {
                known = if (isSolid(x, y, z)) 1 else 2
                asked.put(key, known)
            }
            return known.toInt() == 1
        }

        val lowest = if (camera.y < floorY) Int.MIN_VALUE else floorY
        val cx = floor(camera.x).toInt()
        val cy = floor(camera.y).toInt()
        val cz = floor(camera.z).toInt()
        for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
            val x = cx + dx
            val y = cy + dy
            val z = cz + dz
            if (distanceToBlock(camera, x, y, z) <= NEAR_RANGE && solid(x, y, z)) picks[key(x, y, z)] = OcclusionPick(x, y, z, true)
        }
        for (target in targets) {
            val along = target.point - camera
            val length = along.length()
            if (length < 1e-6) continue
            var s = 0.0

            var lastBx = Int.MIN_VALUE
            var lastBy = Int.MIN_VALUE
            var lastBz = Int.MIN_VALUE
            var lastRadius = -1
            while (s <= length - target.clearEnd && picks.size < MAX_BLOCKS) {
                val radius = (NEAR_RADIUS + (target.farRadius - NEAR_RADIUS) * (s / length)).roundToInt().coerceAtLeast(NEAR_RADIUS)
                val p = camera + along * (s / length)
                val bx = floor(p.x).toInt()
                val by = floor(p.y).toInt()
                val bz = floor(p.z).toInt()
                if (bx == lastBx && by == lastBy && bz == lastBz && radius == lastRadius) {
                    s += STEP
                    continue
                }
                lastBx = bx
                lastBy = by
                lastBz = bz
                lastRadius = radius
                for (dx in -radius..radius) for (dy in -radius..radius) for (dz in -radius..radius) {
                    val x = bx + dx
                    val y = by + dy
                    val z = bz + dz
                    if (y >= lowest && solid(x, y, z)) picks.putIfAbsent(key(x, y, z), OcclusionPick(x, y, z, false))
                }
                s += STEP
            }
        }
        return picks
    }

    private fun distanceToBlock(p: Point, x: Int, y: Int, z: Int): Double {
        val dx = max(max(x - p.x, p.x - (x + 1)), 0.0)
        val dy = max(max(y - p.y, p.y - (y + 1)), 0.0)
        val dz = max(max(z - p.z, p.z - (z + 1)), 0.0)
        return Point(dx, dy, dz).length()
    }
}

class OcclusionBlocks(private val onDirty: (sectionX: Int, sectionY: Int, sectionZ: Int) -> Unit) {
    enum class Phase { NEW, FADED, RISING, LINGER }

    class Entry(val x: Int, val y: Int, val z: Int) {
        var phase = Phase.NEW
        var goal = FADED_ALPHA
        var timer = 0.0
        var unseen = 0.0
        val alpha = Spring(1.0, STIFFNESS, Spring.dampingFor(STIFFNESS, 1.0))
    }

    private val table = HashMap<Long, Entry>()
    private val pending = HashMap<Long, IntArray>()

    private class Hidden(val set: LongOpenHashSet, val minX: Int, val maxX: Int, val minY: Int, val maxY: Int, val minZ: Int, val maxZ: Int)

    @Volatile
    private var hidden = EMPTY

    val entries: Collection<Entry> get() = table.values
    val active: Boolean get() = table.isNotEmpty()

    fun hides(x: Int, y: Int, z: Int): Boolean {
        val h = hidden
        return x >= h.minX && x <= h.maxX && y >= h.minY && y <= h.maxY && z >= h.minZ && z <= h.maxZ && h.set.contains(OcclusionSelector.key(x, y, z))
    }

    fun choose(picks: Map<Long, OcclusionPick>) {
        var changed = false
        for ((key, pick) in picks) {
            val goal = if (pick.near) 0.0 else FADED_ALPHA
            val entry = table[key]
            if (entry == null) {
                table[key] = Entry(pick.x, pick.y, pick.z).also {
                    it.goal = goal
                    markDirty(it)
                }
                changed = true
                continue
            }
            entry.unseen = 0.0
            entry.goal = goal
            when (entry.phase) {
                Phase.RISING -> entry.phase = Phase.FADED
                Phase.LINGER -> {
                    entry.phase = Phase.NEW
                    entry.timer = 0.0
                    entry.alpha.snap(1.0)
                    markDirty(entry)
                    changed = true
                }
                else -> {}
            }
        }
        if (changed) publish()
        flush()
    }

    fun releaseAll() {
        for (entry in table.values) entry.unseen = max(entry.unseen, HOLD_SECONDS + 1e-3)
    }

    fun step(dt: Double) {
        var changed = false
        val iterator = table.values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.unseen += dt
            entry.timer += dt
            when (entry.phase) {
                Phase.NEW -> when {
                    entry.unseen > HOLD_SECONDS -> entry.phase = Phase.RISING
                    entry.timer >= MESH_DELAY_SECONDS -> entry.phase = Phase.FADED
                }
                Phase.FADED -> {
                    entry.alpha.step(entry.goal, dt)
                    if (entry.unseen > HOLD_SECONDS) entry.phase = Phase.RISING
                }
                Phase.RISING -> {
                    entry.alpha.step(1.0, dt)
                    if (entry.alpha.value >= SOLID_ALPHA) {
                        entry.alpha.snap(1.0)
                        entry.phase = Phase.LINGER
                        entry.timer = 0.0
                        markDirty(entry)
                        changed = true
                    }
                }
                Phase.LINGER -> if (entry.timer >= LINGER_SECONDS) iterator.remove()
            }
        }
        if (changed) publish()
        flush()
    }

    fun clear() {
        for (entry in table.values) if (entry.phase != Phase.LINGER) markDirty(entry)
        table.clear()
        hidden = EMPTY
        flush()
    }

    private fun publish() {
        val next = LongOpenHashSet()
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        var minZ = Int.MAX_VALUE
        var maxZ = Int.MIN_VALUE
        for ((key, entry) in table) {
            if (entry.phase == Phase.LINGER) continue
            next.add(key)
            minX = minOf(minX, entry.x)
            maxX = maxOf(maxX, entry.x)
            minY = minOf(minY, entry.y)
            maxY = maxOf(maxY, entry.y)
            minZ = minOf(minZ, entry.z)
            maxZ = maxOf(maxZ, entry.z)
        }
        hidden = if (next.isEmpty()) EMPTY else Hidden(next, minX, maxX, minY, maxY, minZ, maxZ)
    }

    private fun markDirty(entry: Entry) {
        val sx = entry.x shr 4
        val sy = entry.y shr 4
        val sz = entry.z shr 4
        pending.putIfAbsent(OcclusionSelector.key(sx, sy, sz), intArrayOf(sx, sy, sz))
    }

    private fun flush() {
        for (section in pending.values) onDirty(section[0], section[1], section[2])
        pending.clear()
    }

    companion object {

        private val EMPTY = Hidden(LongOpenHashSet(), Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MIN_VALUE)
        const val FADED_ALPHA = 0.25
        const val SOLID_ALPHA = 0.98
        const val STIFFNESS = 60.0
        const val MESH_DELAY_SECONDS = 0.25
        const val HOLD_SECONDS = 0.5
        const val LINGER_SECONDS = 0.4
    }
}
