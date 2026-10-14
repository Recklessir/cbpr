package com.cbpr.camera

import com.cbpr.battle.MoveProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DoublesFramingTest {
    private fun member(x: Double, z: Double, size: Double = 1.0) = GroupMember(Point(x, 1.0, z), size)

    @Test
    fun aGroupOfOneIsThatPokemon() {
        val only = listOf(member(3.0, 4.0, 2.0))
        assertEquals(Point(3.0, 1.0, 4.0), PokemonGroup.centre(only))
        assertEquals(2.0, PokemonGroup.size(only))
    }

    @Test
    fun anEmptyGroupHasNoCentreAndNoSize() {
        assertNull(PokemonGroup.centre(emptyList()))
        assertEquals(0.0, PokemonGroup.size(emptyList()))
    }

    @Test
    fun aPairIsFramedAtItsMiddleAndNeedsMoreRoomTheFurtherApartItIs() {
        val near = listOf(member(0.0, 0.0), member(2.0, 0.0))
        val far = listOf(member(0.0, 0.0), member(6.0, 0.0))
        assertEquals(Point(1.0, 1.0, 0.0), PokemonGroup.centre(near))
        assertEquals(3.0, PokemonGroup.size(near))
        assertEquals(7.0, PokemonGroup.size(far))
    }

    private val mon = Point(0.0, 1.0, 0.0)
    private val foe = Point(0.0, 1.0, 8.0)
    private val monB = Point(3.0, 1.0, 0.0)
    private val foeB = Point(-3.0, 1.0, 8.0)
    private fun battle(owner: String? = null, sizes: Map<String, Double> = emptyMap()) = BattleSnapshot(
        listOf(BattleSnapshot.Member("p1a", mon, sizes["p1a"] ?: 1.0), BattleSnapshot.Member("p1b", monB, sizes["p1b"] ?: 1.0)),
        listOf(BattleSnapshot.Member("p2a", foe, sizes["p2a"] ?: 1.0), BattleSnapshot.Member("p2b", foeB, sizes["p2b"] ?: 1.0)),
        owner,
    )

    private val profile = MoveProfile.GENERIC

    private fun plan(attackerIsMon: Boolean, attackerSlot: String? = null, targetSlot: String? = null) =
        ShotPlan(profile, attackerIsMon, reducedMotion = false, hitStopEnabled = true, critSpecials = true, attackerSlot = attackerSlot, targetSlot = targetSlot)

    private fun beat(subjectIsMon: Boolean, slot: String?) = BeatPlan(BeatPlan.Kind.FAINT, subjectIsMon, reducedMotion = false, subjectSlot = slot)

    @Test
    fun anActionShotWithNoSlotsIsBetweenTheTwoSides() {
        val points = ShotSubjects.action(plan(attackerIsMon = true), mon, foe, battle())
        assertEquals(mon, points.first)
        assertEquals(foe, points.second)
        val reverse = ShotSubjects.action(plan(attackerIsMon = false), mon, foe, battle())
        assertEquals(foe, reverse.first)
        assertEquals(mon, reverse.second)
    }

    @Test
    fun anActionShotGoesFromTheNamedAttackerToTheNamedTarget() {
        val points = ShotSubjects.action(plan(attackerIsMon = true, attackerSlot = "p1b", targetSlot = "p2b"), mon, foe, battle())
        assertEquals(monB, points.first)
        assertEquals(foeB, points.second)
    }

    @Test
    fun aMoveWithNoNamedTargetAimsAtTheOpposingGroup() {
        val points = ShotSubjects.action(plan(attackerIsMon = false, attackerSlot = "p2b"), mon, foe, battle())
        assertEquals(foeB, points.first)
        assertEquals(mon, points.second)
    }

    @Test
    fun aSlotNotInTheWorldFallsBackToTheSide() {
        val points = ShotSubjects.action(plan(attackerIsMon = true, attackerSlot = "p1c", targetSlot = "p2c"), mon, foe, battle())
        assertEquals(mon, points.first)
        assertEquals(foe, points.second)
    }

    @Test
    fun aBeatAboutOnePokemonReplacesOnlyItsSidesPoint() {
        val monBeat = ShotSubjects.beat(beat(subjectIsMon = true, slot = "p1b"), mon, foe, battle())
        assertEquals(monB, monBeat.first)
        assertEquals(foe, monBeat.second)
        val foeBeat = ShotSubjects.beat(beat(subjectIsMon = false, slot = "p2b"), mon, foe, battle())
        assertEquals(mon, foeBeat.first)
        assertEquals(foeB, foeBeat.second)
    }

    @Test
    fun aBeatWithNoSlotOrAnUnknownSlotUsesTheSides() {
        val none = ShotSubjects.beat(beat(subjectIsMon = true, slot = null), mon, foe, battle())
        assertEquals(mon, none.first)
        val unknown = ShotSubjects.beat(beat(subjectIsMon = true, slot = "p1c"), mon, foe, battle())
        assertEquals(mon, unknown.first)
        assertEquals(foe, unknown.second)
    }

    private val vanilla = Pose(10.0, 65.0, 10.0, 170.0, 15.0)
    private val frame = 1.0 / 60

    private fun commandCamera(snapshot: BattleSnapshot, space: Boolean = false): BattleCamera {
        val camera = BattleCamera()
        if (space) camera.useSpace(null, true)
        camera.useBattle(snapshot)
        camera.begin(vanilla)
        repeat(240) { camera.update(frame, vanilla, mon, foe) }
        return camera
    }

    private fun poseAfterPlaying(camera: BattleCamera, plan: ShotPlan, seconds: Double): Pose {
        camera.play(plan)
        var pose: Pose? = null
        repeat((seconds / frame).toInt()) { pose = camera.update(frame, vanilla, mon, foe) }
        return assertNotNull(pose)
    }

    @Test
    fun aSlotThatIsTheSideCentreChangesNothing() {
        val withSlots = commandCamera(battle())
        val without = commandCamera(BattleSnapshot())
        val a = poseAfterPlaying(withSlots, plan(attackerIsMon = true, attackerSlot = "p1a", targetSlot = "p2a"), 0.6)
        val b = poseAfterPlaying(without, plan(attackerIsMon = true), 0.6)
        assertEquals(b.x, a.x, 1e-9)
        assertEquals(b.z, a.z, 1e-9)
        assertEquals(b.yaw, a.yaw, 1e-9)
    }

    @Test
    fun aShotAboutAPokemonOffTheSideCentreMovesTheCameraDifferently() {
        val named = commandCamera(battle())
        val plain = commandCamera(battle())
        val a = poseAfterPlaying(named, plan(attackerIsMon = true, attackerSlot = "p1b", targetSlot = "p2b"), 0.6)
        val b = poseAfterPlaying(plain, plan(attackerIsMon = true), 0.6)
        assertNotEquals(b.x, a.x)
    }

    private fun menuViewPose(owner: String?, size: Double = 1.0): Pose {
        val camera = commandCamera(battle())
        camera.menuView = true
        camera.useBattle(battle(owner, mapOf("p1a" to size, "p1b" to size)))
        var pose: Pose? = null
        repeat(240) { pose = camera.update(frame, vanilla, mon, foe) }
        return assertNotNull(pose)
    }

    @Test
    fun theMenuViewFramesTheMonBeingCommanded() {
        val first = menuViewPose("p1a")
        val second = menuViewPose("p1b")
        assertNotEquals(first.x, second.x)
    }

    @Test
    fun theMenuViewOfTheSideCentreIsWhatSinglesHas() {
        val unnamed = menuViewPose(null)
        val named = menuViewPose("p1a")
        assertEquals(unnamed.x, named.x, 1e-9)
        assertEquals(unnamed.z, named.z, 1e-9)
        val unknown = menuViewPose("p1c")
        assertEquals(unnamed.x, unknown.x, 1e-9)
    }

    @Test
    fun aBiggerCommandedMonPushesTheMenuViewFurtherOut() {
        val small = menuViewPose("p1b", 1.0)
        val big = menuViewPose("p1b", 4.0)
        assertNotEquals(small.x, big.x)
    }

    @Test
    fun aFaintBeatAboutTheSecondMonIsFramedOnIt() {
        val named = commandCamera(battle())
        val plain = commandCamera(battle())
        named.playBeat(beat(subjectIsMon = true, slot = "p1b"))
        plain.playBeat(beat(subjectIsMon = true, slot = null))
        var a: Pose? = null
        var b: Pose? = null
        repeat(60) {
            a = named.update(frame, vanilla, mon, foe)
            b = plain.update(frame, vanilla, mon, foe)
        }
        assertNotEquals(assertNotNull(b).x, assertNotNull(a).x)
    }

    private fun pair(gap: Double, size: Double = 1.0) = BattleSnapshot(
        listOf(BattleSnapshot.Member("p1a", mon, 1.0), BattleSnapshot.Member("p1b", Point(mon.x + gap, 1.0, 0.0), size)),
        listOf(BattleSnapshot.Member("p2a", foe, 1.0)),
    )

    private fun distanceTo(pose: Pose, point: Point) = (Point(pose.x, pose.y, pose.z) - point).length()

    @Test
    fun aShotAboutOneBigMonOfAPairIsFramedOnItAndFittedToItsSize() {
        val about = plan(attackerIsMon = true, attackerSlot = "p1b", targetSlot = "p2a")
        val small = poseAfterPlaying(commandCamera(pair(3.0), space = true), about, 1.0)
        val big = poseAfterPlaying(commandCamera(pair(3.0, 8.8), space = true), about, 1.0)
        assertEquals(true, distanceTo(big, Point(3.0, 1.0, 0.0)) > distanceTo(small, Point(3.0, 1.0, 0.0)) + 1.0, "room for the big one")
        assertEquals(true, small.x > 0.0, "framed from the side of the Mon it is about")
    }

    @Test
    fun aSlotNotInTheWorldIsFittedToTheSideAsAWholeNotToOnePokemon() {
        val unknown = plan(attackerIsMon = true, attackerSlot = "p1c", targetSlot = "p2c")
        val together = poseAfterPlaying(commandCamera(pair(0.2), space = true), unknown, 1.0)
        val apart = poseAfterPlaying(commandCamera(pair(6.0), space = true), unknown, 1.0)
        assertEquals(true, distanceTo(apart, mon) > distanceTo(together, mon) + 1.0, "a wider side needs more room")
    }
}
