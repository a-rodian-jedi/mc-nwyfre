package com.nwyfre.nwyfre.ritual.prereq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.nwyfre.nwyfre.stage.PlayerStages;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

/**
 * The prerequisite rules themselves.
 *
 * <p>These are the gates that decide whether a ritual may start, so getting one subtly wrong is
 * exactly the kind of bug that is invisible until a player complains their rite never works. The
 * cases that earn their place here are the ones with a trap in them: wrapping time windows,
 * vanilla reporting rain during a thunderstorm, moon phase 0 meaning full, and the empty-composite
 * semantics.
 */
class RitualPrerequisiteTest {

    private static final ResourceLocation STAGE_A = ResourceLocation.fromNamespaceAndPath("nwyfre_test", "hidden_progression_marker");
    private static final ResourceLocation STAGE_B = ResourceLocation.fromNamespaceAndPath("nwyfre_test", "second_hidden_marker");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static RitualPrerequisite always() {
        return new WorldPrerequisites.Elevation(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    private static RitualPrerequisite never() {
        return new WorldPrerequisites.Elevation(1000, 2000);
    }

    // ---- composites ------------------------------------------------------------------------

    @Test
    void emptyAllOfPasses() {
        // Matches PlayerStages.hasAll, which already set this precedent in the codebase.
        assertTrue(new RitualPrerequisite.AllOf(List.of()).test(TestPrerequisiteContext.create()));
    }

    @Test
    void emptyAnyOfFails() {
        // Matches PlayerStages.hasAny.
        assertFalse(new RitualPrerequisite.AnyOf(List.of()).test(TestPrerequisiteContext.create()));
    }

    @Test
    void allOfNeedsEveryTerm() {
        TestPrerequisiteContext context = TestPrerequisiteContext.create();
        assertTrue(new RitualPrerequisite.AllOf(List.of(always(), always())).test(context));
        assertFalse(new RitualPrerequisite.AllOf(List.of(always(), never())).test(context));
    }

    @Test
    void anyOfNeedsOneTerm() {
        TestPrerequisiteContext context = TestPrerequisiteContext.create();
        assertTrue(new RitualPrerequisite.AnyOf(List.of(never(), always())).test(context));
        assertFalse(new RitualPrerequisite.AnyOf(List.of(never(), never())).test(context));
    }

    @Test
    void notInverts() {
        TestPrerequisiteContext context = TestPrerequisiteContext.create();
        assertFalse(new RitualPrerequisite.Not(always()).test(context));
        assertTrue(new RitualPrerequisite.Not(never()).test(context));
    }

    @Test
    void allOfReportsOnlyTheResponsibleTerms() {
        // The whole point of collectFailures: tell the player the one thing they are missing.
        RitualPrerequisite tree = new RitualPrerequisite.AllOf(List.of(
                always(),
                new WorldPrerequisites.TimeOfDay(WorldPrerequisites.TimeOfDay.Phase.NIGHT, 0, 0),
                always()));

        List<Component> failures = tree.failures(TestPrerequisiteContext.create().time(6000));

        assertEquals(1, failures.size(), "only the time term should be reported");
        assertEquals("nwyfre.ritual.prereq.time_of_day.night",
                ((net.minecraft.network.chat.contents.TranslatableContents) failures.getFirst().getContents()).getKey());
    }

    @Test
    void anyOfReportsOneCombinedLineRatherThanEveryAlternative() {
        // Listing each unmet alternative separately would read as though all were required.
        RitualPrerequisite tree = new RitualPrerequisite.AnyOf(List.of(never(), never()));

        List<Component> failures = tree.failures(TestPrerequisiteContext.create());

        assertEquals(1, failures.size());
        assertEquals("nwyfre.ritual.prereq.any_of",
                ((net.minecraft.network.chat.contents.TranslatableContents) failures.getFirst().getContents()).getKey());
    }

    @Test
    void aSatisfiedTreeReportsNothing() {
        assertTrue(new RitualPrerequisite.AllOf(List.of(always())).failures(
                TestPrerequisiteContext.create()).isEmpty());
    }

    // ---- time ------------------------------------------------------------------------------

    @Test
    void namedTimePhasesMatchTheirWindows() {
        var night = new WorldPrerequisites.TimeOfDay(WorldPrerequisites.TimeOfDay.Phase.NIGHT, 0, 0);

        assertTrue(night.test(TestPrerequisiteContext.create().time(18000)), "midnight is night");
        assertFalse(night.test(TestPrerequisiteContext.create().time(6000)), "noon is not night");
    }

    @Test
    void timeIsTakenModuloADaySoLaterDaysStillMatch() {
        var day = new WorldPrerequisites.TimeOfDay(WorldPrerequisites.TimeOfDay.Phase.DAY, 0, 0);
        // Day 10 at noon. Without the modulo this would fall outside every window forever.
        assertTrue(day.test(TestPrerequisiteContext.create().time(24000L * 10 + 6000)));
    }

    @Test
    void customTimeWindowsWrapAroundMidnight() {
        // A window whose min exceeds its max is the span across midnight, which is the only way
        // to express something like "the small hours".
        var acrossMidnight = new WorldPrerequisites.TimeOfDay(
                WorldPrerequisites.TimeOfDay.Phase.CUSTOM, 23000, 1000);

        assertTrue(acrossMidnight.test(TestPrerequisiteContext.create().time(23500)));
        assertTrue(acrossMidnight.test(TestPrerequisiteContext.create().time(500)));
        assertFalse(acrossMidnight.test(TestPrerequisiteContext.create().time(12000)));
    }

    // ---- moon ------------------------------------------------------------------------------

    @Test
    void moonPhaseZeroIsTheFullMoon() {
        // Vanilla numbering, and the single easiest thing to get backwards in a ritual pack.
        var fullMoon = new WorldPrerequisites.MoonPhase(List.of(0));

        assertTrue(fullMoon.test(TestPrerequisiteContext.create().moon(0)));
        assertFalse(fullMoon.test(TestPrerequisiteContext.create().moon(4)));
    }

    @Test
    void moonPhaseAcceptsSeveralPhases() {
        var waxing = new WorldPrerequisites.MoonPhase(List.of(6, 7));
        assertTrue(waxing.test(TestPrerequisiteContext.create().moon(7)));
        assertFalse(waxing.test(TestPrerequisiteContext.create().moon(5)));
    }

    // ---- weather ---------------------------------------------------------------------------

    @Test
    void rainDoesNotAlsoAcceptAThunderstorm() {
        // Vanilla reports isRaining() as true during thunder, so a naive check would conflate
        // them and "rain" rituals would fire in storms.
        var rain = new WorldPrerequisites.Weather(WorldPrerequisites.Weather.Kind.RAIN);

        assertTrue(rain.test(TestPrerequisiteContext.create().withRain()));
        assertFalse(rain.test(TestPrerequisiteContext.create().withThunder()),
                "a thunderstorm must not satisfy a plain rain requirement");
    }

    @Test
    void thunderAndClearMatchOnlyThemselves() {
        var thunder = new WorldPrerequisites.Weather(WorldPrerequisites.Weather.Kind.THUNDER);
        var clear = new WorldPrerequisites.Weather(WorldPrerequisites.Weather.Kind.CLEAR);

        assertTrue(thunder.test(TestPrerequisiteContext.create().withThunder()));
        assertFalse(thunder.test(TestPrerequisiteContext.create().withRain()));

        assertTrue(clear.test(TestPrerequisiteContext.create().withClearSkies()));
        assertFalse(clear.test(TestPrerequisiteContext.create().withRain()));
    }

    // ---- place -----------------------------------------------------------------------------

    @Test
    void elevationIsInclusiveOnBothEnds() {
        var band = new WorldPrerequisites.Elevation(60, 70);

        assertTrue(band.test(TestPrerequisiteContext.create().at(new BlockPos(0, 60, 0))));
        assertTrue(band.test(TestPrerequisiteContext.create().at(new BlockPos(0, 70, 0))));
        assertFalse(band.test(TestPrerequisiteContext.create().at(new BlockPos(0, 71, 0))));
    }

    @Test
    void elevationDefaultsAreOpenEnded() {
        var deep = new WorldPrerequisites.Elevation(Integer.MIN_VALUE, 0);
        assertTrue(deep.test(TestPrerequisiteContext.create().at(new BlockPos(0, -40, 0))));
        assertFalse(deep.test(TestPrerequisiteContext.create().at(new BlockPos(0, 40, 0))));
    }

    @Test
    void dimensionMatchesExactly() {
        var overworld = new WorldPrerequisites.InDimension(Level.OVERWORLD);

        assertTrue(overworld.test(TestPrerequisiteContext.create().dimension(Level.OVERWORLD)));
        assertFalse(overworld.test(TestPrerequisiteContext.create().dimension(Level.NETHER)));
    }

    @Test
    void blockAtDefaultsToTheBlockBelowTheAltar() {
        // "On dirt" is the overwhelmingly common case, so the default offset has to be downward
        // or every pack author would have to write it out.
        var onDirt = new WorldPrerequisites.BlockAt(new Vec3i(0, -1, 0),
                HolderSet.direct(BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.DIRT)));

        assertTrue(onDirt.test(TestPrerequisiteContext.create()
                .block(TestPrerequisiteContext.ALTAR.below(), Blocks.DIRT.defaultBlockState())));
        assertFalse(onDirt.test(TestPrerequisiteContext.create()
                .block(TestPrerequisiteContext.ALTAR.below(), Blocks.STONE.defaultBlockState())));
    }

    @Test
    void blockAtHonoursAnExplicitOffset() {
        var ceiling = new WorldPrerequisites.BlockAt(new Vec3i(0, 3, 0),
                HolderSet.direct(BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.GOLD_BLOCK)));

        assertTrue(ceiling.test(TestPrerequisiteContext.create()
                .block(TestPrerequisiteContext.ALTAR.above(3), Blocks.GOLD_BLOCK.defaultBlockState())));
        assertFalse(ceiling.test(TestPrerequisiteContext.create()
                .block(TestPrerequisiteContext.ALTAR.below(), Blocks.GOLD_BLOCK.defaultBlockState())),
                "the offset must be respected, not just any nearby position");
    }

