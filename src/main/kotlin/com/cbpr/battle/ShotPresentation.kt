package com.cbpr.battle

import com.cbpr.audio.SoundSlot

interface ShotPresentation {

    fun callout(move: ShotDirector.MoveUsed) {}

    fun result(won: Boolean, winner: String) {}

    fun sound(slot: SoundSlot, monSide: Boolean?, pokemon: String? = null) {}

    fun stamp(kind: StampKind, message: MessageText) {}

    fun damageNumber(lost: Double, monSide: Boolean?, pokemon: String? = null) {}

    fun cue(label: String, rgb: Int, monSide: Boolean?, pokemon: String? = null) {}

    fun volatile(id: String, monSide: Boolean?, pokemon: String? = null) {}

    fun field(id: String, started: Boolean) {}

    object None : ShotPresentation
}
