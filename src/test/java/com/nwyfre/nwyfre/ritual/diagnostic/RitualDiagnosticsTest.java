package com.nwyfre.nwyfre.ritual.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.nwyfre.nwyfre.ritual.Element;
import com.nwyfre.nwyfre.ritual.RitualAugment;
import com.nwyfre.nwyfre.ritual.RitualDemands;
import com.nwyfre.nwyfre.ritual.RitualRecipe;
import com.nwyfre.nwyfre.ritual.RitualSite;
import com.nwyfre.nwyfre.ritual.effect.WorldEffects;
import com.nwyfre.nwyfre.ritual.pattern.MatchResult;
import com.nwyfre.nwyfre.ritual.pattern.PatternCell;
import com.nwyfre.nwyfre.ritual.pattern.RitualPattern;
import com.nwyfre.nwyfre.ritual.prereq.PrerequisiteContext;
import com.nwyfre.nwyfre.ritual.prereq.RitualPrerequisite;
import com.nwyfre.nwyfre.ritual.prereq.StagePrerequisite;
import com.nwyfre.nwyfre.ritual.prereq.WorldPrerequisites;
import com.nwyfre.nwyfre.stage.PlayerStages;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * The diagnostic ladder.
 *
 * <p>{@code specs/rituals.md} asks for Occultism-style diagnostics, which means the thing being
 * tested is a judgement call: of several true statements about a half-built circle, which one is
 * most useful? These tests pin that judgement down, because the whole feature is worthless if it
 * tells a player about a missing offering when what they actually lack is half the circle.
 *
 * <p>Assertions are on <strong>translation keys, never rendered English</strong>, so rewording any
 * message is free (CLAUDE.md: a test must not turn a content edit into a build failure).
 */
class RitualDiagnosticsTest {

    private static final ResourceKey<Element> EARTH = element("earth");
    private static final ResourceKey<Element> FIRE = element("fire");
    private static final ResourceLocation STAGE =
            ResourceLocation.fromNamespaceAndPath("nwyfre_test", "hidden_marker");
    private static final BlockPos ALTAR = new BlockPos(0, 64, 0);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ResourceKey<Element> element(String path) {
        return ResourceKey.create(RitualPattern.ELEMENT_REGISTRY,
                ResourceLocation.fromNamespaceAndPath("nwyfre_test", path));
    }

    /** Stands in for the registry lookup the sender does in production. */
    private static Component nameOf(ResourceKey<Element> key) {
        return Component.literal(key.location().getPath());
    }

    private static String keyOf(Component component) {
        return component.getContents() instanceof TranslatableContents translatable
                ? translatable.getKey()
                : "<literal:" + component.getString() + ">";
    }

    private static List<String> keysOf(DiagnosticReport report) {
        return report.lines().stream().map(RitualDiagnosticsTest::keyOf).toList();
    }

    // ---- fixtures ---------------------------------------------------------------------------

    private static PatternCell bowl(ResourceKey<Element> element) {
        return new PatternCell(new PatternCell.Predicate.IsTag(
                com.nwyfre.nwyfre.registry.NwyfreTags.Blocks.RITUAL_BOWLS),
                Optional.of(element), Optional.empty());
    }

    private static PatternCell glyph() {
        return new PatternCell(new PatternCell.Predicate.IsTag(
                com.nwyfre.nwyfre.registry.NwyfreTags.Blocks.CHALK_GLYPHS),
                Optional.empty(), Optional.empty());
    }

    private static RitualPattern pattern() {
        return RitualPattern.of(0,
                List.of(List.of("_E_", "gAg", "_F_")),
                Map.of('_', new PatternCell(new PatternCell.Predicate.Any(),
                                Optional.empty(), Optional.empty()),
                        'A', new PatternCell(new PatternCell.Predicate.IsAltar(),
                                Optional.empty(), Optional.empty()),
                        'g', glyph(), 'E', bowl(EARTH), 'F', bowl(FIRE)));
    }

    private static RitualRecipe recipe(ItemStack activation, Map<ResourceKey<Element>, Ingredient> offerings,
            Optional<RitualPrerequisite> prerequisites) {
        return new RitualRecipe(
                new RitualRecipe.Placement(
                        ResourceKey.create(RitualPattern.REGISTRY,
                                ResourceLocation.fromNamespaceAndPath("nwyfre_test", "circle")),
                        Ingredient.of(activation),
                        offerings),
                new RitualRecipe.Timing(100, false, 24000),
                RitualDemands.NONE,
                prerequisites,
                false,
                new WorldEffects.SetTime(WorldEffects.SetTime.Target.SUNRISE, 0),
                List.<RitualAugment>of(),
                ItemStack.EMPTY);
    }