    @Test
    void blockAtAcceptsAnyOfSeveralBlocks() {
        var soft = new WorldPrerequisites.BlockAt(new Vec3i(0, -1, 0), HolderSet.direct(
                BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.DIRT),
                BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.GRASS_BLOCK)));

        assertTrue(soft.test(TestPrerequisiteContext.create()
                .block(TestPrerequisiteContext.ALTAR.below(), Blocks.GRASS_BLOCK.defaultBlockState())));
    }

    @Test
    void fluidAtDefaultsToTheAltarPositionItself() {
        // "In water" means at the altar, not below it.
        var inWater = new WorldPrerequisites.FluidAt(Vec3i.ZERO,
                HolderSet.direct(BuiltInRegistries.FLUID.wrapAsHolder(Fluids.WATER)));

        assertTrue(inWater.test(TestPrerequisiteContext.create()
                .fluid(TestPrerequisiteContext.ALTAR, Fluids.WATER.defaultFluidState())));
        assertFalse(inWater.test(TestPrerequisiteContext.create()), "dry air is not water");
    }

    // ---- stages ----------------------------------------------------------------------------

    @Test
    void stageTermRequiresEveryStageByDefault() {
        PlayerStages onlyA = new PlayerStages();
        onlyA.grant(STAGE_A);

        var needsBoth = new StagePrerequisite(List.of(STAGE_A, STAGE_B), StagePrerequisite.Mode.ALL);

        assertFalse(needsBoth.test(TestPrerequisiteContext.create().withStages(onlyA)));

        onlyA.grant(STAGE_B);
        assertTrue(needsBoth.test(TestPrerequisiteContext.create().withStages(onlyA)));
    }

    @Test
    void stageTermInAnyModeNeedsJustOne() {
        PlayerStages onlyB = new PlayerStages();
        onlyB.grant(STAGE_B);

        var needsEither = new StagePrerequisite(List.of(STAGE_A, STAGE_B), StagePrerequisite.Mode.ANY);

        assertTrue(needsEither.test(TestPrerequisiteContext.create().withStages(onlyB)));
    }

    @Test
    void stageTermRefusesAnUnattendedRitualByDefault() {
        // A hopper has no stages, so letting it through would silently bypass progression.
        var gated = new StagePrerequisite(List.of(STAGE_A), StagePrerequisite.Mode.ALL);

        assertFalse(gated.test(TestPrerequisiteContext.create().unattended()),
                "automation must not satisfy a stage gate while allowUnattendedStagedRituals is off");
    }

    @Test
    void stageTermDescriptionLeaksNoStageIds() {
        // Stage ids are pack-internal and a spoiler; they belong in the debug log, not in chat.
        var gated = new StagePrerequisite(List.of(STAGE_A), StagePrerequisite.Mode.ALL);

        String rendered = gated.describe().getString() + gated.describe().toString();

        assertFalse(rendered.contains("nwyfre_test"), "the stage namespace must not reach the player");
        assertFalse(rendered.contains(STAGE_A.getPath()), "the stage path must not reach the player");
    }
}
