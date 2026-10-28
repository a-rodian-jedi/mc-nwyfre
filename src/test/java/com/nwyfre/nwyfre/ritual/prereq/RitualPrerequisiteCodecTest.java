package com.nwyfre.nwyfre.ritual.prereq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;

/**
 * The prerequisite file format.
 *
 * <p>Every term type is parsed from the JSON a pack author would actually write, so a rename or a
 * changed field name shows up here rather than as a silently-ignored gate in somebody's modpack.
 * The round trip matters too: a term that decodes but cannot re-encode would break any tooling
 * that reads recipes back out.
 *
 * <p>Needs {@link RegistryOps} rather than plain {@code JsonOps} because the block, fluid and biome
 * terms take a {@code HolderSet}, which resolves against the registries.
 *
 * <p><strong>Limit of this layer:</strong> a headless bootstrap has only the static registries, with
 * no tags loaded and no datapack registries, so {@code "#some:tag"} and biome references cannot be
 * resolved here. Those forms are exercised instead by a shipped recipe that uses them, which the
 * server parses on boot, plus the manual pass -- see the plan. Everything that does not need tags
 * (field names, defaults, dispatch, round trips) is pinned down here, where it is cheap.
 */
class RitualPrerequisiteCodecTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static DataResult<RitualPrerequisite> parse(String json) {
        return RitualPrerequisite.CODEC.parse(ops, JsonParser.parseString(json));
    }

    private static RitualPrerequisite parseOrThrow(String json) {
        return parse(json).getOrThrow();
    }

    /** Decode, re-encode, decode again: anything lossy shows up as an unequal second pass. */
    private static void assertRoundTrips(String json) {
        RitualPrerequisite first = parseOrThrow(json);
        JsonElement encoded = RitualPrerequisite.CODEC.encodeStart(ops, first).getOrThrow();
        RitualPrerequisite second = RitualPrerequisite.CODEC.parse(ops, encoded).getOrThrow();
        assertEquals(first, second, "re-decoding an encoded term should give back an equal term");
    }

    @Test
    void parsesStage() {
        RitualPrerequisite term = parseOrThrow("""
                {"type": "nwyfre:stage", "stages": ["nwyfre:awakening", "mypack:bronze_age"]}""");

        StagePrerequisite stage = assertInstanceOf(StagePrerequisite.class, term);
        assertEquals(2, stage.stages().size());
        assertEquals(StagePrerequisite.Mode.ALL, stage.mode(), "mode should default to all");
        assertEquals("mypack", stage.stages().get(1).getNamespace(),
                "a pack-invented stage id must survive, since stages are not a registry");
    }

    @Test
    void parsesStageInAnyMode() {
        StagePrerequisite stage = assertInstanceOf(StagePrerequisite.class, parseOrThrow("""
                {"type": "nwyfre:stage", "stages": ["nwyfre:awakening"], "mode": "any"}"""));
        assertEquals(StagePrerequisite.Mode.ANY, stage.mode());
    }

    @Test
    void parsesNamedTimeOfDay() {
        var term = assertInstanceOf(WorldPrerequisites.TimeOfDay.class, parseOrThrow("""
                {"type": "nwyfre:time_of_day", "time": "night"}"""));
        assertEquals(WorldPrerequisites.TimeOfDay.Phase.NIGHT, term.phase());
    }

    @Test
    void parsesCustomTimeWindow() {
        var term = assertInstanceOf(WorldPrerequisites.TimeOfDay.class, parseOrThrow("""
                {"type": "nwyfre:time_of_day", "min": 23000, "max": 1000}"""));
        assertEquals(WorldPrerequisites.TimeOfDay.Phase.CUSTOM, term.phase());
        assertEquals(23000, term.min());
        assertEquals(1000, term.max());
    }

    @Test
    void parsesMoonPhase() {
        var term = assertInstanceOf(WorldPrerequisites.MoonPhase.class, parseOrThrow("""
                {"type": "nwyfre:moon_phase", "phases": [0]}"""));
        assertEquals(List.of(0), term.phases());
    }

    @Test
    void rejectsAnOutOfRangeMoonPhase() {
        assertTrue(parse("""
                {"type": "nwyfre:moon_phase", "phases": [8]}""").error().isPresent(),
                "there are only eight moon phases");
    }

    @Test
    void parsesWeather() {
        var term = assertInstanceOf(WorldPrerequisites.Weather.class, parseOrThrow("""
                {"type": "nwyfre:weather", "weather": "thunder"}"""));
        assertEquals(WorldPrerequisites.Weather.Kind.THUNDER, term.kind());
    }

    @Test
    void parsesDimension() {
        var term = assertInstanceOf(WorldPrerequisites.InDimension.class, parseOrThrow("""
                {"type": "nwyfre:dimension", "dimension": "minecraft:the_nether"}"""));
        assertEquals(Level.NETHER, term.dimension());
    }

    @Test
    void parsesElevation() {
        var term = assertInstanceOf(WorldPrerequisites.Elevation.class, parseOrThrow("""
                {"type": "nwyfre:elevation", "min": -64, "max": 0}"""));
        assertEquals(-64, term.min());
        assertEquals(0, term.max());
    }

    @Test
    void parsesBlockAtWithASingleBlockAndDefaultOffset() {
        var term = assertInstanceOf(WorldPrerequisites.BlockAt.class, parseOrThrow("""
                {"type": "nwyfre:block_at", "blocks": "minecraft:dirt"}"""));
        // The default has to be downward or "on dirt" would need an explicit offset every time.
        assertEquals(-1, term.offset().getY());
        assertEquals(0, term.offset().getX());
    }

    @Test
    void parsesBlockAtWithAnExplicitOffsetAndSeveralBlocks() {
        var term = assertInstanceOf(WorldPrerequisites.BlockAt.class, parseOrThrow("""
                {"type": "nwyfre:block_at", "offset": [0, 2, 0],
                 "blocks": ["minecraft:dirt", "minecraft:grass_block"]}"""));
        assertEquals(2, term.offset().getY());
    }

    @Test
    void parsesFluidAtWithDefaultOffsetAtTheAltar() {
        var term = assertInstanceOf(WorldPrerequisites.FluidAt.class, parseOrThrow("""
                {"type": "nwyfre:fluid_at", "fluids": "minecraft:water"}"""));
        // "In water" means at the altar, not below it.
        assertEquals(0, term.offset().getY());
    }

    @Test
    void parsesPackCondition() {
        assertInstanceOf(PackConditionPrerequisite.class, parseOrThrow("""
                {"type": "nwyfre:pack_condition",
                 "condition": {"type": "neoforge:mod_loaded", "modid": "nwyfre"}}"""));
    }

    @Test
    void parsesNestedComposites() {
        RitualPrerequisite term = parseOrThrow("""
                {"type": "nwyfre:all_of", "terms": [
                   {"type": "nwyfre:stage", "stages": ["nwyfre:awakening"]},
                   {"type": "nwyfre:not", "term": {"type": "nwyfre:weather", "weather": "clear"}},
                   {"type": "nwyfre:any_of", "terms": [
                      {"type": "nwyfre:moon_phase", "phases": [0]},
                      {"type": "nwyfre:time_of_day", "time": "night"}]}]}""");

        RitualPrerequisite.AllOf all = assertInstanceOf(RitualPrerequisite.AllOf.class, term);
        assertEquals(3, all.terms().size());
        assertInstanceOf(RitualPrerequisite.Not.class, all.terms().get(1));
        RitualPrerequisite.AnyOf any = assertInstanceOf(RitualPrerequisite.AnyOf.class, all.terms().get(2));
        assertEquals(2, any.terms().size());
    }

    @Test
    void parsesEmptyComposites() {
        // These are meaningful, not degenerate: all_of passes and any_of fails.
        assertInstanceOf(RitualPrerequisite.AllOf.class,
                parseOrThrow("""
                {"type": "nwyfre:all_of", "terms": []}"""));
        assertInstanceOf(RitualPrerequisite.AnyOf.class,
                parseOrThrow("""
                {"type": "nwyfre:any_of", "terms": []}"""));
    }

    @Test
    void rejectsAnUnknownTermType() {
        DataResult<RitualPrerequisite> result = parse("""
                {"type": "nwyfre:definitely_not_a_term"}""");
        assertTrue(result.error().isPresent(),
                "an unrecognised term must fail loudly rather than be ignored as an open gate");
    }

    @Test
    void rejectsATermWithNoType() {
        assertTrue(parse("""
                {"stages": ["nwyfre:awakening"]}""").error().isPresent());
    }

    @Test
    void everyTermTypeRoundTrips() {
        assertRoundTrips("""
                {"type": "nwyfre:stage", "stages": ["nwyfre:awakening"], "mode": "any"}""");
        assertRoundTrips("""
                {"type": "nwyfre:time_of_day", "time": "dawn"}""");
        assertRoundTrips("""
                {"type": "nwyfre:time_of_day", "min": 100, "max": 200}""");
        assertRoundTrips("""
                {"type": "nwyfre:moon_phase", "phases": [0, 4]}""");
        assertRoundTrips("""
                {"type": "nwyfre:weather", "weather": "rain"}""");
        assertRoundTrips("""
                {"type": "nwyfre:dimension", "dimension": "minecraft:overworld"}""");
        assertRoundTrips("""
                {"type": "nwyfre:elevation", "min": 0, "max": 128}""");
        assertRoundTrips("""
                {"type": "nwyfre:block_at", "offset": [1, -1, 0], "blocks": "minecraft:dirt"}""");
        assertRoundTrips("""
                {"type": "nwyfre:fluid_at", "fluids": "minecraft:lava"}""");
        assertRoundTrips("""
                {"type": "nwyfre:all_of", "terms": [{"type": "nwyfre:weather", "weather": "clear"}]}""");
        assertRoundTrips("""
                {"type": "nwyfre:any_of", "terms": [{"type": "nwyfre:weather", "weather": "clear"}]}""");
        assertRoundTrips("""
                {"type": "nwyfre:not", "term": {"type": "nwyfre:weather", "weather": "clear"}}""");
    }
}