    private static RitualSite.Candidate candidate(RitualRecipe recipe, MatchResult match,
            Map<ResourceKey<Element>, ItemStack> bowls) {
        return new RitualSite.Candidate(
                new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("nwyfre_test", "stub"), recipe),
                pattern(), match, bowls);
    }

    private static MatchResult matched() {
        return new MatchResult(true, Map.of(),
                Map.of(EARTH, ALTAR.north(), FIRE, ALTAR.south()));
    }

    private static MatchResult missingGlyphs() {
        return new MatchResult(false,
                Map.of(ALTAR.east(), glyph(), ALTAR.west(), glyph()),
                Map.of(EARTH, ALTAR.north(), FIRE, ALTAR.south()));
    }

    private static MatchResult missingBowlsOnly() {
        return new MatchResult(false,
                Map.of(ALTAR.north(), bowl(EARTH)),
                Map.of(EARTH, ALTAR.north(), FIRE, ALTAR.south()));
    }

    private static PrerequisiteContext context(PlayerStages stages, long time) {
        return new PrerequisiteContext() {
            @Override
            public BlockPos altarPos() {
                return ALTAR;
            }

            @Override
            public long dayTime() {
                return time;
            }

            @Override
            public int moonPhase() {
                return 0;
            }

            @Override
            public boolean raining() {
                return false;
            }

            @Override
            public boolean thundering() {
                return false;
            }

            @Override
            public ResourceKey<Level> dimension() {
                return Level.OVERWORLD;
            }

            @Override
            public Holder<Biome> biomeAt(BlockPos pos) {
                throw new UnsupportedOperationException();
            }

            @Override
            public BlockState blockAt(BlockPos pos) {
                return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }

            @Override
            public FluidState fluidAt(BlockPos pos) {
                return net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
            }

            @Override
            public Optional<PlayerStages> stages() {
                return Optional.ofNullable(stages);
            }

            @Override
            public HolderLookup.Provider registries() {
                return net.minecraft.core.RegistryAccess
                        .fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            }
        };
    }

    // ---- the ladder -------------------------------------------------------------------------

    @Test
    void withNoRitualsAtAllItSaysSo() {
        DiagnosticReport report = RitualDiagnostics.diagnose(List.of(),
                new ItemStack(Items.CLOCK), context(new PlayerStages(), 0),
                RitualDiagnosticsTest::nameOf);

        assertEquals(DiagnosticReport.Rung.NOTHING_CLOSE, report.rung());
        assertEquals(List.of("nwyfre.ritual.diag.nothing_close"), keysOf(report));
    }

    @Test
    void anIncompleteCircleMarksTheMissingBlocks() {
        // The whole point of the feature: turn "something is wrong" into "those blocks, there".
        DiagnosticReport report = RitualDiagnostics.diagnose(
                List.of(candidate(recipe(new ItemStack(Items.CLOCK), Map.of(), Optional.empty()),
                        missingGlyphs(), Map.of())),
                new ItemStack(Items.CLOCK), context(new PlayerStages(), 0),
                RitualDiagnosticsTest::nameOf);

        assertEquals(DiagnosticReport.Rung.PATTERN_INCOMPLETE, report.rung());
        assertEquals(List.of("nwyfre.ritual.diag.pattern_incomplete"), keysOf(report));
        assertEquals(2, report.marks().size(), "both missing glyphs should be marked");
        assertTrue(report.marks().contains(ALTAR.east()));
    }

    @Test
    void aCircleMissingOnlyBowlsGetsItsOwnMessage() {
        // A player in this state usually does not know bowls are a separate thing to place, so
        // reporting it as a generic incomplete circle would be unhelpful.
        DiagnosticReport report = RitualDiagnostics.diagnose(
                List.of(candidate(recipe(new ItemStack(Items.CLOCK), Map.of(), Optional.empty()),
                        missingBowlsOnly(), Map.of())),
                new ItemStack(Items.CLOCK), context(new PlayerStages(), 0),
                RitualDiagnosticsTest::nameOf);

        assertEquals(DiagnosticReport.Rung.NO_BOWLS, report.rung());
        assertEquals(List.of("nwyfre.ritual.diag.no_bowls"), keysOf(report));
        assertEquals(List.of(ALTAR.north()), report.marks());
    }

    @Test
    void theClosestCircleIsTheOneExplained() {
        // Two rituals, neither built. The one needing less work is the one the player was
        // probably attempting, so that is the one to describe.
        RitualSite.Candidate far = candidate(
                recipe(new ItemStack(Items.CLOCK), Map.of(), Optional.empty()), missingGlyphs(), Map.of());
        RitualSite.Candidate near = candidate(
                recipe(new ItemStack(Items.CLOCK), Map.of(), Optional.empty()), missingBowlsOnly(), Map.of());

        DiagnosticReport report = RitualDiagnostics.diagnose(List.of(far, near),
                new ItemStack(Items.CLOCK), context(new PlayerStages(), 0),
                RitualDiagnosticsTest::nameOf);

        assertEquals(DiagnosticReport.Rung.NO_BOWLS, report.rung(),
                "the nearer circle, missing one bowl, should be the one explained");
    }

    @Test
    void aBuiltCircleWithTheWrongItemNamesWhatItWants() {
        DiagnosticReport report = RitualDiagnostics.diagnose(
                List.of(candidate(recipe(new ItemStack(Items.CLOCK), Map.of(), Optional.empty()),
                        matched(), Map.of())),
                new ItemStack(Items.DIRT), context(new PlayerStages(), 0),
                RitualDiagnosticsTest::nameOf);

        assertEquals(DiagnosticReport.Rung.WRONG_ACTIVATION, report.rung());
        assertEquals("nwyfre.ritual.diag.wrong_activation", keysOf(report).getFirst());
        assertTrue(keysOf(report).contains("nwyfre.ritual.diag.activation_option"),
                "it should name at least one item the circle answers to");
    }

    @Test
    void missingOfferingsAreListedByElementAndMarked() {
        RitualRecipe recipe = recipe(new ItemStack(Items.CLOCK),
                Map.of(EARTH, Ingredient.of(new ItemStack(Items.WHEAT)),
                        FIRE, Ingredient.of(new ItemStack(Items.COAL))),
                Optional.empty());

        DiagnosticReport report = RitualDiagnostics.diagnose(
                List.of(candidate(recipe, matched(),
                        Map.of(EARTH, new ItemStack(Items.WHEAT), FIRE, ItemStack.EMPTY))),
                new ItemStack(Items.CLOCK), context(new PlayerStages(), 0),
                RitualDiagnosticsTest::nameOf);

        assertEquals(DiagnosticReport.Rung.MISSING_OFFERINGS, report.rung());
        assertEquals("nwyfre.ritual.diag.missing_offerings", keysOf(report).getFirst());
        assertTrue(keysOf(report).contains("nwyfre.ritual.diag.offering_line"));
        // Only the empty bowl is reported, not the satisfied one.
        assertEquals(List.of(ALTAR.south()), report.marks(),
                "only the unsatisfied fire bowl should be marked");
    }

    @Test
    void prerequisiteFailuresListOnlyTheResponsibleTerms() {
        RitualPrerequisite gate = new RitualPrerequisite.AllOf(List.of(
                new WorldPrerequisites.TimeOfDay(WorldPrerequisites.TimeOfDay.Phase.NIGHT, 0, 0),
                new WorldPrerequisites.Elevation(Integer.MIN_VALUE, Integer.MAX_VALUE)));

        DiagnosticReport report = RitualDiagnostics.diagnose(
                List.of(candidate(recipe(new ItemStack(Items.CLOCK), Map.of(), Optional.of(gate)),
                        matched(), Map.of())),
                new ItemStack(Items.CLOCK), context(new PlayerStages(), 6000),
                RitualDiagnosticsTest::nameOf);

        assertEquals(DiagnosticReport.Rung.PREREQUISITES, report.rung());
        assertEquals(List.of("nwyfre.ritual.diag.prerequisites",
                        "nwyfre.ritual.prereq.time_of_day.night"),
                keysOf(report),
                "the satisfied elevation term should not be mentioned");
    }

    @Test
    void aStageRefusalNeverLeaksTheStageId() {
        // Stage ids are pack-internal and a spoiler. They belong in the debug log, not in chat.
        RitualPrerequisite gate = new StagePrerequisite(List.of(STAGE), StagePrerequisite.Mode.ALL);

        DiagnosticReport report = RitualDiagnostics.diagnose(
                List.of(candidate(recipe(new ItemStack(Items.CLOCK), Map.of(), Optional.of(gate)),
                        matched(), Map.of())),
                new ItemStack(Items.CLOCK), context(new PlayerStages(), 0),
                RitualDiagnosticsTest::nameOf);

        assertEquals(DiagnosticReport.Rung.PREREQUISITES, report.rung());

        String rendered = report.lines().stream()
                .map(line -> line.getString() + line.toString())
                .reduce("", String::concat);
        assertFalse(rendered.contains("hidden_marker"), "the stage path must not reach the player");
        assertFalse(rendered.contains("nwyfre_test"), "the stage namespace must not reach the player");
    }

    @Test
    void anEverythingReadyCircleReportsNothing() {
        // Reached only when something else refused the start, such as an event veto. Saying
        // nothing is correct: there is no advice to give.
        DiagnosticReport report = RitualDiagnostics.diagnose(
                List.of(candidate(recipe(new ItemStack(Items.CLOCK), Map.of(), Optional.empty()),
                        matched(), Map.of())),
                new ItemStack(Items.CLOCK), context(new PlayerStages(), 0),
                RitualDiagnosticsTest::nameOf);

        assertEquals(DiagnosticReport.Rung.READY, report.rung());
        assertFalse(report.hasAnythingToSay());
    }

    @Test
    void aBuiltCircleIsPreferredOverACloserUnbuiltOne() {
        // Once any circle is actually standing, the problem has moved on from construction.
        RitualSite.Candidate unbuilt = candidate(
                recipe(new ItemStack(Items.CLOCK), Map.of(), Optional.empty()), missingBowlsOnly(), Map.of());
        RitualSite.Candidate built = candidate(
                recipe(new ItemStack(Items.NETHER_STAR), Map.of(), Optional.empty()), matched(), Map.of());

        DiagnosticReport report = RitualDiagnostics.diagnose(List.of(unbuilt, built),
                new ItemStack(Items.DIRT), context(new PlayerStages(), 0),
                RitualDiagnosticsTest::nameOf);

        assertEquals(DiagnosticReport.Rung.WRONG_ACTIVATION, report.rung());
    }
}
