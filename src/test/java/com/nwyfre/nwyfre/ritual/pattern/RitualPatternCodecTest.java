package com.nwyfre.nwyfre.ritual.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

/**
 * Pins down the pattern file format: what a valid pattern resolves to, and -- more importantly --
 * which malformed patterns are rejected with an error rather than loading into something subtly
 * wrong. A pattern that silently resolves to the wrong offsets would make every ritual using it
 * unbuildable with no clue why, so the validation is the part worth testing hardest.
 *
 * <p>Fixtures live in {@code src/test/resources/fixtures/ritual_pattern} and never change, so
 * editing the real {@code lesser_circle} cannot break these.
 */
class RitualPatternCodecTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        // Block and tag codecs resolve against the vanilla registries.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static DataResult<RitualPattern> parse(String fixture) {
        return RitualPattern.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Fixtures.read(fixture)));
    }

    private static RitualPattern parseOrThrow(String fixture) {
        return parse(fixture).getOrThrow();
    }

    @Test
    void resolvesCellsAsOffsetsFromTheAltar() {
        RitualPattern pattern = parseOrThrow("valid_cross");

        // The fixture is a 3x3 with the altar dead centre, so the four bowls land one block out.
        assertEquals(BlockPos.ZERO.north(), pattern.offeringSlotOffset(Fixtures.EARTH).orElseThrow(),
                "earth should resolve to the north cell");
        assertEquals(BlockPos.ZERO.east(), pattern.offeringSlotOffset(Fixtures.AIR).orElseThrow(),
                "air should resolve to the east cell");
        assertEquals(BlockPos.ZERO.south(), pattern.offeringSlotOffset(Fixtures.FIRE).orElseThrow(),
                "fire should resolve to the south cell");
        assertEquals(BlockPos.ZERO.west(), pattern.offeringSlotOffset(Fixtures.WATER).orElseThrow(),
                "water should resolve to the west cell");
    }

    @Test
    void northIsNegativeZSoALayerReadsLikeAMap() {
        // This is the convention the whole element model rests on: if row order ever flipped,
        // every shipped pattern would quietly place its bowls at the wrong elements.
        RitualPattern pattern = parseOrThrow("valid_cross");
        assertTrue(pattern.offeringSlotOffset(Fixtures.EARTH).orElseThrow().getZ() < 0,
                "the first row of a layer must be north of the altar");
    }

    @Test
    void offsetYLiftsLayersAboveTheAltar() {
        RitualPattern pattern = parseOrThrow("valid_two_layer");
        assertTrue(pattern.cells().containsKey(new BlockPos(0, 1, 0)),
                "the second layer should resolve one block above the altar");
    }

    @Test
    void anchorAlwaysResolvesToOrigin() {
        RitualPattern pattern = parseOrThrow("valid_cross");
        assertTrue(pattern.cells().get(BlockPos.ZERO).isAnchor(),
                "the cell at the origin should be the altar anchor");
    }

    @Test
    void wildcardCellsDoNotCountAsDemands() {
        RitualPattern pattern = parseOrThrow("valid_cross");
        // Four bowls and four glyphs are demanded; the anchor and the wildcards are not.
        assertEquals(8, pattern.demandingCellCount());
    }

    @Test
    void rejectsAPatternWithNoAltar() {
        DataResult<RitualPattern> result = parse("broken_no_altar");
        assertTrue(result.error().isPresent(), "a pattern with no anchor must not load");
        assertTrue(result.error().orElseThrow().message().contains("nwyfre:altar"),
                "the error should say what is missing, got: " + result.error().orElseThrow().message());
    }

    @Test
    void rejectsAPatternWithTwoAltars() {
        DataResult<RitualPattern> result = parse("broken_two_altars");
        assertTrue(result.error().isPresent(), "an ambiguous anchor must not load");
    }

    @Test
    void rejectsRaggedRows() {
        DataResult<RitualPattern> result = parse("broken_ragged");
        assertTrue(result.error().isPresent(), "rows of differing length must not load");
        assertTrue(result.error().orElseThrow().message().contains("same length"));
    }

    @Test
    void rejectsTwoCellsClaimingOneElement() {
        DataResult<RitualPattern> result = parse("broken_duplicate_element");
        assertTrue(result.error().isPresent(), "two bowls for one element is ambiguous and must not load");
    }

    @Test
    void rejectsAnAnchorThatDoesNotLandOnYZero() {
        // offset_y and the layer the altar is drawn in have to agree, or every offset is shifted.
        DataResult<RitualPattern> result = parse("broken_anchor_off_y");
        assertTrue(result.error().isPresent(), "an anchor off Y 0 must not load");
    }

    @Test
    void rejectsMultiCharacterMappingKeys() {
        DataResult<RitualPattern> result = parse("broken_long_key");
        assertTrue(result.error().isPresent(), "a mapping key longer than one character must not load");
    }

    @Test
    void tagCellsCarryAPreviewBlock() {
        RitualPattern pattern = parseOrThrow("valid_cross");
        PatternCell earth = pattern.cells().get(pattern.offeringSlotOffset(Fixtures.EARTH).orElseThrow());
        assertTrue(earth.previewBlock().isPresent(), "a tag cell needs a display block for previews");
        assertFalse(earth.isWildcard());
        assertTrue(earth.isOfferingSlot());
    }

    /** Fixture loading and the element keys the fixtures refer to. */
    static final class Fixtures {

        static final net.minecraft.resources.ResourceKey<com.nwyfre.nwyfre.ritual.Element> EARTH = element("earth");
        static final net.minecraft.resources.ResourceKey<com.nwyfre.nwyfre.ritual.Element> AIR = element("air");
        static final net.minecraft.resources.ResourceKey<com.nwyfre.nwyfre.ritual.Element> FIRE = element("fire");
        static final net.minecraft.resources.ResourceKey<com.nwyfre.nwyfre.ritual.Element> WATER = element("water");

        private static net.minecraft.resources.ResourceKey<com.nwyfre.nwyfre.ritual.Element> element(String path) {
            return net.minecraft.resources.ResourceKey.create(
                    RitualPattern.ELEMENT_REGISTRY,
                    ResourceLocation.fromNamespaceAndPath("nwyfre_test", path));
        }

        static String read(String name) {
            String path = "/fixtures/ritual_pattern/" + name + ".json";
            try (var stream = RitualPatternCodecTest.class.getResourceAsStream(path)) {
                if (stream == null) {
                    throw new IllegalStateException("Missing test fixture: " + path);
                }
                return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Could not read test fixture: " + path, exception);
            }
        }

        private Fixtures() {
        }
    }
}
