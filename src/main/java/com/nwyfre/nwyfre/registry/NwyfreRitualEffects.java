package com.nwyfre.nwyfre.registry;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.ritual.RitualEffect;
import com.nwyfre.nwyfre.ritual.effect.ItemEffects;
import com.nwyfre.nwyfre.ritual.effect.WorldEffects;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry of ritual effect types.
 *
 * <p>A real registry, not a fixed enum, so another mod or a KubeJS startup script can contribute a
 * new kind of outcome. The {@code type} field of a recipe effect is a key into here.
 *
 * <p>v0 covers all five purposes named in the spec: summons, enchantment and item creation via
 * {@code give_item} and {@code summon}, weather control via {@code set_weather}, and world
 * interaction via {@code set_time}, {@code area_effect} and the {@code run_command} escape hatch.
 */
public final class NwyfreRitualEffects {

    public static final ResourceKey<Registry<MapCodec<? extends RitualEffect>>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(Nwyfre.id("ritual_effect"));

    public static final DeferredRegister<MapCodec<? extends RitualEffect>> EFFECTS =
            DeferredRegister.create(REGISTRY_KEY, Nwyfre.MODID);

    public static final Registry<MapCodec<? extends RitualEffect>> REGISTRY =
            EFFECTS.makeRegistry(builder -> {
            });

    public static final Supplier<MapCodec<? extends RitualEffect>> SET_TIME =
            register("set_time", WorldEffects.SetTime.CODEC);
    public static final Supplier<MapCodec<? extends RitualEffect>> SET_WEATHER =
            register("set_weather", WorldEffects.SetWeather.CODEC);
    public static final Supplier<MapCodec<? extends RitualEffect>> AREA_EFFECT =
            register("area_effect", WorldEffects.AreaEffect.CODEC);
    public static final Supplier<MapCodec<? extends RitualEffect>> RUN_COMMAND =
            register("run_command", WorldEffects.RunCommand.CODEC);
    public static final Supplier<MapCodec<? extends RitualEffect>> GIVE_ITEM =
            register("give_item", ItemEffects.GiveItem.CODEC);
    public static final Supplier<MapCodec<? extends RitualEffect>> SUMMON =
            register("summon", ItemEffects.Summon.CODEC);

    private static Supplier<MapCodec<? extends RitualEffect>> register(
            String name, MapCodec<? extends RitualEffect> codec) {
        return EFFECTS.register(name, () -> codec);
    }

    /** The id an effect type is addressed by in JSON, for error messages and tests. */
    public static ResourceLocation idOf(MapCodec<? extends RitualEffect> codec) {
        return REGISTRY.getKey(codec);
    }

    private NwyfreRitualEffects() {
    }
}
