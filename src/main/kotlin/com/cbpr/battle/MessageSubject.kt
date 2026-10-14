package com.cbpr.battle

data class MessageSubject(val monSide: Boolean?, val slot: String?) {
    class Named(val slot: String, val name: String)

    class Roster(val owner: String, val members: List<Named>)

    companion object {
        val UNKNOWN = MessageSubject(null, null)

        fun resolve(text: String, mons: Roster, foes: Roster): MessageSubject {
            val onMons = mentions(text, mons)
            if (onMons == mentions(text, foes)) return UNKNOWN
            val side = if (onMons) mons else foes
            return MessageSubject(onMons, slotIn(text, side.members))
        }

        private fun mentions(text: String, roster: Roster) = named(text, roster.owner) || roster.members.any { named(text, it.name) }

        private fun named(text: String, name: String): Boolean =
            name.isNotBlank() && Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(name)}(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE).containsMatchIn(text)

        private fun slotIn(text: String, members: List<Named>): String? =
            members.singleOrNull()?.slot ?: members.filter { named(text, it.name) }.singleOrNull()?.slot
    }
}
