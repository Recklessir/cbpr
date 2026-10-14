package com.cbpr.battle

import com.cbpr.camera.Point
import com.cbpr.camera.TargetPicker
import com.cbpr.render.MenuHost
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleTargetSelection
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import org.slf4j.LoggerFactory

class TargetMarker(val point: Point, val label: String, val hovered: Boolean)

class TargetStep(private val session: BattleSession<PokemonEntity, ClientBattlePokemon>) {
    private val log = LoggerFactory.getLogger("cbpr")

    var hovered: String? = null
        private set
    private var lastSelection: BattleTargetSelection? = null

    fun markers(selection: BattleTargetSelection?, partialTick: Float): List<TargetMarker> {
        selection ?: return emptyList()
        return selection.targetTiles.filter { it.selectable }.mapNotNull { tile ->
            val slot = tile.target.getPNX()
            val entity = session.combatant(slot)?.entity?.takeIf { !it.isRemoved } ?: return@mapNotNull null
            val feet = entity.getPosition(partialTick)
            TargetMarker(Point(feet.x, feet.y + entity.bbHeight + MARKER_LIFT, feet.z), tile.target.battlePokemon?.displayName?.string ?: slot, slot == hovered)
        }
    }

    fun track(selection: BattleTargetSelection?, ray: MenuHost.CursorRay?): BattleTargetSelection.TargetTile? {
        if (selection == null) {
            lastSelection = null
            hovered = null
            return null
        }
        if (ray == null) {
            hovered = null
            return null
        }
        val tiles = selection.targetTiles.filter { it.selectable }
        if (selection !== lastSelection) {
            lastSelection = selection
            log.info(
                "CBPR target step: {} of {} targets selectable ({}), roster {}, move {} targets {}, commanding {}",
                tiles.size, selection.targetTiles.size, tiles.map { it.target.getPNX() }, (session.mons + session.foes).map { it.slot },
                selection.move?.id, selection.targetType, selection.request.activePokemon.getPNX(),
            )
        }
        val boxes = tiles.mapNotNull { tile ->
            val slot = tile.target.getPNX()
            val box = session.combatant(slot)?.entity?.boundingBox?.inflate(TARGET_GRAB) ?: return@mapNotNull null
            TargetPicker.Candidate(slot, Point(box.minX, box.minY, box.minZ), Point(box.maxX, box.maxY, box.maxZ))
        }
        val slot = TargetPicker.pick(ray.origin, ray.direction, boxes, keep = hovered, keepGrow = TARGET_STICKY)
        hovered = slot
        return slot?.let { picked -> tiles.firstOrNull { it.target.getPNX() == picked } }
    }

    fun click(selection: BattleTargetSelection?, ray: MenuHost.CursorRay?): Boolean {
        val tile = track(selection, ray) ?: return false
        tile.onClick()
        return true
    }

    private companion object {
        const val TARGET_GRAB = 0.5
        const val TARGET_STICKY = 0.5
        const val MARKER_LIFT = 0.35
    }
}
