package com.cbpr.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BeatShotGimmickTest {
    private val mon = Point(0.0, 1.0, 0.0)
    private val foe = Point(0.0, 1.0, 8.0)
    private val frame = 1.0 / 60

    private fun gimmick(style: GimmickStyle, reduced: Boolean = false) =
        BeatShot(BeatPlan(BeatPlan.Kind.GIMMICK, true, reduced, style = style))

    private fun runTo(shot: BeatShot, progress: Double, seconds: Double) {
        repeat((seconds * progress / frame).toInt()) { shot.step(frame) }
    }

    @Test
    fun theFlashPeaksAtTheMomentOfTheChangeAndIsOffBeforeAndAfter() {
        for (style in GimmickStyle.entries) {
            val shot = gimmick(style)
            assertEquals(0.0, shot.flash, 1e-9, "$style starts without a flash")
            runTo(shot, style.changeAt, style.seconds)
            assertTrue(shot.flash > 0.8, "$style flash at the change ${shot.flash}")
            runTo(shot, 1.0 - style.changeAt - 0.05, style.seconds)
            assertEquals(0.0, shot.flash, 1e-9, "$style flash fades again")
        }
    }

    @Test
    fun eachGimmickFlashesInItsOwnColour() {
        assertEquals(GimmickStyle.entries.size, GimmickStyle.entries.map { it.flashRgb }.toSet().size)
    }

    @Test
    fun reducedMotionHasNoFlashTiltOrRumble() {
        val shot = gimmick(GimmickStyle.TERA, reduced = true)
        repeat(200) {
            shot.step(frame)
            assertEquals(0.0, shot.flash)
            assertEquals(0.0, shot.roll)
            assertEquals(0.0, shot.rumble)
        }
    }

    @Test
    fun theViewKicksOnceAtTheStartAndOnceAtTheChange() {
        val shot = gimmick(GimmickStyle.MEGA)
        val kicks = mutableListOf<Double>()
        repeat(60 * 4) {
            shot.step(frame)
            val kick = shot.takeKick()
            if (kick > 0.0) kicks += kick
        }
        assertEquals(2, kicks.size, "kicks $kicks")
        assertTrue(kicks.all { it in 5.0..20.0 })
    }

    @Test
    fun otherMomentsNeverKickTheView() {
        val faint = BeatShot(BeatPlan(BeatPlan.Kind.FAINT, true, false))
        repeat(120) {
            faint.step(frame)
            assertEquals(0.0, faint.takeKick())
        }
    }

    @Test
    fun theTiltStaysWithinAFewDegreesAndOnlyTeraSwingsBothWays() {
        val tera = gimmick(GimmickStyle.TERA)
        var low = 0.0
        var high = 0.0
        repeat((GimmickStyle.TERA.seconds / frame).toInt()) {
            tera.step(frame)
            low = minOf(low, tera.roll)
            high = maxOf(high, tera.roll)
        }
        assertTrue(low < -3.0 && high > 3.0 && low > -8.0 && high < 8.0, "tera tilt $low to $high")
        val mega = gimmick(GimmickStyle.MEGA)
        repeat(100) {
            mega.step(frame)
            assertEquals(0.0, mega.roll)
        }
    }

    @Test
    fun theGiantsArrivalRumblesAndFlashes() {
        val dynamax = BeatShot(BeatPlan(BeatPlan.Kind.DYNAMAX, true, false, subjectSize = 1.0, otherSize = 2.0))
        repeat((7.0 * Shots.DYNAMAX_ARRIVE_AT / frame).toInt()) { dynamax.step(frame) }
        assertTrue(dynamax.flash > 0.8, "flash ${dynamax.flash}")
        repeat((7.0 * 0.03 / frame).toInt()) { dynamax.step(frame) }
        assertTrue(dynamax.rumble > 0.0, "rumble")
        assertTrue(dynamax.flashRgb != 0xFFFFFF)
    }

    @Test
    fun theDynamaxShotBacksOffAsTheGiantActuallyGrows() {
        fun farthest(liveSize: Double): Double {
            val shot = BeatShot(BeatPlan(BeatPlan.Kind.DYNAMAX, true, false, subjectSize = 1.0, otherSize = 1.0))
            repeat((7.0 * 0.8 / frame).toInt()) { shot.step(frame) }
            shot.liveSizes(liveSize, 1.0)
            val pose = shot.pose(mon, foe)!!
            return (Point(pose.x, pose.y, pose.z) - mon).length()
        }
        assertTrue(farthest(4.0) > farthest(1.0) + 2.0, "the camera should follow the live size")
    }

    @Test
    fun everyGimmickShotEndsOnItsOwnAndLetsTheBarsGo() {
        for (style in GimmickStyle.entries) {
            val shot = gimmick(style)
            var seconds = 0.0
            while (!shot.finished && seconds < 10.0) {
                shot.step(frame)
                seconds += frame
            }
            assertTrue(shot.finished, "$style never finishes")
            assertTrue(!shot.wantsBars)
        }
    }
}
