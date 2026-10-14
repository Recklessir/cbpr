package com.cbpr.battle

import com.cbpr.camera.BattleCamera
import com.cbpr.camera.BeatPlan
import com.cbpr.camera.Point
import com.cbpr.camera.Pose
import com.cbpr.config.CbprConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShotDirectorTest {
    private val vanilla = Pose(10.0, 65.0, 10.0, 170.0, 15.0)
    private val mon = Point(0.0, 1.0, 0.0)
    private val foe = Point(0.0, 1.0, 8.0)
    private val frame = 1.0 / 60

    private val rush = MoveProfile("rush", emptySet(), emptySet(), 0.2, 0.4, 1.0, 0.5, 0.7, 0.9, 30.0, 5.0, 0.6, 0.0, 0.05, true, null)
    private val quiet = MoveProfile("quiet", setOf("shy"), emptySet(), 0.2, 0.4, 1.0, 0.5, 0.7, 0.9, 30.0, 5.0, 0.6, 0.0, 0.05, false, null)
    private val profiles = ProfileSet(listOf(MoveProfile.GENERIC, rush, quiet))

    private var config = CbprConfig()
    private val callouts = mutableListOf<String>()
    private val results = mutableListOf<Pair<Boolean, String>>()
    private val stamps = mutableListOf<Pair<StampKind, String>>()
    private val numbers = mutableListOf<Pair<Double, Boolean?>>()
    private val cues = mutableListOf<Triple<String, Int, Boolean?>>()
    private val fields = mutableListOf<Pair<String, Boolean>>()
    private val volatiles = mutableListOf<Pair<String, Boolean?>>()

    private val slotsSeen = mutableListOf<Pair<String, String?>>()
    private val camera = BattleCamera()

    private fun roster(owner: String, vararg members: Pair<String, String>) =
        MessageSubject.Roster(owner, members.map { MessageSubject.Named(it.first, it.second) })

    private var monRoster = roster("Reckshin", "p1a" to "Charmander", "p1b" to "Bulbasaur")
    private var foeRoster = roster("Wild", "p2a" to "Rattata", "p2b" to "Yungoos")

    private val director = ShotDirector(
        camera = camera,
        profiles = { profiles },
        settings = { config },
        lookupMove = { id -> if (id == "tackle") MoveInfo(id, "physical", "normal", "normal") else null },
        mons = { monRoster },
        foes = { foeRoster },
        presentation = object : ShotPresentation {
            override fun callout(move: ShotDirector.MoveUsed) {
                callouts += move.moveId
            }

            override fun result(won: Boolean, winner: String) {
                results += won to winner
            }

            override fun stamp(kind: StampKind, message: MessageText) {
                stamps += kind to message.key
            }

            override fun damageNumber(lost: Double, monSide: Boolean?, pokemon: String?) {
                numbers += lost to monSide
                slotsSeen += "damage" to pokemon
            }

            override fun cue(label: String, rgb: Int, monSide: Boolean?, pokemon: String?) {
                cues += Triple(label, rgb, monSide)
                slotsSeen += "cue" to pokemon
            }

            override fun field(id: String, started: Boolean) {
                fields += id to started
            }

            override fun volatile(id: String, monSide: Boolean?, pokemon: String?) {
                volatiles += id to monSide
                slotsSeen += "volatile" to pokemon
            }
        },
    )

    @Test
    fun damageNumbersCueAndVolatileWordsKnowWhichPokemonTheyAreAbout() {
        director.handle(
            listOf(
                BattleEvent.Damage(0.2, true, "p1b"),
                BattleEvent.StatChange("Reckshin's Bulbasaur", "Attack", 1),
                BattleEvent.Volatile("Reckshin's Charmander", "confusion"),
            ),
            0.0,
        )
        assertEquals(listOf<Pair<String, String?>>("damage" to "p1b", "cue" to "p1b", "volatile" to "p1a"), slotsSeen)
    }

    private fun cueFor(text: String) = director.handle(listOf(BattleEvent.StatChange(text, "Attack", 1)), 0.0)

    @Test
    fun aTrainerNamedRatIsNotFoundInAWildRattata() {
        monRoster = roster("Rat", "p1a" to "Charmander")
        foeRoster = roster("Wild", "p2a" to "Rattata")
        cueFor("Wild Rattata")
        assertEquals(listOf<Boolean?>(false), cues.map { it.third })
        cueFor("Rat's Charmander")
        assertEquals(listOf<Boolean?>(false, true), cues.map { it.third })
    }

    @Test
    fun aMirrorMatchShowsNothingForAnAmbiguousMessage() {
        monRoster = roster("Reckshin", "p1a" to "Charmander")
        foeRoster = roster("Bob", "p2a" to "Charmander")
        cueFor("Charmander")
        director.handle(listOf(BattleEvent.MoveUsed("Charmander", "rush"), BattleEvent.Volatile("Charmander", "taunt")), 0.0)
        assertTrue(cues.isEmpty() && callouts.isEmpty() && volatiles.isEmpty())
        assertNull(camera.beatKind)
        assertFalse(camera.busy)
    }

    @Test
    fun aPokemonNicknamedLikeATrainerShowsNothing() {
        monRoster = roster("Reckshin", "p1a" to "Charmander")
        foeRoster = roster("Wild", "p2a" to "Reckshin")
        cueFor("Wild Reckshin")
        assertTrue(cues.isEmpty())
    }

    @Test
    fun aMessageNamingNeitherSideShowsNothing() {
        cueFor("Someone's Pikachu")
        assertTrue(cues.isEmpty())
    }

    @Test
    fun anAmbiguousWinnerShowsNoResult() {
        monRoster = roster("Rattata", "p1a" to "Charmander")
        foeRoster = roster("Wild", "p2a" to "Rattata")
        commandPhase()
        director.handle(listOf(BattleEvent.Outcome("Wild Rattata")), frame)
        assertTrue(results.isEmpty())
        assertFalse(camera.busy)
    }

    @Test
    fun aPokemonTheTextCannotPlaceIsShownForTheSideOnly() {
        director.handle(listOf(BattleEvent.StatChange("Wild Absol", "Speed", -1)), 0.0)
        assertEquals(listOf<Pair<String, String?>>("cue" to null), slotsSeen)
    }

    private fun commandPhase() {
        camera.begin(vanilla)
        repeat(240) { camera.update(frame, vanilla, mon, foe) }
        assertEquals(BattleCamera.Phase.COMMAND, camera.phase)
    }

    private fun run(seconds: Double): Pose? {
        var pose: Pose? = null
        repeat((seconds / frame).toInt()) { pose = camera.update(frame, vanilla, mon, foe) }
        return pose
    }

    @Test
    fun aMoveStartsAnAttackShotAndACallout() {
        commandPhase()
        director.handle(listOf(BattleEvent.MoveUsed("Reckshin's Charmander", "tackle")), frame)
        assertTrue(camera.wantsBars)
        assertEquals(listOf("tackle"), callouts)
    }

    @Test
    fun aCritSuperEffectiveAndResistedHitEachGetAStampWithTheirMessage() {
        commandPhase()
        director.handle(
            listOf(
                BattleEvent.Crit(MessageText("cobblemon.battle.crit")),
                BattleEvent.SuperEffective(MessageText("cobblemon.battle.superEffective")),
                BattleEvent.NoEffect(MessageText("cobblemon.battle.resisted")),
            ),
            frame,
        )
        assertEquals(
            listOf(StampKind.CRIT to "cobblemon.battle.crit", StampKind.SUPER_EFFECTIVE to "cobblemon.battle.superEffective", StampKind.NO_EFFECT to "cobblemon.battle.resisted"),
            stamps,
        )
    }

    @Test
    fun noStampWhenTheSwitchIsOffOrTheMessageIsMissing() {
        commandPhase()
        director.handle(listOf(BattleEvent.Crit()), frame)
        config = config.copy(impactStamps = false)
        director.handle(listOf(BattleEvent.SuperEffective(MessageText("cobblemon.battle.superEffective"))), frame)
        assertTrue(stamps.isEmpty())
    }

    @Test
    fun damageAndHealingBothGetANumberButOnlyDamageKicksTheView() {
        commandPhase()
        director.handle(listOf(BattleEvent.Damage(0.25, onMonSide = false), BattleEvent.Damage(-0.05, onMonSide = true), BattleEvent.Damage(0.0, onMonSide = true)), frame)
        assertEquals(listOf<Pair<Double, Boolean?>>(0.25 to false, -0.05 to true), numbers)
        config = config.copy(damageNumbers = false)
        director.handle(listOf(BattleEvent.Damage(0.5, onMonSide = false)), frame)
        assertEquals(2, numbers.size)
    }

    @Test
    fun aStatChangeGetsACueForTheRightSideUnlessSwitchedOff() {
        commandPhase()
        director.handle(listOf(BattleEvent.StatChange("Reckshin's Charmander", "Attack", 2), BattleEvent.StatChange("Rattata", "Speed", -1)), frame)
        assertEquals(
            listOf(Triple("Attack +2", StatStages.UP_RGB, true as Boolean?), Triple("Speed -1", StatStages.DOWN_RGB, false as Boolean?)),
            cues,
        )
        config = config.copy(statusCues = false)
        director.handle(listOf(BattleEvent.StatChange("Rattata", "Defense", 1)), frame)
        assertEquals(2, cues.size)
    }

    @Test
    fun aWeatherStartingWashesStampsAndTakesAWideShotButItsEndOnlyWashesAndStamps() {
        commandPhase()
        director.handle(listOf(BattleEvent.Field("raindance", true, MessageText("cobblemon.battle.weather.raindance.start"))), frame)
        assertEquals(listOf("raindance" to true), fields)
        assertEquals(listOf(StampKind.FIELD to "cobblemon.battle.weather.raindance.start"), stamps)
        assertEquals(BeatPlan.Kind.WEATHER, camera.beatKind)
        run(3.0)
        assertNull(camera.beatKind)
        director.handle(listOf(BattleEvent.Field("raindance", false, MessageText("cobblemon.battle.weather.raindance.end"))), frame)
        assertEquals(listOf("raindance" to true, "raindance" to false), fields)
        assertNull(camera.beatKind)
    }

    @Test
    fun aWeatherDoesNotCutAFaintShotAndTheSwitchTurnsItOff() {
        commandPhase()
        director.handle(listOf(BattleEvent.Fainted(true)), frame)
        assertEquals(BeatPlan.Kind.FAINT, camera.beatKind)
        director.handle(listOf(BattleEvent.Field("hail", true, null)), frame)
        assertEquals(BeatPlan.Kind.FAINT, camera.beatKind)
        assertEquals(listOf("hail" to true), fields, "the wash still follows")
        config = config.copy(weatherWash = false)
        director.handle(listOf(BattleEvent.Field("snow", true, null)), frame)
        assertEquals(1, fields.size)
    }

    @Test
    fun aConditionGetsAWordForTheRightSideUnlessCuesAreOff() {
        commandPhase()
        director.handle(listOf(BattleEvent.Volatile("Reckshin's Charmander", "confusion"), BattleEvent.Volatile("Rattata", "taunt")), frame)
        assertEquals(listOf<Pair<String, Boolean?>>("confusion" to true, "taunt" to false), volatiles)
        config = config.copy(statusCues = false)
        director.handle(listOf(BattleEvent.Volatile("Rattata", "yawn")), frame)
        assertEquals(2, volatiles.size)
    }

    @Test
    fun aHitDuringTheShotKicksTheView() {
        commandPhase()
        director.handle(listOf(BattleEvent.MoveUsed("Reckshin's Charmander", "tackle")), frame)
        run(0.3)
        director.handle(listOf(BattleEvent.Damage(0.4)), frame)
        val pose = assertNotNull(camera.update(frame, vanilla, mon, foe))
        assertTrue(pose.fov > 1.0, "fov ${pose.fov}")
    }

    @Test
    fun aCritThatArrivesBeforeTheDamageTiltsTheView() {
        commandPhase()
        director.handle(listOf(BattleEvent.MoveUsed("Reckshin's Charmander", "tackle"), BattleEvent.Crit()), frame)
        run(0.3)
        director.handle(listOf(BattleEvent.Damage(0.4)), frame)
        val pose = assertNotNull(run(0.4))
        assertTrue(pose.roll != 0.0, "roll ${pose.roll}")
    }

    @Test
    fun aCritThatArrivesAfterTheDamageStillUpgradesTheHit() {
        commandPhase()
        director.handle(listOf(BattleEvent.MoveUsed("Reckshin's Charmander", "tackle")), frame)
        run(0.3)
        director.handle(listOf(BattleEvent.Damage(0.4)), frame)
        director.handle(listOf(BattleEvent.Crit()), 0.1)
        val pose = assertNotNull(run(0.4))
        assertTrue(pose.roll != 0.0, "roll ${pose.roll}")
    }

    @Test
    fun healingAndZeroDamageNeverTriggerAnImpact() {
        commandPhase()
        director.handle(listOf(BattleEvent.MoveUsed("Reckshin's Charmander", "tackle")), frame)
        run(0.3)
        director.handle(listOf(BattleEvent.Damage(-0.3), BattleEvent.Damage(0.0)), frame)
        val pose = assertNotNull(camera.update(frame, vanilla, mon, foe))
        assertEquals(0.0, pose.fov, 1e-9)
    }

    @Test
    fun aMissOrImmunityLeavesTheShotToTimeOut() {
        commandPhase()
        director.handle(listOf(BattleEvent.MoveUsed("Reckshin's Charmander", "tackle"), BattleEvent.NoEffect()), frame)
        run(8.0)
        assertFalse(camera.wantsBars)
    }

    @Test
    fun anUnknownModdedMoveStillGetsAShotAndACallout() {
        commandPhase()
        director.handle(listOf(BattleEvent.MoveUsed("Wild Thing", "modmove")), frame)
        assertTrue(camera.wantsBars)
        assertEquals(listOf("modmove"), callouts)
    }

    @Test
    fun aProfileCanOptOutOfItsCallout() {
        commandPhase()
        director.handle(listOf(BattleEvent.MoveUsed("Wild Thing", "shy")), frame)
        assertTrue(callouts.isEmpty())
        assertTrue(camera.wantsBars)
    }

    @Test
    fun switchingCinematicShotsOffKeepsTheCalloutButNotTheCamera() {
        config = CbprConfig(cinematicShots = false)
        commandPhase()
        director.handle(listOf(BattleEvent.MoveUsed("Reckshin's Charmander", "tackle")), frame)
        assertFalse(camera.wantsBars)
        assertEquals(listOf("tackle"), callouts)
    }

    @Test
    fun switchingCalloutsOffSuppressesThem() {
        config = CbprConfig(callouts = false)
        commandPhase()
        director.handle(listOf(BattleEvent.MoveUsed("Reckshin's Charmander", "tackle")), frame)
        assertTrue(callouts.isEmpty())
        assertTrue(camera.wantsBars)
    }

    @Test
    fun theAttackerIsTheFoeWhenTheNameIsNotTheTrainers() {
        commandPhase()
        director.handle(listOf(BattleEvent.MoveUsed("Wild Yungoos", "tackle")), frame)
        run(0.3)
        director.handle(listOf(BattleEvent.Damage(0.4), BattleEvent.Crit()), frame)
        val pose = assertNotNull(run(0.4))

        assertTrue(pose.roll < -1.0, "roll ${pose.roll}")
    }

    @Test
    fun eventsOutsideTheCommandPhaseAreHarmless() {
        director.handle(listOf(BattleEvent.MoveUsed("Reckshin's Charmander", "tackle"), BattleEvent.Damage(0.5)), frame)
        assertFalse(camera.wantsBars)
        assertEquals(BattleCamera.Phase.OFF, camera.phase)
    }

    @Test
    fun resetForgetsModifiersFromAnEarlierBattle() {
        commandPhase()
        director.handle(listOf(BattleEvent.Crit()), frame)
        director.reset()
        director.handle(listOf(BattleEvent.MoveUsed("Reckshin's Charmander", "tackle")), frame)
        run(0.3)
        director.handle(listOf(BattleEvent.Damage(0.4)), frame)
        val pose = assertNotNull(run(0.4))
        assertEquals(0.0, pose.roll, 1e-6, "an old crit must not tilt a later battle")
    }

    @Test
    fun aFaintStartsTheFaintShotAndDimsTheWorld() {
        commandPhase()
        director.handle(listOf(BattleEvent.Fainted(onMonSide = false)), frame)
        assertTrue(camera.busy)
        assertTrue(camera.wantsBars)
        assertEquals(1.0, camera.tint)
    }

    @Test
    fun aSendOutStartsTheSwoopWithoutDimming() {
        config = CbprConfig(sendOutSwoop = true)
        commandPhase()
        director.handle(listOf(BattleEvent.SentOut(onMonSide = true)), frame)
        assertTrue(camera.busy)
        assertEquals(0.0, camera.tint)
    }

    @Test
    fun aWinShowsTheResultAndStartsTheOrbit() {
        commandPhase()
        director.handle(listOf(BattleEvent.Outcome("Reckshin")), frame)
        assertEquals(listOf(true to "Reckshin"), results)
        assertTrue(camera.busy)
        assertEquals(0.0, camera.tint)
    }

    @Test
    fun aLossShowsTheResultAndOrbitsUnderADarkTint() {
        commandPhase()
        director.handle(listOf(BattleEvent.Outcome("Wild Yungoos")), frame)
        assertEquals(listOf(false to "Wild Yungoos"), results)
        assertEquals(1.0, camera.tint)
    }

    @Test
    fun eachMomentCanBeSwitchedOffOnItsOwn() {
        config = CbprConfig(faintShot = false, sendOutSwoop = true)
        commandPhase()
        director.handle(listOf(BattleEvent.Fainted(true)), frame)
        assertFalse(camera.busy)
        director.handle(listOf(BattleEvent.SentOut(true)), frame)
        assertTrue(camera.busy, "the send-out switch is separate from the faint switch")
    }

    @Test
    fun switchingTheOrbitOffStillShowsTheResultText() {
        config = CbprConfig(victoryOrbit = false)
        commandPhase()
        director.handle(listOf(BattleEvent.Outcome("Reckshin")), frame)
        assertFalse(camera.busy)
        assertEquals(1, results.size)
    }

    @Test
    fun switchingCalloutsOffHidesTheResultButKeepsTheOrbit() {
        config = CbprConfig(callouts = false)
        commandPhase()
        director.handle(listOf(BattleEvent.Outcome("Reckshin")), frame)
        assertTrue(results.isEmpty())
        assertTrue(camera.busy)
    }

    @Test
    fun theMasterSwitchTurnsEveryShotOffButNotTheResultText() {
        config = CbprConfig(cinematicShots = false)
        commandPhase()
        director.handle(
            listOf(BattleEvent.Fainted(true), BattleEvent.SentOut(true), BattleEvent.Outcome("Reckshin")),
            frame,
        )
        assertFalse(camera.busy)
        assertEquals(1, results.size)
    }

    @Test
    fun momentsDuringTheIntroDoNotInterruptIt() {
        camera.begin(vanilla)
        director.handle(listOf(BattleEvent.SentOut(true), BattleEvent.Fainted(false)), frame)
        assertFalse(camera.busy)
        assertEquals(BattleCamera.Phase.INTRO, camera.phase)
    }
}
