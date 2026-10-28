package com.nwyfre.nwyfre;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Lints that every sprite a Nwyfre particle definition names actually exists.
 *
 * <p>Exists because of a real bug: {@code ritual_marker} referenced
 * {@code minecraft:end_rod}, which is not a sprite -- vanilla's end-rod particle is drawn from the
 * {@code glitter_*} set. A particle pointing at a missing sprite does not warn; it renders as the
 * black-and-magenta checkerboard, and the one place it showed up was the diagnostics, which is
 * precisely where a player most needs to trust what they are seeing.
 *
 * <p>Sprite <em>existence</em> is checked by {@code NwyfreParticleDefinitionProvider} at datagen
 * time, which is the only layer that can see vanilla textures. What is checked here is the half a
 * headless test can reach: that every particle Nwyfre registers has a definition at all, and that
 * the definition names at least one sprite. A registered particle with no definition is
 * unrenderable, and nothing warns about that either.
 *
 * <p>Structural, not content: never which sprites were chosen.
 */
class ParticleDefinitionTest {

    /** Every particle Nwyfre registers, which must each have a definition file. */
    private static final List<String> PARTICLES =
            List.of("ritual_waiting", "ritual_marker", "offering_consumed");

    private static JsonObject definition(String name) {
        String path = "/assets/nwyfre/particles/" + name + ".json";
        try (var stream = ParticleDefinitionTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "missing particle definition: " + path
                    + " -- registering a ParticleType without one leaves it unrenderable");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("could not read " + path, exception);
        }
    }

    @Test
    void everyParticleHasADefinitionWithAtLeastOneSprite() {
        for (String particle : PARTICLES) {
            JsonArray textures = definition(particle).getAsJsonArray("textures");
            assertNotNull(textures, particle + " has no textures array");
            assertFalse(textures.isEmpty(), particle + " lists no sprites");
        }
    }

    @Test
    void everyRegisteredParticleIsListedHere() {
        // Keeps this lint honest: adding a particle to the registry without adding it to the
        // generator and to this list would leave the new one unchecked.
        assertEquals(
                com.nwyfre.nwyfre.datagen.NwyfreParticleDefinitionProvider.particleNames(),
                PARTICLES,
                "the generator and this lint must cover the same particles");
    }
}
