package com.nwyfre.nwyfre;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nwyfre.nwyfre.ritual.Element;
import com.nwyfre.nwyfre.ritual.RitualEffect;
import com.nwyfre.nwyfre.ritual.effect.WorldEffects;
import com.nwyfre.nwyfre.ritual.prereq.RitualPrerequisite;
import com.nwyfre.nwyfre.ritual.prereq.StagePrerequisite;
import com.nwyfre.nwyfre.ritual.prereq.WorldPrerequisites;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;

/**
 * Lints that every translation key the ritual system asks for actually exists.
 *
 * <p>A missing key does not crash or warn: the player simply sees
 * {@code nwyfre.ritual.prereq.stage} in their chat. That makes it exactly the kind of defect that
 * ships, and the ritual system leans heavily on translated text -- every prerequisite, effect and
 * diagnostic line is a key.
 *
 * <p>Keys are gathered by calling {@code describe()} on <strong>real instances</strong> and by
 * walking the actual enums, rather than by pattern-matching the source. That tests the code path a
 * player will hit (CLAUDE.md: test real code paths), and it means adding a new enum constant or
 * effect without its key fails here immediately.
 *
 * <p>Structural, not content: it asserts keys exist, never what they say, so rewording any message
 * is free.
 */
class LangKeyCoverageTest {

    private static Set<String> langKeys;

    @BeforeAll
    static void readLanguageFile() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        // Read as a stream: mod resources can live in a virtual filesystem, so turning the
        // resource URL into a Path is not safe in this harness.
        try (var stream = LangKeyCoverageTest.class
                .getResourceAsStream("/assets/nwyfre/lang/en_us.json")) {
            assertNotNull(stream, "en_us.json should be on the test classpath");
            JsonObject json = JsonParser
                    .parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            langKeys = json.keySet();
        }
    }

    /** Pulls the key out of a translatable component, or fails if it is not translatable. */
    private static String keyOf(Component component) {
        assertTrue(component.getContents() instanceof TranslatableContents,
                "expected a translatable component, got: " + component);
        return ((TranslatableContents) component.getContents()).getKey();
    }

    private static void assertKeysExist(List<String> keys) {
        List<String> missing = keys.stream().distinct().filter(key -> !langKeys.contains(key)).toList();
        assertTrue(missing.isEmpty(), "missing from en_us.json: " + missing);
    }

    // ---- keys taken from real objects --------------------------------------------------------

    @Test
    void everyPrerequisiteTermDescribesItselfWithAKnownKey() {
        List<RitualPrerequisite> terms = new ArrayList<>(List.of(
                new StagePrerequisite(List.of(ResourceLocation.withDefaultNamespace("x")),
                        StagePrerequisite.Mode.ALL),
                new WorldPrerequisites.MoonPhase(List.of(0)),
                new WorldPrerequisites.InDimension(Level.OVERWORLD),
                new WorldPrerequisites.Elevation(0, 10),
                new WorldPrerequisites.Elevation(Integer.MIN_VALUE, 10),
                new WorldPrerequisites.Elevation(0, Integer.MAX_VALUE),
                new WorldPrerequisites.BlockAt(new Vec3i(0, -1, 0),
                        net.minecraft.core.HolderSet.direct()),
                new WorldPrerequisites.FluidAt(Vec3i.ZERO, net.minecraft.core.HolderSet.direct()),
                new com.nwyfre.nwyfre.ritual.prereq.PackConditionPrerequisite(
                        net.neoforged.neoforge.common.conditions.TrueCondition.INSTANCE),
                new RitualPrerequisite.AnyOf(List.of()),
                new RitualPrerequisite.Not(new WorldPrerequisites.Elevation(0, 1))));

        // Every named phase and weather kind, so a new constant cannot slip through untranslated.
        for (WorldPrerequisites.TimeOfDay.Phase phase : WorldPrerequisites.TimeOfDay.Phase.values()) {
            terms.add(new WorldPrerequisites.TimeOfDay(phase, 0, 100));
        }
        for (WorldPrerequisites.Weather.Kind kind : WorldPrerequisites.Weather.Kind.values()) {
            terms.add(new WorldPrerequisites.Weather(kind));
        }

        assertKeysExist(terms.stream().map(term -> keyOf(term.describe())).toList());
    }

    @Test
    void everyEffectDescribesItselfWithAKnownKey() {
        List<RitualEffect> effects = new ArrayList<>(List.of(
                new WorldEffects.RunCommand(List.of("say hi"))));

        for (WorldEffects.SetTime.Target target : WorldEffects.SetTime.Target.values()) {
            effects.add(new WorldEffects.SetTime(target, 0));
        }
        for (WorldEffects.SetWeather.Kind kind : WorldEffects.SetWeather.Kind.values()) {
            effects.add(new WorldEffects.SetWeather(kind, 100));
        }

        assertKeysExist(effects.stream().map(effect -> keyOf(effect.describe())).toList());
    }

    @Test
    void everyMoonPhaseHasAName() {
        // Vanilla has eight, and MoonPhase renders each one by number.
        List<String> keys = new ArrayList<>();
        for (int phase = 0; phase < 8; phase++) {
            keys.add("nwyfre.moon_phase." + phase);
        }
        assertKeysExist(keys);
    }

    @Test
    void everyDirectionAnElementCanFaceHasAName() {
        // Element.displayNameWithDirection renders the direction, and a pack may invent an element
        // facing up or down, so all six need to be translatable.
        assertKeysExist(List.of(Direction.values()).stream()
                .map(direction -> "nwyfre.direction." + direction.getSerializedName())
                .toList());
    }

    @Test
    void elementDisplayUsesAKnownKey() {
        Element element = new Element(0, Direction.NORTH);
        ResourceKey<Element> key = ResourceKey.create(
                com.nwyfre.nwyfre.ritual.pattern.RitualPattern.ELEMENT_REGISTRY,
                Nwyfre.id("earth"));

        assertKeysExist(List.of(
                keyOf(element.displayNameWithDirection(key)),
                keyOf(Element.displayName(key))));
    }

    // ---- keys that are only referenced by name ----------------------------------------------

    @Test
    void everyDiagnosticLineHasAKey() {
        // These are the only keys not reachable from an object, so they are listed explicitly.
        // DiagnosticReport.Rung is the checklist: one message family per rung.
        assertKeysExist(List.of(
                "nwyfre.ritual.diag.nothing_close",
                "nwyfre.ritual.diag.pattern_incomplete",
                "nwyfre.ritual.diag.no_bowls",
                "nwyfre.ritual.diag.wrong_activation",
                "nwyfre.ritual.diag.activation_option",
                "nwyfre.ritual.diag.missing_offerings",
                "nwyfre.ritual.diag.offering_line",
                "nwyfre.ritual.diag.unwanted_offering",
                "nwyfre.ritual.diag.something",
                "nwyfre.ritual.diag.prerequisites"));
    }

    @Test
    void everyRegisteredBlockHasAName() {
        // Datagen does not produce the language file, so a new block ships untranslated unless
        // somebody remembers. This is that somebody.
        assertKeysExist(List.of(
                "block.nwyfre.ritual_altar",
                "block.nwyfre.ritual_bowl",
                "block.nwyfre.chalk_glyph",
                "itemGroup.nwyfre.main"));
    }

    @Test
    void everyShippedElementHasAName() {
        assertKeysExist(List.of("earth", "air", "fire", "water").stream()
                .map(element -> "element.nwyfre." + element)
                .toList());
    }

    @Test
    void theElementWithDirectionFormatExists() {
        assertKeysExist(List.of("nwyfre.ritual.element_with_direction"));
    }
}
