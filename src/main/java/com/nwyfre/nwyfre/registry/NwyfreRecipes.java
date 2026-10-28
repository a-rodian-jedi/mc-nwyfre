package com.nwyfre.nwyfre.registry;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.ritual.RitualRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.NotNull;

/**
 * The ritual recipe type and serializer.
 *
 * <p>Registering as a vanilla recipe type is what buys datapack loading, {@code /reload} and client
 * sync for free, and it is also what makes {@code ServerEvents.recipes} in KubeJS work with no
 * further integration code.
 */
public final class NwyfreRecipes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, Nwyfre.MODID);

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Nwyfre.MODID);

    public static final Supplier<RecipeType<RitualRecipe>> RITUAL_TYPE =
            RECIPE_TYPES.register("ritual", () -> RecipeType.simple(Nwyfre.id("ritual")));

    public static final Supplier<RecipeSerializer<RitualRecipe>> RITUAL_SERIALIZER =
            RECIPE_SERIALIZERS.register("ritual", RitualSerializer::new);

    private NwyfreRecipes() {
    }

    /**
     * Streams the whole recipe to clients.
     *
     * <p>Built from the JSON codec rather than written by hand. A ritual recipe is large and
     * nested, and a hand-rolled stream codec whose field order drifted from the JSON one would
     * desync clients silently rather than throw -- the exact failure mode CLAUDE.md calls out.
     * Deriving it means there is only one description of the format to keep correct.
     */
    public static class RitualSerializer implements RecipeSerializer<RitualRecipe> {

        private static final StreamCodec<RegistryFriendlyByteBuf, RitualRecipe> STREAM_CODEC =
                net.minecraft.network.codec.ByteBufCodecs.fromCodecWithRegistries(RitualRecipe.CODEC);

        @Override
        public @NotNull MapCodec<RitualRecipe> codec() {
            return RitualRecipe.MAP_CODEC;
        }

        @Override
        public @NotNull StreamCodec<RegistryFriendlyByteBuf, RitualRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
