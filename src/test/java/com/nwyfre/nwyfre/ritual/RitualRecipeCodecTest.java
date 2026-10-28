package com.nwyfre.nwyfre.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.nwyfre.nwyfre.ritual.effect.ItemEffects;
import com.nwyfre.nwyfre.ritual.effect.WorldEffects;
import com.nwyfre.nwyfre.ritual.pattern.RitualPattern;
import com.nwyfre.nwyfre.ritual.prereq.RitualPrerequisite;
import com.nwyfre.nwyfre.ritual.prereq.StagePrerequisite;

import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The ritual recipe file format: the one artefact a pack author actually writes.
 *
 * <p>Includes the {@code StreamCodec} round trip CLAUDE.md requires. The recipe is synced to
 * clients for JEI, and a stream codec whose field order drifted from the JSON one would desync
 * clients <em>silently</em> rather than throw -- which is why it is derived from the JSON codec
 * rather than hand-written, and why that is checked here.
 *
 * <p>As with the prerequisite tests, tag-form ingredients cannot resolve in a headless bootstrap;
 * the shipped recipes use them, so the server parsing those on boot covers that form.
 */
class RitualRecipeCodecTest {

    private static RegistryAccess registries;
    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        ops = RegistryOps.create(JsonOps.INSTANCE, registries);
    }

    private static DataResult<RitualRecipe> parse(String json) {
        return RitualRecipe.CODEC.parse(ops, JsonParser.parseString(json));
    }

    private static RitualRecipe parseOrThrow(String json) {
        return parse(json).getOrThrow();
    }

    /** The smallest legal recipe: a pattern, something to start it with, and an outcome. */
    static RitualRecipe minimalRecipe() {
        return new RitualRecipe(
                new RitualRecipe.Placement(
                        ResourceKey.create(RitualPattern.REGISTRY,
                                ResourceLocation.fromNamespaceAndPath("nwyfre_test", "circle")),
                        Ingredient.of(new ItemStack(Items.CLOCK)),
                        Map.of()),
                new RitualRecipe.Timing(100, false, 24000),
                RitualDemands.NONE,
                Optional.empty(),
                false,
                new WorldEffects.SetTime(WorldEffects.SetTime.Target.SUNRISE, 0),
                List.of(),
                ItemStack.EMPTY);
    }


    /**
     * Compares two recipes for equivalence rather than equality.
     *
     * <p>{@link RitualRecipe} is a record, but it carries an {@link ItemStack} icon and
     * {@code ItemStack} does <strong>not</strong> override {@code equals} -- vanilla compares
     * stacks with {@code ItemStack.matches}. Record equality therefore falls back to identity for
     * that one field, so a round-tripped recipe is never {@code equals} to its original whenever an
     * icon is present. Everything else compares normally.
     */
    private static void assertEquivalent(RitualRecipe expected, RitualRecipe actual) {
        assertEquals(expected.placement().pattern(), actual.placement().pattern());
        assertEquals(expected.placement().offerings().keySet(), actual.placement().offerings().keySet());
        assertEquals(expected.timing(), actual.timing());
        assertEquals(expected.demands(), actual.demands());
        assertEquals(expected.prerequisites(), actual.prerequisites());
        assertEquals(expected.sustainPrerequisites(), actual.sustainPrerequisites());
        assertEquals(expected.effect(), actual.effect());
        assertEquals(expected.augments(), actual.augments());
        assertTrue(ItemStack.matches(expected.icon(), actual.icon()),
                "the icon should survive the round trip");
    }

    @Test
    void parsesTheMinimalRecipe() {
        RitualRecipe recipe = parseOrThrow("""
                {
                  "pattern": "nwyfre:lesser_circle",
                  "activation_item": {"item": "minecraft:clock"},
                  "effect": {"type": "nwyfre:set_time", "time": "sunrise"}
                }""");

        assertEquals("nwyfre:lesser_circle", recipe.placement().pattern().location().toString());
        assertEquals(100, recipe.timing().duration(), "duration should default to 100");
        assertFalse(recipe.timing().persistent(), "rituals should default to one-shot");
        assertTrue(recipe.placement().offerings().isEmpty());
        assertTrue(recipe.prerequisites().isEmpty());
        assertFalse(recipe.sustainPrerequisites(), "re-checking prerequisites must default to off");
        assertTrue(recipe.augments().isEmpty());
        assertInstanceOf(WorldEffects.SetTime.class, recipe.effect());
    }

    @Test
    void parsesOfferingsKeyedByElement() {
        RitualRecipe recipe = parseOrThrow("""
                {
                  "pattern": "nwyfre:lesser_circle",
                  "activation_item": {"item": "minecraft:clock"},
                  "offerings": {
                    "nwyfre:earth": {"item": "minecraft:wheat"},
                    "nwyfre:fire": {"item": "minecraft:coal"}
                  },
                  "effect": {"type": "nwyfre:set_weather", "weather": "clear"}
                }""");

        assertEquals(2, recipe.placement().offerings().size());
        assertTrue(recipe.offeringElements().stream()
                        .anyMatch(element -> element.location().getPath().equals("earth")),
                "offerings must stay keyed by element, not flattened into a list");
    }

    @Test
    void parsesTheFullForm() {
        RitualRecipe recipe = parseOrThrow("""
                {
                  "pattern": "nwyfre:lesser_circle",
                  "activation_item": {"item": "minecraft:candle"},
                  "offerings": {"nwyfre:water": {"item": "minecraft:glistering_melon_slice"}},
                  "duration": 200,
                  "persistent": true,
                  "burn_ticks": 12000,
                  "sustain_prerequisites": true,
                  "prerequisites": {
                    "type": "nwyfre:all_of",
                    "terms": [
                      {"type": "nwyfre:stage", "stages": ["nwyfre:awakening"]},
                      {"type": "nwyfre:time_of_day", "time": "night"}
                    ]
                  },
                  "sacrifice": {"tag": "minecraft:zombies", "display_name": "ritual.nwyfre.sacrifice.zombies"},
                  "item_to_use": {"item": "minecraft:experience_bottle"},
                  "effect": {"type": "nwyfre:area_effect", "effect": "minecraft:regeneration", "radius": 8},
                  "augments": [
                    {"requires": [{"item": "minecraft:diamond"}], "potency": 4}
                  ],
                  "icon": {"id": "minecraft:candle"}
                }""");

        assertEquals(200, recipe.timing().duration());
        assertTrue(recipe.timing().persistent());
        assertEquals(12000, recipe.timing().burnTicks());
        assertTrue(recipe.sustainPrerequisites());
        assertTrue(recipe.demands().requiresSacrifice());
        assertTrue(recipe.demands().requiresItemUse());
        assertEquals(1, recipe.augments().size());
        assertEquals(4, recipe.augments().getFirst().potency());
        assertInstanceOf(RitualPrerequisite.AllOf.class, recipe.prerequisites().orElseThrow());
    }

    @Test
    void rejectsARecipeWithNoEffect() {
        assertTrue(parse("""
                {
                  "pattern": "nwyfre:lesser_circle",
                  "activation_item": {"item": "minecraft:clock"}
                }""").error().isPresent(), "a ritual with no outcome is meaningless");
    }

    @Test
    void rejectsARecipeWithNoActivationItem() {
        assertTrue(parse("""
                {
                  "pattern": "nwyfre:lesser_circle",
                  "effect": {"type": "nwyfre:set_time", "time": "sunrise"}
                }""").error().isPresent(), "a ritual with nothing to start it could never fire");
    }

    @Test
    void rejectsAPersistentRitualWhoseEffectCannotRepeat() {
        // Caught at load rather than in play: a candle handing out an item every twenty ticks is
        // a pack author mistake, and the author should hear about it while editing the file.
        DataResult<RitualRecipe> result = parse("""
                {
                  "pattern": "nwyfre:lesser_circle",
                  "activation_item": {"item": "minecraft:candle"},
                  "persistent": true,
                  "effect": {"type": "nwyfre:give_item", "result": {"id": "minecraft:diamond"}}
                }""");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("persistent"),
                "the error should say what is wrong, got: " + result.error().orElseThrow().message());
    }

    @Test
    void rejectsAPersistentRitualWhoseAugmentEffectCannotRepeat() {
        // The augment can replace the effect, so it has to clear the same bar.
        assertTrue(parse("""
                {
                  "pattern": "nwyfre:lesser_circle",
                  "activation_item": {"item": "minecraft:candle"},
                  "persistent": true,
                  "effect": {"type": "nwyfre:area_effect", "effect": "minecraft:regeneration"},
                  "augments": [
                    {"requires": [{"item": "minecraft:diamond"}],
                     "effect": {"type": "nwyfre:summon", "entity": "minecraft:wolf"}}
                  ]
                }""").error().isPresent());
    }

    @Test
    void rejectsAnAugmentWithNoRequirements() {
        assertTrue(parse("""
                {
                  "pattern": "nwyfre:lesser_circle",
                  "activation_item": {"item": "minecraft:clock"},
                  "effect": {"type": "nwyfre:set_time", "time": "sunrise"},
                  "augments": [{"potency": 5}]
                }""").error().isPresent(), "an augment with no requires would match every ritual");
    }

    @Test
    void acceptsAPersistentAreaEffect() {
        // The spec example. Must not be caught by the persistence validation.
        RitualRecipe recipe = parseOrThrow("""
                {
                  "pattern": "nwyfre:lesser_circle",
                  "activation_item": {"item": "minecraft:candle"},
                  "persistent": true,
                  "effect": {"type": "nwyfre:area_effect", "effect": "minecraft:regeneration", "radius": 8}
                }""");

        assertTrue(recipe.effect().supportsPersistent());
    }

    @Test
    void jsonRoundTrips() {
        String json = """
                {
                  "pattern": "nwyfre:lesser_circle",
                  "activation_item": {"item": "minecraft:candle"},
                  "offerings": {"nwyfre:earth": {"item": "minecraft:wheat"}},
                  "duration": 150,
                  "prerequisites": {"type": "nwyfre:stage", "stages": ["nwyfre:awakening"], "mode": "any"},
                  "effect": {"type": "nwyfre:set_weather", "weather": "rain", "duration": 6000},
                  "augments": [{"requires": [{"item": "minecraft:diamond"}], "potency": 2}]
                }""";

        RitualRecipe first = parseOrThrow(json);
        JsonElement encoded = RitualRecipe.CODEC.encodeStart(ops, first).getOrThrow();
        RitualRecipe second = RitualRecipe.CODEC.parse(ops, encoded).getOrThrow();

        assertEquivalent(first, second);
    }

    @Test
    void streamCodecRoundTripsAndConsumesTheWholeBuffer() {
        // CLAUDE.md: a mismatched encode/decode pair desyncs clients silently rather than throwing,
        // so the buffer has to be drained exactly.
        RitualRecipe original = parseOrThrow("""
                {
                  "pattern": "nwyfre:lesser_circle",
                  "activation_item": {"item": "minecraft:candle"},
                  "offerings": {"nwyfre:fire": {"item": "minecraft:coal"}},
                  "duration": 200,
                  "persistent": true,
                  "burn_ticks": 9000,
                  "prerequisites": {"type": "nwyfre:moon_phase", "phases": [0]},
                  "sacrifice": {"tag": "minecraft:zombies", "display_name": "ritual.nwyfre.sacrifice.zombies"},
                  "effect": {"type": "nwyfre:area_effect", "effect": "minecraft:regeneration", "radius": 8},
                  "augments": [{"requires": [{"item": "minecraft:diamond"}], "potency": 4}],
                  "icon": {"id": "minecraft:candle"}
                }""");

        StreamCodec<RegistryFriendlyByteBuf, RitualRecipe> streamCodec =
                ByteBufCodecs.fromCodecWithRegistries(RitualRecipe.CODEC);

        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        streamCodec.encode(buffer, original);
        RitualRecipe decoded = streamCodec.decode(buffer);

        assertEquals(0, buffer.readableBytes(), "decode should consume exactly what encode wrote");
        assertEquivalent(original, decoded);
    }

    @Test
    void stagePrerequisiteSurvivesTheWireSoJeiCanHideOnIt() {
        // JEI hides a recipe whose stage terms are unmet, which only works if the term reaches
        // the client intact.
        RitualRecipe original = parseOrThrow("""
                {
                  "pattern": "nwyfre:lesser_circle",
                  "activation_item": {"item": "minecraft:clock"},
                  "prerequisites": {"type": "nwyfre:stage", "stages": ["nwyfre:awakening"]},
                  "effect": {"type": "nwyfre:set_time", "time": "sunrise"}
                }""");

        StreamCodec<RegistryFriendlyByteBuf, RitualRecipe> streamCodec =
                ByteBufCodecs.fromCodecWithRegistries(RitualRecipe.CODEC);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        streamCodec.encode(buffer, original);

        RitualRecipe decoded = streamCodec.decode(buffer);

        StagePrerequisite stage = assertInstanceOf(StagePrerequisite.class,
                decoded.prerequisites().orElseThrow());
        assertEquals(1, stage.stages().size());
    }

    @Test
    void durationScalesWithTheConfigMultiplier() {
        // Default multiplier is 1.0, so this also guards against the scaling silently zeroing out.
        assertEquals(100, new RitualRecipe.Timing(100, false, 24000).scaledDuration());
    }

    @Test
    void ticksPerOfferingLeavesABeatAtTheEnd() {
        // The +1 divisor is what stops the last offering vanishing at the same instant the ritual
        // completes, which would read as the offering being wasted.
        RitualRecipe twoOfferings = parseOrThrow("""
                {
                  "pattern": "nwyfre:lesser_circle",
                  "activation_item": {"item": "minecraft:clock"},
                  "offerings": {
                    "nwyfre:earth": {"item": "minecraft:wheat"},
                    "nwyfre:fire": {"item": "minecraft:coal"}
                  },
                  "duration": 300,
                  "effect": {"type": "nwyfre:set_time", "time": "sunrise"}
                }""");

        assertEquals(100.0F, twoOfferings.ticksPerOffering(), 0.001F);
    }

    @Test
    void parsesEveryEffectType() {
        // A rename in any effect codec shows up here rather than as a dead ritual in a modpack.
        assertInstanceOf(WorldEffects.SetTime.class, effectOf("""
                {"type": "nwyfre:set_time", "time": "midnight"}"""));
        assertInstanceOf(WorldEffects.SetWeather.class, effectOf("""
                {"type": "nwyfre:set_weather", "weather": "thunder"}"""));
        assertInstanceOf(WorldEffects.AreaEffect.class, effectOf("""
                {"type": "nwyfre:area_effect", "effect": "minecraft:regeneration"}"""));
        assertInstanceOf(WorldEffects.RunCommand.class, effectOf("""
                {"type": "nwyfre:run_command", "commands": "say hello"}"""));
        assertInstanceOf(ItemEffects.GiveItem.class, effectOf("""
                {"type": "nwyfre:give_item", "result": {"id": "minecraft:diamond", "count": 2}}"""));
        assertInstanceOf(ItemEffects.Summon.class, effectOf("""
                {"type": "nwyfre:summon", "entity": "minecraft:wolf", "count": 3, "tame": true}"""));
    }

    @Test
    void runCommandAcceptsASingleStringOrAList() {
        // Authors write one command far more often than several, so both forms are allowed.
        WorldEffects.RunCommand single = assertInstanceOf(WorldEffects.RunCommand.class, effectOf("""
                {"type": "nwyfre:run_command", "commands": "say one"}"""));
        WorldEffects.RunCommand many = assertInstanceOf(WorldEffects.RunCommand.class, effectOf("""
                {"type": "nwyfre:run_command", "commands": ["say one", "say two"]}"""));

        assertEquals(1, single.commands().size());
        assertEquals(2, many.commands().size());
    }

    @Test
    void rejectsAnUnknownEffectType() {
        assertTrue(parse("""
                {
                  "pattern": "nwyfre:lesser_circle",
                  "activation_item": {"item": "minecraft:clock"},
                  "effect": {"type": "nwyfre:definitely_not_an_effect"}
                }""").error().isPresent(),
                "an unrecognised effect must fail loudly rather than load as a ritual that does nothing");
    }

    private static RitualEffect effectOf(String effectJson) {
        return parseOrThrow("""
                {
                  "pattern": "nwyfre:lesser_circle",
                  "activation_item": {"item": "minecraft:clock"},
                  "effect": %s
                }""".formatted(effectJson)).effect();
    }
}
