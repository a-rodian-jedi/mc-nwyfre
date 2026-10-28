package com.nwyfre.nwyfre.ritual.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.nwyfre.nwyfre.ritual.Element;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Exercises the matcher against a hand-built world.
 *
 * <p>This is the payoff for {@link PatternMatcher} taking a {@code Function<BlockPos, BlockState>}
 * instead of a level: the whole matcher runs headlessly here in milliseconds, against a plain
 * {@code Map} of positions, so the GameTest suite only has to prove the wiring rather than the
 * matching rules.
 *
 * <p>Patterns here are built programmatically with concrete vanilla blocks rather than tags, since
 * tag contents are a datapack concern and are not loaded in a headless test.
 */
class PatternMatcherTest {

    private static final ResourceKey<Element> EARTH = element("earth");
    private static final ResourceKey<Element> FIRE = element("fire");

    /** Stands in for a glyph. */
    private static final Block GLYPH = Blocks.WHITE_WOOL;
    /** Stands in for a bowl. */
    private static final Block BOWL = Blocks.STONE;

    private static final BlockPos ALTAR = new BlockPos(100, 64, -40);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ResourceKey<Element> element(String path) {
        return ResourceKey.create(RitualPattern.ELEMENT_REGISTRY,
                ResourceLocation.fromNamespaceAndPath("nwyfre_test", path));
    }

    private static PatternCell cell(Block block) {
        return new PatternCell(new PatternCell.Predicate.IsBlock(block), Optional.empty(), Optional.empty());
    }

    private static PatternCell bowl(ResourceKey<Element> element) {
        return new PatternCell(new PatternCell.Predicate.IsBlock(BOWL), Optional.of(element), Optional.empty());
    }

    private static PatternCell anchor() {
        return new PatternCell(new PatternCell.Predicate.IsAltar(), Optional.empty(), Optional.empty());
    }

    private static PatternCell wildcard() {
        return new PatternCell(new PatternCell.Predicate.Any(), Optional.empty(), Optional.empty());
    }

    /**
     * A 3x3 circle: altar centre, an earth bowl north, a fire bowl south, glyphs east and west,
     * wildcards on the diagonals.
     */
    private static RitualPattern crossPattern() {
        return RitualPattern.of(0,
                List.of(List.of("_E_", "gAg", "_F_")),
                Map.of('_', wildcard(), 'A', anchor(), 'g', cell(GLYPH), 'E', bowl(EARTH), 'F', bowl(FIRE)));
    }

    /** A world where every demanding cell of {@link #crossPattern()} is satisfied. */
    private static Map<BlockPos, BlockState> completeWorld() {
        Map<BlockPos, BlockState> world = new HashMap<>();
        world.put(ALTAR.north(), BOWL.defaultBlockState());
        world.put(ALTAR.south(), BOWL.defaultBlockState());
        world.put(ALTAR.east(), GLYPH.defaultBlockState());
        world.put(ALTAR.west(), GLYPH.defaultBlockState());
        return world;
    }

    private static Function<BlockPos, BlockState> lookup(Map<BlockPos, BlockState> world) {
        return pos -> world.getOrDefault(pos, Blocks.AIR.defaultBlockState());
    }

    @Test
    void matchesACompleteCircle() {
        MatchResult result = PatternMatcher.match(crossPattern(), ALTAR, lookup(completeWorld()));

        assertTrue(result.matched(), "a fully built circle should match");
        assertTrue(result.missing().isEmpty());
    }

    @Test
    void reportsAMissingGlyphAtItsWorldPosition() {
        Map<BlockPos, BlockState> world = completeWorld();
        world.remove(ALTAR.east());

        MatchResult result = PatternMatcher.match(crossPattern(), ALTAR, lookup(world));

        assertFalse(result.matched());
        assertEquals(1, result.missingCount());
        // Absolute world position, not a pattern offset: the diagnostics mark these directly.
        assertTrue(result.missing().containsKey(ALTAR.east()),
                "the missing cell should be reported at its world position");
    }

    @Test
    void locatesOfferingSlotsInWorldSpace() {
        MatchResult result = PatternMatcher.match(crossPattern(), ALTAR, lookup(completeWorld()));

        assertEquals(ALTAR.north(), result.offeringSlot(EARTH).orElseThrow());
        assertEquals(ALTAR.south(), result.offeringSlot(FIRE).orElseThrow());
    }

    @Test
    void offeringSlotsAreReportedEvenWhenTheCircleIsIncomplete() {
        // The diagnostics need the slot positions precisely when the circle is NOT yet built,
        // so it can mark where bowls are supposed to go.
        Map<BlockPos, BlockState> world = completeWorld();
        world.clear();

        MatchResult result = PatternMatcher.match(crossPattern(), ALTAR, lookup(world));

        assertFalse(result.matched());
        assertEquals(ALTAR.north(), result.offeringSlot(EARTH).orElseThrow());
    }

