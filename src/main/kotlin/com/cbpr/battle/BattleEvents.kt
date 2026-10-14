package com.cbpr.battle

import com.cbpr.camera.GimmickStyle
import com.cobblemon.mod.common.client.CobblemonClient
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import org.slf4j.LoggerFactory
import java.util.UUID

object BattleEvents {
    private val log = LoggerFactory.getLogger("cbpr")
    private val queue = BattleEventQueue()
    private var reported = false

    fun drain(): List<BattleEvent> = queue.drain()

    fun clear() {
        queue.clear()
        shares.clear()
    }

    fun messages(messages: List<Component>) = guarded { messages.forEach(::scan) }

    private val shares = HashMap<UUID, Double>()

    fun health(pnx: String, newHealth: Float, newMaxHealth: Float?) = guarded {
        val battle = CobblemonClient.battle ?: return@guarded
        val (actor, active) = battle.getPokemonFromPNX(pnx) ?: return@guarded
        val pokemon = active.battlePokemon ?: return@guarded
        val now = HpShare.of(pokemon.isHpFlat, newHealth, newMaxHealth ?: pokemon.maxHp) ?: return@guarded
        val before = shares[pokemon.uuid] ?: HpShare.of(pokemon.isHpFlat, pokemon.hpValue, pokemon.maxHp) ?: return@guarded
        shares[pokemon.uuid] = now
        queue.post(BattleEvent.Damage(before - now, actor.uuid == Minecraft.getInstance().player?.uuid, pnx))
    }

    fun fainted(pnx: String) = guarded {
        val actor = CobblemonClient.battle?.getPokemonFromPNX(pnx)?.first ?: return@guarded
        queue.post(BattleEvent.Fainted(actor.uuid == Minecraft.getInstance().player?.uuid, pnx))
    }

    fun sentOut(isAlly: Boolean, pnx: String) = guarded { queue.post(BattleEvent.SentOut(isAlly, pnx)) }

    private fun scan(message: Component) {
        val contents = message.contents
        if (contents is TranslatableContents) {
            val args = contents.args
            when (MessageKind.of(contents.key)) {
                MessageKind.MOVE_USED -> if (args.size >= 2) queue.post(BattleEvent.MoveUsed(text(args[0]), moveId(args[1]), args.getOrNull(2)?.let(::text)))
                MessageKind.CRIT -> queue.post(BattleEvent.Crit(messageText(contents)))
                MessageKind.SUPER_EFFECTIVE -> queue.post(BattleEvent.SuperEffective(messageText(contents)))
                MessageKind.NO_EFFECT -> queue.post(BattleEvent.NoEffect(messageText(contents)))
                MessageKind.WIN -> postOutcome(args)
                MessageKind.MEGA -> gimmick(args, GimmickStyle.MEGA)
                MessageKind.TERA -> gimmick(args, GimmickStyle.TERA)
                MessageKind.ZPOWER -> gimmick(args, GimmickStyle.ZPOWER)
                MessageKind.ULTRA -> gimmick(args, GimmickStyle.ULTRA)
                MessageKind.TRANSFORMATION -> if (args.isNotEmpty()) queue.post(BattleEvent.Transformation(text(args[0])))
                MessageKind.DYNAMAX -> if (args.isNotEmpty()) queue.post(BattleEvent.Dynamax(text(args[0])))
                MessageKind.STAT_CHANGE -> statChange(contents.key, args)
                MessageKind.VOLATILE -> VolatileKeys.of(contents.key)?.let { id ->
                    val index = VolatileKeys.afflictedArgument(id, args.size)
                    if (args.size > index) queue.post(BattleEvent.Volatile(text(args[index]), id))
                }
                MessageKind.FIELD_CHANGE -> FieldKeys.of(contents.key)?.let { queue.post(BattleEvent.Field(it.id, it.started, messageText(contents))) }
                null -> Unit
            }
        }
        message.siblings.forEach(::scan)
    }

    private fun statChange(key: String, args: Array<Any?>) {
        val stages = StatStages.of(key) ?: return
        if (args.size >= 2) queue.post(BattleEvent.StatChange(text(args[0]), text(args[1]), stages))
    }

    private fun messageText(contents: TranslatableContents) = MessageText(contents.key, contents.args.map(::text))

    private fun gimmick(args: Array<Any?>, style: GimmickStyle) {
        if (args.isNotEmpty()) queue.post(BattleEvent.Gimmick(text(args[0]), style))
    }

    private fun postOutcome(args: Array<Any?>) {
        if (args.isNotEmpty()) queue.post(BattleEvent.Outcome(text(args[0])))
    }

    private fun text(argument: Any?): String = (argument as? Component)?.string ?: argument.toString()

    private fun moveId(argument: Any?): String {
        val key = ((argument as? Component)?.contents as? TranslatableContents)?.key
        return MoveProfile.moveId(key?.substringAfterLast('.') ?: text(argument))
    }

    private fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            if (!reported) {
                reported = true
                log.error("CBPR could not read a battle event, ignoring events it cannot read", e)
            }
        }
    }
}
