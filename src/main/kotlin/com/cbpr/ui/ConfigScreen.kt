package com.cbpr.ui

import com.cbpr.config.CbprConfig
import com.cbpr.config.Settings
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

class ConfigScreen(private val parent: Screen?) : Screen(Component.translatable("cbpr.config.title")) {
    private val initial = Settings.current
    private var config = initial
    private var tabIndex = 0

    private val tab: Tab get() = SettingsModel.tabs[tabIndex]

    override fun init() {
        val total = min(width - 20, MAX_WIDTH)
        val tabs = SettingsModel.tabs
        addRenderableWidget(toggle(SettingsModel.master, MASTER_WIDTH).also { it.x = (width - MASTER_WIDTH) / 2; it.y = MASTER_Y })
        val tabWidth = min(MAX_TAB, (total - TAB_GAP * (tabs.size - 1)) / tabs.size)
        val tabsLeft = (width - (tabWidth * tabs.size + TAB_GAP * (tabs.size - 1))) / 2
        for ((index, entry) in tabs.withIndex()) {
            addRenderableWidget(
                Button.builder(Component.translatable("cbpr.tab.${entry.id}")) {
                    tabIndex = index
                    rebuildWidgets()
                }.bounds(tabsLeft + index * (tabWidth + TAB_GAP), TAB_Y, tabWidth, BUTTON_HEIGHT).build().also { it.active = index != tabIndex },
            )
        }

        val entries = tab.entries
        val room = height - BOTTOM_ROOM - SETTINGS_Y
        val columns = (2..4).firstOrNull { ceil(entries.size / it.toDouble()) * MIN_ROW_STEP <= room } ?: 4
        val columnWidth = min(MAX_COLUMN, (total - COLUMN_GAP * (columns - 1)) / columns)
        val gridWidth = columnWidth * columns + COLUMN_GAP * (columns - 1)
        val gridLeft = (width - gridWidth) / 2
        val rowStep = if (ceil(entries.size / columns.toDouble()) * ROW_STEP > room) MIN_ROW_STEP else ROW_STEP
        for ((index, entry) in entries.withIndex()) {
            val widget = widgetFor(entry, columnWidth)
            widget.x = gridLeft + (index % columns) * (columnWidth + COLUMN_GAP)
            widget.y = SETTINGS_Y + (index / columns) * rowStep
            addRenderableWidget(widget)
        }

        val bottom = height - 28
        val buttonWidth = min(100, (total - 2 * COLUMN_GAP) / 3)
        val start = (width - (3 * buttonWidth + 2 * COLUMN_GAP)) / 2
        addRenderableWidget(
            Button.builder(Component.translatable("cbpr.ui.reset")) {
                change(SettingsModel.reset(config, tab))
                rebuildWidgets()
            }.bounds(start, bottom, buttonWidth, BUTTON_HEIGHT).build(),
        )
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel")) { cancel() }.bounds(start + buttonWidth + COLUMN_GAP, bottom, buttonWidth, BUTTON_HEIGHT).build())
        addRenderableWidget(Button.builder(Component.translatable("gui.done")) { onClose() }.bounds(start + 2 * (buttonWidth + COLUMN_GAP), bottom, buttonWidth, BUTTON_HEIGHT).build())
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF)
    }

    private fun widgetFor(entry: Entry, widgetWidth: Int): AbstractWidget = when (entry) {
        is Entry.Switch -> toggle(entry.toggle, widgetWidth)
        Entry.Volume -> VolumeSlider(widgetWidth)
    }

    private fun toggle(toggle: Toggle, widgetWidth: Int): AbstractWidget =
        CycleButton.onOffBuilder(toggle.get(config))
            .create(0, 0, widgetWidth, BUTTON_HEIGHT, Component.translatable("cbpr.config.${toggle.key}")) { _, value ->
                change(toggle.set(config, value))
            }

    private fun change(next: CbprConfig) {
        config = next
        Settings.apply(config)
    }

    private fun cancel() {
        Settings.apply(initial)
        minecraft!!.setScreen(parent)
    }

    override fun onClose() {
        minecraft!!.setScreen(parent)
    }

    override fun removed() {
        Settings.save()
    }

    private inner class VolumeSlider(widgetWidth: Int) : AbstractSliderButton(0, 0, widgetWidth, BUTTON_HEIGHT, label(config.soundVolume), config.soundVolume) {
        override fun updateMessage() {
            message = label(value)
        }

        override fun applyValue() {
            change(config.copy(soundVolume = value))
        }
    }

    private fun label(volume: Double): Component =
        Component.translatable("cbpr.config.soundVolume").append(Component.literal(": ${(volume * 100).roundToInt()}%"))

    private companion object {
        const val MAX_WIDTH = 420
        const val MAX_COLUMN = 150
        const val COLUMN_GAP = 8
        const val TAB_GAP = 2
        const val MASTER_Y = 24
        const val MASTER_WIDTH = 150
        const val MAX_TAB = 110
        const val TAB_Y = 50
        const val SETTINGS_Y = 80
        const val BUTTON_HEIGHT = 20
        const val ROW_STEP = 24
        const val MIN_ROW_STEP = 22
        const val BOTTOM_ROOM = 36
    }
}
