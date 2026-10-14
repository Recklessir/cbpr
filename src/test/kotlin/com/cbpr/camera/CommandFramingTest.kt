package com.cbpr.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CommandFramingTest {
    private val vanilla = Pose(10.0, 65.0, 10.0, 170.0, 15.0)
    private val frame = 1.0 / 60
    private val monA = Point(-2.0, 1.0, 0.0)
    private val monB = Point(2.0, 1.0, 0.0)
    private val foeA = Point(-3.0, 1.0, 9.0)
    private val foeB = Point(3.0, 1.0, 9.0)
    private val sidesCentre = Pair(Point(0.0, 1.0, 0.0), Point(0.0, 1.0, 9.0))

    private fun snapshot(spread: Double = 4.0, owner: String? = null, targetStep: Boolean = false) = BattleSnapshot(
        listOf(BattleSnapshot.Member("p1a", monA, 1.0), BattleSnapshot.Member("p1b", Point(monA.x + spread, 1.0, 0.0), 1.0)),
        listOf(BattleSnapshot.Member("p2a", foeA, 1.0), BattleSnapshot.Member("p2b", Point(foeA.x + spread, 1.0, 9.0), 1.0)),
        owner, targetStep,
    )

    private fun camera(spread: Double = 4.0, space: Boolean = true): BattleCamera {
        val camera = BattleCamera()
        if (space) camera.useSpace(null, true)
        camera.useBattle(snapshot(spread))
        camera.begin(vanilla)
        repeat(300) { camera.update(frame, vanilla, sidesCentre.first, sidesCentre.second) }
        return camera
    }

    private fun settle(camera: BattleCamera, seconds: Double = 5.0): Pose {
        var pose: Pose? = null
        repeat((seconds / frame).toInt()) { pose = camera.update(frame, vanilla, sidesCentre.first, sidesCentre.second) }
        return assertNotNull(pose)
    }

    private fun distanceBetween(a: Pose, b: Point) = Point(a.x - b.x, a.y - b.y, a.z - b.z).length()

    @Test
    fun theCommandShotIsOnTheMonTheMenuIsFor() {
        val first = camera().also { it.useBattle(snapshot(owner = "p1a")) }
        val second = camera().also { it.useBattle(snapshot(owner = "p1b")) }
        val a = settle(first)
        val b = settle(second)
        assertNotEquals(a.x, b.x)
        assertEquals(true, a.x < b.x, "the camera sits further left for the left Mon")
    }

    @Test
    fun theCameraMovesToTheNextMonWhenItsMenuOpens() {
        val camera = camera()
        camera.useBattle(snapshot(owner = "p1a"))
        val first = settle(camera)
        camera.useBattle(snapshot(owner = "p1b"))
        val second = settle(camera)
        assertNotEquals(first.x, second.x)

        val mid = camera().also { it.useBattle(snapshot(owner = "p1a")); settle(it); it.useBattle(snapshot(owner = "p1b")) }
        val soon = settle(mid, 0.1)
        assertEquals(true, soon.x > first.x && soon.x < second.x, "x ${soon.x} between ${first.x} and ${second.x}")
    }

    @Test
    fun theOtherPokemonDoNotPullTheCameraBackWhileAMenuIsOpen() {
        val tight = camera(spread = 4.0)
        val spread = camera(spread = 8.0)
        tight.useBattle(snapshot(4.0, "p1a"))
        spread.useBattle(snapshot(8.0, "p1a"))
        val a = settle(tight)
        val b = settle(spread)
        assertEquals(a.x, b.x, 1e-3)
        assertEquals(a.y, b.y, 1e-3)
        assertEquals(a.z, b.z, 1e-3)
    }

    @Test
    fun theWholeFieldStillPullsTheCameraBackWhenNoMenuIsOpen() {
        val tight = camera(spread = 0.2)
        val spread = camera(spread = 8.0)
        val a = settle(tight)
        val b = settle(spread)
        assertEquals(true, distanceBetween(b, sidesCentre.first) > distanceBetween(a, sidesCentre.first) + 0.5, "a ${a.z} b ${b.z}")
    }

    @Test
    fun theTargetStepGoesUpToAViewOfTheWholeField() {
        val resting = camera()
        resting.useBattle(snapshot(owner = "p1a"))
        val rest = settle(resting)
        val picking = camera()
        picking.menuView = true
        picking.useBattle(snapshot(owner = "p1a", targetStep = true))
        val overview = settle(picking)
        assertEquals(true, overview.y > rest.y + 3.0, "overview y ${overview.y} against ${rest.y}")
        assertEquals(true, overview.pitch > 30.0, "looking down, pitch ${overview.pitch}")

        assertEquals(true, overview.z < sidesCentre.first.z)
    }

    @Test
    fun noShotEverSitsFurtherFromThePokemonThanTheLimit() {
        val mon = Point(0.0, 1.0, 0.0)
        val foe = Point(0.0, 1.0, 60.0)
        val camera = BattleCamera()
        camera.begin(vanilla)
        var farthest = 0.0
        repeat(600) { frameIndex ->
            val pose = camera.update(frame, vanilla, mon, foe) ?: return@repeat

            if (frameIndex < 240) return@repeat
            val eye = Point(pose.x, pose.y, pose.z)
            farthest = maxOf(farthest, minOf((eye - mon).length(), (eye - foe).length()))
        }
        assertEquals(true, farthest <= BattleCamera.MAX_ZOOM_OUT + 1e-6, "farthest $farthest")
    }

    @Test
    fun theOverviewIsNotScaledAgainByTheGroupSize() {
        fun overview(space: Boolean) = camera(spread = 8.0, space = space).also {
            it.menuView = true
            it.useBattle(snapshot(8.0, targetStep = true))
        }.let(::settle)
        val fitted = overview(true)
        val plain = overview(false)
        assertEquals(plain.x, fitted.x, 1e-3)
        assertEquals(plain.y, fitted.y, 1e-3)
        assertEquals(plain.z, fitted.z, 1e-3)
    }

    @Test
    fun theTargetStepViewStaysPutWhereverTheCursorIs() {
        fun overviewWith(cursorX: Double, cursorY: Double): Pose {
            val camera = camera()
            camera.menuView = true
            camera.parallax = true
            camera.useBattle(snapshot(targetStep = true))
            camera.cursorX = cursorX
            camera.cursorY = cursorY
            return settle(camera)
        }
        val a = overviewWith(1.0, 1.0)
        val b = overviewWith(-1.0, -1.0)
        assertEquals(a.yaw, b.yaw, 1e-6)
        assertEquals(a.pitch, b.pitch, 1e-6)
    }

    @Test
    fun theCursorDriftStillWorksWhileChoosingAMove() {
        fun commandWith(cursorX: Double): Pose {
            val camera = camera()
            camera.parallax = true
            camera.cursorX = cursorX
            return settle(camera)
        }
        assertNotEquals(commandWith(1.0).yaw, commandWith(-1.0).yaw)
    }

    @Test
    fun leavingTheTargetStepGoesBackToTheMenuView() {
        val camera = camera()
        camera.menuView = true
        camera.useBattle(snapshot(owner = "p1a"))
        val before = settle(camera)
        camera.useBattle(snapshot(owner = "p1a", targetStep = true))
        settle(camera)
        camera.useBattle(snapshot(owner = "p1a"))
        val after = settle(camera)
        assertEquals(before.x, after.x, 1e-3)
        assertEquals(before.y, after.y, 1e-3)
    }

    @Test
    fun theOverviewTakesInMoreRoomTheFurtherApartThePokemonAre() {
        val tight = Shots.overview(Point(0.0, 1.0, 0.0), Point(0.0, 1.0, 6.0), 1.0)
        val wide = Shots.overview(Point(0.0, 1.0, 0.0), Point(0.0, 1.0, 14.0), 6.0)
        assertEquals(true, wide.y > tight.y)
        assertEquals(true, wide.z < tight.z)
    }

    private fun box(slot: String, x: Double, z: Double) = TargetPicker.Candidate(slot, Point(x - 0.5, 0.0, z - 0.5), Point(x + 0.5, 2.0, z + 0.5))
    private val origin = Point(0.0, 1.0, 0.0)

    @Test
    fun aRayThroughABoxPicksIt() {
        assertEquals("a", TargetPicker.pick(origin, Point(0.0, 0.0, 1.0), listOf(box("a", 0.0, 8.0), box("b", 4.0, 8.0))))
        assertEquals("b", TargetPicker.pick(origin, Point(0.5, 0.0, 1.0), listOf(box("a", 0.0, 8.0), box("b", 4.0, 8.0))))
    }

    @Test
    fun aRayThatMissesEveryBoxPicksNothing() {
        assertNull(TargetPicker.pick(origin, Point(-1.0, 0.0, 1.0), listOf(box("a", 0.0, 8.0))))
    }

    @Test
    fun theNearestBoxOnTheRayWins() {
        val boxes = listOf(box("far", 0.0, 12.0), box("near", 0.0, 5.0))
        assertEquals("near", TargetPicker.pick(origin, Point(0.0, 0.0, 1.0), boxes))
    }

    @Test
    fun aBoxBehindTheOriginIsNotPicked() {
        assertNull(TargetPicker.pick(origin, Point(0.0, 0.0, 1.0), listOf(box("behind", 0.0, -5.0))))
    }

    @Test
    fun aRayThatStartsInsideABoxPicksIt() {
        assertEquals("in", TargetPicker.pick(Point(0.0, 1.0, 8.0), Point(0.0, 0.0, 1.0), listOf(box("in", 0.0, 8.0))))
    }

    @Test
    fun theOnePickedStaysPickedWhileTheRayIsStillNearIt() {
        val box = listOf(box("a", 0.0, 8.0))
        val off = Point(0.07, 0.0, 1.0)
        assertNull(TargetPicker.pick(origin, off, box))
        assertEquals("a", TargetPicker.pick(origin, off, box, keep = "a", keepGrow = 0.5))
    }

    @Test
    fun theKeptOneLetsGoOnceTheRayIsWellAwayFromIt() {
        val box = listOf(box("a", 0.0, 8.0))
        assertNull(TargetPicker.pick(origin, Point(1.0, 0.0, 1.0), box, keep = "a", keepGrow = 0.5))
    }

    @Test
    fun aRayThroughAnotherBoxBeatsTheKeptOne() {
        val boxes = listOf(box("a", 0.0, 8.0), box("b", 4.0, 8.0))
        assertEquals("b", TargetPicker.pick(origin, Point(0.5, 0.0, 1.0), boxes, keep = "a", keepGrow = 5.0))
    }

    @Test
    fun aKeptOneThatIsNoLongerACandidateIsDropped() {
        assertNull(TargetPicker.pick(origin, Point(0.0, 0.0, 1.0), listOf(box("b", 20.0, 8.0)), keep = "a", keepGrow = 0.5))
    }

    @Test
    fun noCandidatesPicksNothing() {
        assertNull(TargetPicker.pick(origin, Point(0.0, 0.0, 1.0), emptyList()))
    }
}