    @Test
    void ignoresWildcardCells() {
        // The diagonals are wildcards, so filling them with junk must change nothing.
        Map<BlockPos, BlockState> world = completeWorld();
        world.put(ALTAR.north().east(), Blocks.LAVA.defaultBlockState());
        world.put(ALTAR.south().west(), Blocks.BEDROCK.defaultBlockState());

        assertTrue(PatternMatcher.match(crossPattern(), ALTAR, lookup(world)).matched());
    }

    @Test
    void ignoresTheAnchorCell() {
        // Nothing is placed at ALTAR itself, so if the anchor were tested this would fail.
        // The caller only calls match() because it already found an altar there.
        assertTrue(PatternMatcher.match(crossPattern(), ALTAR, lookup(completeWorld())).matched(),
                "the anchor cell must not be re-tested");
    }

    @Test
    void doesNotMatchWhenABowlHoldsTheWrongBlock() {
        Map<BlockPos, BlockState> world = completeWorld();
        world.put(ALTAR.north(), Blocks.DIRT.defaultBlockState());

        MatchResult result = PatternMatcher.match(crossPattern(), ALTAR, lookup(world));

        assertFalse(result.matched());
        assertTrue(result.missing().containsKey(ALTAR.north()));
    }

    @Test
    void missingOnlyOfferingSlotsDistinguishesAnUnfurnishedCircle() {
        // Glyphs drawn, bowls not placed: a distinct diagnostic, because a player in this state
        // usually does not know bowls are a thing yet.
        Map<BlockPos, BlockState> world = new HashMap<>();
        world.put(ALTAR.east(), GLYPH.defaultBlockState());
        world.put(ALTAR.west(), GLYPH.defaultBlockState());

        MatchResult result = PatternMatcher.match(crossPattern(), ALTAR, lookup(world));

        assertFalse(result.matched());
        assertTrue(result.missingOnlyOfferingSlots(), "only the bowls should be outstanding");
    }

    @Test
    void missingOnlyOfferingSlotsIsFalseWhenGlyphsAreAlsoGone() {
        MatchResult result = PatternMatcher.match(crossPattern(), ALTAR, lookup(Map.of()));
        assertFalse(result.missingOnlyOfferingSlots());
    }

    @Test
    void scoreRanksTheClosestPattern() {
        // Used to decide which circle to explain when nothing matched at all.
        RitualPattern pattern = crossPattern();
        Map<BlockPos, BlockState> world = completeWorld();

        assertEquals(pattern.demandingCellCount(), PatternMatcher.score(pattern, ALTAR, lookup(world)));

        world.remove(ALTAR.east());
        assertEquals(pattern.demandingCellCount() - 1, PatternMatcher.score(pattern, ALTAR, lookup(world)));
    }

    @Test
    void scoreNeverGoesNegative() {
        // demandingCellCount and the matcher must agree on what is skipped, or ranking breaks.
        RitualPattern pattern = crossPattern();
        assertTrue(PatternMatcher.score(pattern, ALTAR, lookup(Map.of())) >= 0);
    }

    @Test
    void airPredicateDemandsAnEmptyCell() {
        RitualPattern pattern = RitualPattern.of(0,
                List.of(List.of("cA")),
                Map.of('A', anchor(),
                        'c', new PatternCell(new PatternCell.Predicate.IsAir(),
                                Optional.empty(), Optional.empty())));

        assertTrue(PatternMatcher.match(pattern, ALTAR, lookup(Map.of())).matched(),
                "an air cell should be satisfied by air");

        Map<BlockPos, BlockState> blocked = Map.of(ALTAR.west(), Blocks.STONE.defaultBlockState());
        assertFalse(PatternMatcher.match(pattern, ALTAR, lookup(blocked)).matched(),
                "an air cell should reject a solid block");
    }

    @Test
    void relativeOffsetsFollowTheAltarAroundTheWorld() {
        // Offsets are relative, so the same pattern must work at any altar position.
        BlockPos elsewhere = new BlockPos(-2048, 12, 777);
        Map<BlockPos, BlockState> world = new HashMap<>();
        world.put(elsewhere.north(), BOWL.defaultBlockState());
        world.put(elsewhere.south(), BOWL.defaultBlockState());
        world.put(elsewhere.east(), GLYPH.defaultBlockState());
        world.put(elsewhere.west(), GLYPH.defaultBlockState());

        assertTrue(PatternMatcher.match(crossPattern(), elsewhere, lookup(world)).matched());
    }
}
