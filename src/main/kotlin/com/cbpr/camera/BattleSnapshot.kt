package com.cbpr.camera

class BattleSnapshot(
    val mons: List<Member> = emptyList(),
    val foes: List<Member> = emptyList(),
    val commandedSlot: String? = null,
    val targetStep: Boolean = false,
) {

    class Member(val slot: String, val point: Point, val size: Double)

    private val bySlot = (mons + foes).associateBy { it.slot }
    private val monGroup = mons.map { GroupMember(it.point, it.size) }
    private val foeGroup = foes.map { GroupMember(it.point, it.size) }

    val monCentre: Point? = PokemonGroup.centre(monGroup)
    val foeCentre: Point? = PokemonGroup.centre(foeGroup)
    val monGroupSize: Double = if (mons.isEmpty()) ORDINARY_SIZE else PokemonGroup.size(monGroup)
    val foeGroupSize: Double = if (foes.isEmpty()) ORDINARY_SIZE else PokemonGroup.size(foeGroup)

    val monSize: Double = mons.firstOrNull()?.size ?: ORDINARY_SIZE
    val foeSize: Double = foes.firstOrNull()?.size ?: ORDINARY_SIZE

    val commanded: Member? = commandedSlot?.let { slot -> mons.firstOrNull { it.slot == slot } }

    fun point(slot: String?, side: Point): Point = inSlotOr(slot, side, Member::point)

    fun size(slot: String?, side: Double): Double = inSlotOr(slot, side, Member::size)

    private fun <T : Any> inSlotOr(slot: String?, side: T, of: (Member) -> T): T = slot?.let(bySlot::get)?.let(of) ?: side

    companion object {

        const val ORDINARY_SIZE = 1.0
    }
}
