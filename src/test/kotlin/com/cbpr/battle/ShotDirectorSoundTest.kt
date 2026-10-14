package com.cbpr.battle

import com.cbpr.audio.SoundSlot
import com.cbpr.camera.BattleCamera
import com.cbpr.camera.GimmickStyle
import com.cbpr.config.CbprConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShotDirectorSoundTest {
    private val heard = mutableListOf<Pair<SoundSlot, Boolean?>>()
    private val heardAt = mutableListOf<String?>()
    private val camera = BattleCamera()
    private val sounding = MoveProfile("sounding", setOf("zap"), emptySet(), 0.2, 0.4, 1.0, 0.5, 0.7, 0.9, 30.0, 5.0, 0.6, 0.0, 0.05, true, "crit")
    private val director = ShotDirector(
        camera = camera,
        profiles = { ProfileSet(listOf(MoveProfile.GENERIC, sounding)) },
        settings = { CbprConfig() },
        lookupMove = { id -> if (id == "zap") MoveInfo(id, "special", "electric", "normal") else null },
        mons = { MessageSubject.Roster("Reckshin", listOf(MessageSubject.Named("p1a", "Charmander"))) },
        foes = { MessageSubject.Roster("Wild", listOf(MessageSubject.Named("p2a", "Absol"))) },
        presentation = object : ShotPresentation {
            override fun sound(slot: SoundSlot, monSide: Boolean?, pokemon: String?) {
                heard += slot to monSide
                heardAt += pokemon
            }
        },
    )

    private fun expect(vararg sounds: Pair<SoundSlot, Boolean?>) = assertEquals(sounds.toList(), heard)

    @Test
    fun aHitMakesNoSoundOfItsOwn() {
        director.handle(listOf(BattleEvent.MoveUsed("Reckshin's Charmander", "tackle"), BattleEvent.Damage(0.1), BattleEvent.Damage(0.4)), 0.0)
        expect()
    }

    @Test
    fun aMoveProfileCanNameItsOwnImpactSound() {
        director.handle(listOf(BattleEvent.MoveUsed("Wild Pikachu", "zap"), BattleEvent.Damage(0.05)), 0.0)
        expect(SoundSlot.CRIT to true)
    }

    @Test
    fun noHealthLostMakesNoHitSound() {
        director.handle(listOf(BattleEvent.MoveUsed("Reckshin's Charmander", "tackle"), BattleEvent.Damage(0.0)), 0.0)
        expect()
    }

    @Test
    fun modifiersFaintsAndOutcomesEachHaveTheirSoundAndSendOutsHaveNone() {
        director.handle(
            listOf(
                BattleEvent.MoveUsed("Reckshin's Charmander", "tackle"), BattleEvent.Crit(), BattleEvent.SuperEffective(), BattleEvent.NoEffect(),
                BattleEvent.Fainted(false), BattleEvent.SentOut(true), BattleEvent.Outcome("Reckshin"), BattleEvent.Outcome("Wild Absol"),
            ),
            0.0,
        )
        expect(
                SoundSlot.CRIT to false, SoundSlot.SUPER_EFFECTIVE to false, SoundSlot.NOT_EFFECTIVE to false,
                SoundSlot.FAINT to false,
                SoundSlot.VICTORY to null, SoundSlot.DEFEAT to null,
        )
    }

    @Test
    fun aMoveSoundIsHeardAtThePokemonTheMoveWasUsedOn() {
        director.handle(listOf(BattleEvent.MoveUsed("Reckshin's Charmander", "zap", target = "Wild Absol"), BattleEvent.Damage(0.1)), 0.0)
        assertEquals(listOf<String?>("p2a"), heardAt)
    }

    @Test
    fun aMoveSoundIsHeardAtTheSlotTheDamageNames() {
        director.handle(listOf(BattleEvent.MoveUsed("Reckshin's Charmander", "zap", target = "Wild Absol"), BattleEvent.Damage(0.1, false, "p2b")), 0.0)
        assertEquals(listOf<String?>("p2b"), heardAt)
    }

    @Test
    fun resetForgetsTheMoveAndItsSound() {
        director.handle(listOf(BattleEvent.MoveUsed("Wild Pikachu", "zap")), 0.0)
        director.reset()
        director.handle(listOf(BattleEvent.Damage(0.5)), 0.0)
        expect()
    }
}

