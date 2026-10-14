package com.cbpr.ui

import kotlin.math.ceil
import kotlin.math.min

data class SlotHandle(val kind: BannerKind, val number: Int)

enum class SlotRole { TILE, EXTRA, PANEL }

class BannerSlot(val handle: SlotHandle, val real: Rect, val art: Rect, val role: SlotRole) {
    val kind: BannerKind get() = handle.kind
}

class BannerSlots {
    private val registered = ArrayList<BannerSlot>()
    private val perKind = HashMap<BannerKind, Int>()
    private var guiWidth = 0.0
    private var screen = BattleScreen.NONE
    private var room = 0

    private var used = 0

    val slots: List<BannerSlot> get() = registered

    var stripWidth = 0
        private set

    var overCeiling = false
        private set

    fun begin(guiWidth: Double, widgets: Int, screen: BattleScreen) {
        clear()
        this.guiWidth = guiWidth
        this.screen = screen
        room = min(widgets, MAX_HOSTED)
        overCeiling = widgets > MAX_HOSTED
        stripWidth = ceil(ART_MARGIN + PREVIEW_COLUMN + ceil(room / ROWS.toDouble()) * COLUMN_WIDTH).toInt()
    }

    fun clear() {
        registered.clear()
        perKind.clear()
        used = 0
        room = 0
        stripWidth = 0
        overCeiling = false
    }

    fun register(kind: BannerKind, real: Rect): BannerSlot? {
        val art: Rect
        if (kind == BannerKind.PREVIEW) {
            art = Rect(guiWidth + ART_MARGIN, ART_MARGIN, kind.fullWidth.toDouble(), kind.fullHeight.toDouble())
        } else {

            if (kind == BannerKind.ADDON && (real.width <= 0 || real.height <= 0 || real.width > kind.artWidth || real.height > kind.artHeight)) return null
            val position = used
            if (position >= room) return null
            used++
            art = Rect(
                guiWidth + ART_MARGIN + PREVIEW_COLUMN + (position / ROWS) * COLUMN_WIDTH,
                ART_MARGIN + (position % ROWS) * ROW_HEIGHT,
                kind.fullWidth.toDouble(), kind.fullHeight.toDouble(),
            )
        }
        val number = perKind.merge(kind, 1, Int::plus)!! - 1
        return BannerSlot(SlotHandle(kind, number), real, art, roleOf(kind)).also { registered += it }
    }

    fun drop(slot: BannerSlot) {
        registered.remove(slot)
    }

    private fun roleOf(kind: BannerKind) = when (kind) {
        BannerKind.PREVIEW -> SlotRole.PANEL
        BannerKind.BACK, BannerKind.GIMMICK, BannerKind.SHIFT -> SlotRole.EXTRA
        BannerKind.ADDON -> if (screen == BattleScreen.GENERAL) SlotRole.TILE else SlotRole.EXTRA
        else -> SlotRole.TILE
    }

    companion object {

        const val MAX_HOSTED = 64

        private const val ART_MARGIN = 4.0
        private const val COLUMN_WIDTH = 120.0
        private const val ROW_HEIGHT = 38.0
        private const val ROWS = 4

        private const val PREVIEW_COLUMN = 212.0
    }
}
