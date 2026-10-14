package com.cbpr.audio

enum class SoundSlot(val id: String, val group: Group, val positional: Boolean = false) {
    UI_HOVER("ui_hover", Group.NAVIGATION),
    UI_SELECT("ui_select", Group.UI),
    UI_CONFIRM("ui_confirm", Group.UI),
    UI_BACK("ui_back", Group.UI),
    UI_UNAVAILABLE("ui_unavailable", Group.UI),
    PANEL_OPEN("panel_open", Group.NAVIGATION),
    PANEL_CLOSE("panel_close", Group.NAVIGATION),
    MENU_OPEN("menu_open", Group.NAVIGATION),
    SPECIAL_HIGHLIGHT("special_highlight", Group.UI),
    TRANSITION("transition", Group.CINEMATIC),
    TURN_START("turn_start", Group.BATTLE),
    SUPER_EFFECTIVE("super_effective", Group.BATTLE, positional = true),
    NOT_EFFECTIVE("not_effective", Group.BATTLE, positional = true),
    CRIT("crit", Group.BATTLE, positional = true),
    STATUS_APPLIED("status_applied", Group.BATTLE, positional = true),
    TRANSFORMATION("transformation", Group.BATTLE, positional = true),
    SWITCH("switch", Group.BATTLE, positional = true),
    FAINT("faint", Group.BATTLE, positional = true),
    VICTORY("victory", Group.BATTLE),
    DEFEAT("defeat", Group.BATTLE),
    FLEE("flee", Group.BATTLE);

    enum class Group { NAVIGATION, UI, CINEMATIC, BATTLE }

    companion object {
        fun fromId(id: String?): SoundSlot? = entries.firstOrNull { it.id == id }
    }
}