class ShotDirectorGimmickTest {
    private val heard = mutableListOf<Pair<SoundSlot, Boolean?>>()
    private val camera = BattleCamera()
    private var config = CbprConfig()
    private val director = ShotDirector(
        camera = camera,
        profiles = { ProfileSet(listOf(MoveProfile.GENERIC)) },
        settings = { config },
        lookupMove = { null },
        mons = { MessageSubject.Roster("Reckshin", listOf(MessageSubject.Named("p1a", "Charizard"))) },
        foes = { MessageSubject.Roster("Wild", listOf(MessageSubject.Named("p2a", "Absol"))) },
        presentation = object : ShotPresentation {
            override fun sound(slot: SoundSlot, monSide: Boolean?, pokemon: String?) {
                heard += slot to monSide
            }
        },
    )
    private val vanilla = com.cbpr.camera.Pose(10.0, 65.0, 10.0, 170.0, 15.0)
    private val mon = com.cbpr.camera.Point(0.0, 1.0, 0.0)
    private val foe = com.cbpr.camera.Point(0.0, 1.0, 8.0)
    private val frame = 1.0 / 60

    private fun commandPhase() {
        camera.begin(vanilla)
        repeat(240) { camera.update(frame, vanilla, mon, foe) }
    }

    @Test
    fun aGimmickPlaysTheHighlightSoundAndStartsTheCloseUp() {
        commandPhase()
        director.handle(listOf(BattleEvent.Gimmick("Reckshin's Charizard", GimmickStyle.MEGA)), 0.0)
        assertEquals(listOf<Pair<SoundSlot, Boolean?>>(SoundSlot.SPECIAL_HIGHLIGHT to true), heard)
        assertTrue(camera.busy)
        assertTrue(camera.wantsBars)
    }

    @Test
    fun theCloseUpCanBeSwitchedOffAndTheSoundStays() {
        config = CbprConfig(specialShot = false)
        commandPhase()
        director.handle(listOf(BattleEvent.Gimmick("Wild Absol", GimmickStyle.MEGA)), 0.0)
        assertEquals(listOf<Pair<SoundSlot, Boolean?>>(SoundSlot.SPECIAL_HIGHLIGHT to false), heard)
        assertTrue(!camera.busy)
    }

    @Test
    fun aGimmickWhoseSideCannotBeToldKeepsTheSoundButTakesNoCloseUp() {
        commandPhase()
        director.handle(listOf(BattleEvent.Gimmick("Someone's Pikachu", GimmickStyle.MEGA)), 0.0)
        assertEquals(listOf<Pair<SoundSlot, Boolean?>>(SoundSlot.SPECIAL_HIGHLIGHT to null), heard)
        assertTrue(!camera.busy)
    }

    @Test
    fun aFormChangeMakesOnlyASound() {
        commandPhase()
        director.handle(listOf(BattleEvent.Transformation("Reckshin's Minior")), 0.0)
        assertEquals(listOf<Pair<SoundSlot, Boolean?>>(SoundSlot.TRANSFORMATION to true), heard)
        assertTrue(!camera.busy)
    }

    @Test
    fun theAttackThatFollowsACloseUpCutsItShort() {
        commandPhase()
        director.handle(listOf(BattleEvent.Gimmick("Reckshin's Charizard", GimmickStyle.MEGA)), 0.0)
        assertTrue(camera.busy)
        director.handle(listOf(BattleEvent.MoveUsed("Reckshin's Charizard", "tackle")), 0.0)
        assertTrue(!camera.busy, "the attack replaces the close-up")
        assertTrue(camera.wantsBars)
    }

    @Test
    fun aFaintStillBlocksAnAttackShot() {
        commandPhase()
        director.handle(listOf(BattleEvent.Fainted(false)), 0.0)
        director.handle(listOf(BattleEvent.MoveUsed("Reckshin's Charizard", "tackle")), 0.0)
        assertTrue(camera.busy, "a faint is not interrupted")
    }
}
