package com.cbpr.battle

import com.cbpr.audio.Sfx
import com.cbpr.audio.SoundSlot
import com.cbpr.camera.Point
import com.cbpr.config.Settings
import com.cbpr.render.CinematicHud
import com.cbpr.render.DamageNumbers
import com.cbpr.ui.PartyPreview
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory

class BattlePresentation(
    private val mon: () -> PokemonEntity?,
    private val foe: () -> PokemonEntity?,
    private val field: FieldState,

    private val entityAt: (String) -> PokemonEntity? = { null },
) : ShotPresentation {
    private val log = LoggerFactory.getLogger("cbpr")

    override fun callout(move: ShotDirector.MoveUsed) {
        val template = Moves.getByName(move.moveId)

        val rgb = template?.takeIf { Settings.current.typeColour }?.let { PartyPreview.legible(it.elementalType.primaryColor and 0xFFFFFF) and 0xFFFFFF }
        CinematicHud.showCallout(template?.displayName ?: Component.literal(move.moveId), rgb ?: 0xFFFFFF)
    }

    override fun result(won: Boolean, winner: String) {
        CinematicHud.showResult(Component.translatable("cobblemon.battle.win", winner))
    }

    override fun sound(slot: SoundSlot, monSide: Boolean?, pokemon: String?) {
        val entity = pokemon?.let(entityAt) ?: when (monSide) {
            true -> mon()
            false -> foe()
            null -> null
        }
        Sfx.play(slot, entity.centre(0f))
    }

    override fun stamp(kind: StampKind, message: MessageText) {
        if (!Language.getInstance().has(message.key)) return
        CinematicHud.showStamp(Component.translatable(message.key, *message.args.toTypedArray()), kind.rgb)
    }

    override fun damageNumber(lost: Double, monSide: Boolean?, pokemon: String?) {
        val anchor = headAnchor(monSide, pokemon, 0.0) ?: return
        DamageNumbers.spawn(lost, anchor)
    }

    override fun cue(label: String, rgb: Int, monSide: Boolean?, pokemon: String?) {
        val anchor = headAnchor(monSide, pokemon, CUE_LIFT) ?: return
        DamageNumbers.spawnCue(label, rgb, anchor)
    }

    override fun volatile(id: String, monSide: Boolean?, pokemon: String?) {
        val key = "cbpr.volatile.$id"
        if (!Language.getInstance().has(key)) return
        cue(Component.translatable(key).string, VolatileKeys.RGB, monSide, pokemon)
    }

    override fun field(id: String, started: Boolean) {
        field.apply(id, started)
        log.info("CBPR field {} {}, vignette colour {}", id, if (started) "started" else "ended", field.current?.let(FieldWash::rgb)?.toString(16))
    }

    private fun headAnchor(monSide: Boolean?, pokemon: String?, extra: Double): Point? {
        val entity = pokemon?.let(entityAt) ?: (if (monSide == true) mon() else if (monSide == false) foe() else null) ?: return null
        if (entity.isRemoved) return null
        val feet = entity.getPosition(0f)
        return Point(feet.x, feet.y + entity.bbHeight + NUMBER_LIFT + extra, feet.z)
    }

    private companion object {
        const val NUMBER_LIFT = 0.2
        const val CUE_LIFT = 0.55
    }
}

internal fun PokemonEntity?.centre(partialTick: Float): Point? {
    if (this == null || isRemoved) return null
    val p = getPosition(partialTick)
    return Point(p.x, p.y + bbHeight / 2.0, p.z)
}
