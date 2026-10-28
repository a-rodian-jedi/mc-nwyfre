package com.nwyfre.nwyfre.registry;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.ritual.prereq.PackConditionPrerequisite;
import com.nwyfre.nwyfre.ritual.prereq.RitualPrerequisite;
import com.nwyfre.nwyfre.ritual.prereq.StagePrerequisite;
import com.nwyfre.nwyfre.ritual.prereq.WorldPrerequisites;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry of prerequisite term types.
 *
 * <p>A real registry rather than a fixed enum, so another mod -- or a KubeJS startup script via the
 * registry event -- can contribute a new kind of gate. The {@code type} field of a prerequisite in
 * JSON is a key into here, and {@link RitualPrerequisite#CODEC} dispatches on it.
 */
public final class NwyfrePrerequisites {

    public static final ResourceKey<Registry<MapCodec<? extends RitualPrerequisite>>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(Nwyfre.id("ritual_prerequisite"));

    public static final DeferredRegister<MapCodec<? extends RitualPrerequisite>> PREREQUISITES =
            DeferredRegister.create(REGISTRY_KEY, Nwyfre.MODID);

    public static final Registry<MapCodec<? extends RitualPrerequisite>> REGISTRY =
            PREREQUISITES.makeRegistry(builder -> {
            });

    // Composites.
    public static final Supplier<MapCodec<? extends RitualPrerequisite>> ALL_OF =
            register("all_of", RitualPrerequisite.AllOf.CODEC);
    public static final Supplier<MapCodec<? extends RitualPrerequisite>> ANY_OF =
            register("any_of", RitualPrerequisite.AnyOf.CODEC);
    public static final Supplier<MapCodec<? extends RitualPrerequisite>> NOT =
            register("not", RitualPrerequisite.Not.CODEC);

    // Progression.
    public static final Supplier<MapCodec<? extends RitualPrerequisite>> STAGE =
            register("stage", StagePrerequisite.CODEC);

    // When.
    public static final Supplier<MapCodec<? extends RitualPrerequisite>> TIME_OF_DAY =
            register("time_of_day", WorldPrerequisites.TimeOfDay.CODEC);
    public static final Supplier<MapCodec<? extends RitualPrerequisite>> MOON_PHASE =
            register("moon_phase", WorldPrerequisites.MoonPhase.CODEC);
    public static final Supplier<MapCodec<? extends RitualPrerequisite>> WEATHER =
            register("weather", WorldPrerequisites.Weather.CODEC);

    // Where.
    public static final Supplier<MapCodec<? extends RitualPrerequisite>> BIOME =
            register("biome", WorldPrerequisites.InBiome.CODEC);
    public static final Supplier<MapCodec<? extends RitualPrerequisite>> DIMENSION =
            register("dimension", WorldPrerequisites.InDimension.CODEC);
    public static final Supplier<MapCodec<? extends RitualPrerequisite>> ELEVATION =
            register("elevation", WorldPrerequisites.Elevation.CODEC);
    public static final Supplier<MapCodec<? extends RitualPrerequisite>> BLOCK_AT =
            register("block_at", WorldPrerequisites.BlockAt.CODEC);
    public static final Supplier<MapCodec<? extends RitualPrerequisite>> FLUID_AT =
            register("fluid_at", WorldPrerequisites.FluidAt.CODEC);

    // Escape hatch.
    public static final Supplier<MapCodec<? extends RitualPrerequisite>> PACK_CONDITION =
            register("pack_condition", PackConditionPrerequisite.CODEC);

    private static Supplier<MapCodec<? extends RitualPrerequisite>> register(
            String name, MapCodec<? extends RitualPrerequisite> codec) {
        return PREREQUISITES.register(name, () -> codec);
    }

    /** The id a term type is addressed by in JSON, for error messages and tests. */
    public static ResourceLocation idOf(MapCodec<? extends RitualPrerequisite> codec) {
        return REGISTRY.getKey(codec);
    }

    private NwyfrePrerequisites() {
    }
}
