package com.nwyfre.nwyfre.datagen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nwyfre.nwyfre.Nwyfre;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Writes the particle definitions, and <strong>fails datagen if a sprite does not exist</strong>.
 *
 * <p>These were hand-written at first, and one of them referenced {@code minecraft:end_rod} -- which
 * is not a sprite; vanilla's end-rod particle is drawn from the {@code glitter_*} set. Nothing
 * warned. The particle simply rendered as the black-and-magenta checkerboard, and the one place it
 * appeared was the diagnostics, which is exactly where a player most needs to trust what they see.
 *
 * <p>Generating them puts the check where it can actually run. A headless unit test cannot see
 * vanilla textures, but datagen has the whole client resource tree via {@link ExistingFileHelper} --
 * the same mechanism that already validates every block model texture reference.
 */
public class NwyfreParticleDefinitionProvider implements DataProvider {

    /** Particle id to its sprite list, in draw order. */
    private static final Map<String, List<String>> PARTICLES = new LinkedHashMap<>();

    static {
        // Vanilla sprites throughout: v0 ships no art of its own, and pointing at textures that do
        // not exist yet is the very mistake this provider exists to catch.
        PARTICLES.put("ritual_waiting", List.of("minecraft:soul_fire_flame"));
        PARTICLES.put("ritual_marker", List.of(
                "minecraft:glitter_7", "minecraft:glitter_6", "minecraft:glitter_5",
                "minecraft:glitter_4", "minecraft:glitter_3", "minecraft:glitter_2",
                "minecraft:glitter_1", "minecraft:glitter_0"));
        PARTICLES.put("offering_consumed", List.of("minecraft:flame"));
    }

    private final PackOutput.PathProvider path;
    private final ExistingFileHelper existingFiles;

    public NwyfreParticleDefinitionProvider(PackOutput output, ExistingFileHelper existingFiles) {
        this.path = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "particles");
        this.existingFiles = existingFiles;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<String> missing = new ArrayList<>();
        List<CompletableFuture<?>> written = new ArrayList<>();

        PARTICLES.forEach((particle, sprites) -> {
            JsonArray textures = new JsonArray();
            for (String sprite : sprites) {
                ResourceLocation id = ResourceLocation.parse(sprite);
                if (!existingFiles.exists(id, PackType.CLIENT_RESOURCES, ".png", "textures/particle")) {
                    missing.add(particle + " -> " + sprite);
                }
                textures.add(sprite);
            }

            JsonObject definition = new JsonObject();
            definition.add("textures", textures);
            Path target = path.json(Nwyfre.id(particle));
            written.add(DataProvider.saveStable(cache, definition, target));
        });

        if (!missing.isEmpty()) {
            // Loud on purpose. A missing sprite is invisible at runtime, so this is the only
            // moment anybody will be told about it.
            throw new IllegalStateException(
                    "Particle definitions reference sprites that do not exist: " + missing);
        }

        return CompletableFuture.allOf(written.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Nwyfre particle definitions";
    }

    /** The particles this provider writes, so a test can assert the set is complete. */
    public static List<String> particleNames() {
        return List.copyOf(PARTICLES.keySet());
    }
}
