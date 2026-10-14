package com.cbpr.battle

import com.cbpr.audio.SoundSlot
import com.cbpr.camera.BattleCamera
import com.cbpr.camera.Point
import com.cbpr.camera.Pose
import com.cbpr.config.CbprConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShotDirectorDynamaxTest {
    private val heard = mutableListOf<SoundSlot>()
    private val camera = BattleCamera()
    private val sizes = mapOf(true to 1.0, false to 6.0)
    private val sizeAskedFor = mutableListOf<String?>()
    private var config = CbprConfig()
    private val director = ShotDirector(
        camera = camera,
        profiles = { ProfileSet(listOf(MoveProfile.GENERIC)) },
        settings = { config },
        lookupMove = { null },
        mons = { MessageSubject.Roster("Reckshin", listOf(MessageSubject.Named("p1b", "Charizard"))) },
        foes = { MessageSubject.Roster("Wild", listOf(MessageSubject.Named("p2a", "Absol"))) },
        presentation = object : ShotPresentation {
            override fun sound(slot: SoundSlot, monSide: Boolean?, pokemon: String?) {
                heard += slot
            }
        },
        sizeOf = { side, slot ->
            sizeAskedFor += slot
            sizes.getValue(side)
        },
    )
    private val vanilla = Pose(10.0, 65.0, 10.0, 170.0, 15.0)
    private val mon = Point(0.0, 1.0, 0.0)
    private val foe = Point(0.0, 1.0, 8.0)
    private val frame = 1.0 / 60

    private fun commandPhase() {
        camera.begin(vanilla)
        repeat(240) { camera.update(frame, vanilla, mon, foe) }
    }

    @Test
    fun aDynamaxStartsTheLongShotWithTheHighlightSound() {
        commandPhase()
        director.handle(listOf(BattleEvent.Dynamax("Reckshin's Charizard")), 0.0)
        assertEquals(listOf(SoundSlot.SPECIAL_HIGHLIGHT), heard)
        assertTrue(camera.busy)
        var highest = 0.0
        repeat(90) { camera.update(frame, vanilla, mon, foe)?.let { highest = maxOf(highest, it.y) } }
        assertTrue(highest > 8.0, "highest $highest")
    }

    @Test
    fun theGiantIsLookedUpBySlotInDoubles() {
        commandPhase()
        director.handle(listOf(BattleEvent.Dynamax("Reckshin's Charizard")), 0.0)

        assertEquals(listOf<String?>("p1b", null), sizeAskedFor)
    }

    @Test
    fun theShotIsOverWithinAFixedTime() {
        commandPhase()
        director.handle(listOf(BattleEvent.Dynamax("Wild Charizard")), 0.0)
        repeat(60 * 8) { camera.update(frame, vanilla, mon, foe) }
        assertTrue(!camera.busy)
    }

    @Test
    fun theSwitchTurnsOffTheShotButNotTheSound() {
        config = CbprConfig(specialShot = false)
        commandPhase()
        director.handle(listOf(BattleEvent.Dynamax("Reckshin's Charizard")), 0.0)
        assertEquals(listOf(SoundSlot.SPECIAL_HIGHLIGHT), heard)
        assertTrue(!camera.busy)
    }

    @Test
    fun theAttackThatFollowsCutsTheShotShort() {
        commandPhase()
        director.handle(listOf(BattleEvent.Dynamax("Reckshin's Charizard")), 0.0)
        director.handle(listOf(BattleEvent.MoveUsed("Reckshin's Charizard", "tackle")), 0.0)
        assertTrue(!camera.busy)
    }
}
