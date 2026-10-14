package com.cbpr.camera

import kotlin.math.max

class GroupMember(val point: Point, val size: Double)

object PokemonGroup {

    fun centre(members: List<GroupMember>): Point? {
        if (members.isEmpty()) return null
        val sum = members.fold(Point(0.0, 0.0, 0.0)) { total, member -> total + member.point }
        return sum * (1.0 / members.size)
    }

    fun size(members: List<GroupMember>): Double {
        if (members.isEmpty()) return 0.0
        val biggest = members.maxOf { it.size }
        if (members.size == 1) return biggest
        var widest = 0.0
        for (i in members.indices) for (j in i + 1 until members.size) widest = max(widest, (members[i].point - members[j].point).length())
        return widest + biggest
    }
}

object ShotSubjects {
    class Pair(val first: Point, val second: Point)

    fun action(plan: ShotPlan, mon: Point, foe: Point, battle: BattleSnapshot): Pair {
        val attacker = battle.point(plan.attackerSlot, if (plan.attackerIsMon) mon else foe)
        val victim = battle.point(plan.targetSlot, if (plan.attackerIsMon) foe else mon)
        return Pair(attacker, victim)
    }

    fun beat(plan: BeatPlan, mon: Point, foe: Point, battle: BattleSnapshot): Pair {
        val subject = battle.point(plan.subjectSlot, if (plan.subjectIsMon) mon else foe)
        return if (plan.subjectIsMon) Pair(subject, foe) else Pair(mon, subject)
    }
}
