package com.cbpr.ui

class Rect(val x: Double, val y: Double, val width: Double, val height: Double) {
    fun contains(px: Double, py: Double) = px >= x && px < x + width && py >= y && py < y + height
}

object MenuRegions {
    private const val MENU_WIDTH = 360.0
    private const val MENU_HEIGHT = 210.0
    private const val LOG_WIDTH = 200.0
    private const val LOG_HEIGHT = 130.0

    fun forfeit(guiWidth: Double, guiHeight: Double) = Rect(
        (guiWidth.toInt() / 2 - FORFEIT_LEFT - FORFEIT_MARGIN).toDouble(),
        (guiHeight.toInt() / 2 - FORFEIT_UP - FORFEIT_MARGIN).toDouble(),
        (FORFEIT_WIDTH + 2 * FORFEIT_MARGIN).toDouble(),
        (FORFEIT_HEIGHT + 2 * FORFEIT_MARGIN).toDouble(),
    )

    private const val FORFEIT_WIDTH = 113
    private const val FORFEIT_HEIGHT = 45
    private const val FORFEIT_LEFT = 56
    private const val FORFEIT_UP = 22
    private const val FORFEIT_MARGIN = 6

    fun menu(guiHeight: Double) = Rect(0.0, guiHeight - MENU_HEIGHT, MENU_WIDTH, MENU_HEIGHT)

    fun log(guiWidth: Double, guiHeight: Double) = Rect(guiWidth - LOG_WIDTH, guiHeight - LOG_HEIGHT, LOG_WIDTH, LOG_HEIGHT)
}
