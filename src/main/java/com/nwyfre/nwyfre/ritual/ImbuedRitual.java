package com.nwyfre.nwyfre.ritual;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * A persistent ritual bound into a candle.
 *
 * <p>A Data Component rather than block entity state, which is what makes the imbued candle a real
 * <em>item</em>: it can be picked up, stored, and carried to a different altar, and it keeps its
 * remaining burn time while it travels. {@code specs/rituals.md} asks for a candle that is
 * "imbued or enchanted with the ritual", and a component is exactly that.
 *
 * @param recipeId the ritual bound into the candle
 * @param burnTicksRemaining how much life is left; counts down only while the candle is lit
 * @param potency the augment strength captured when the candle was made, so relighting it elsewhere
 *                cannot be used to re-roll a better augment
 */
public record ImbuedRitual(ResourceLocation recipeId, int burnTicksRemaining, int potency) {

    public static final Codec<ImbuedRitual> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("recipe").forGetter(ImbuedRitual::recipeId),
                    Codec.INT.fieldOf("burn_ticks").forGetter(ImbuedRitual::burnTicksRemaining),
                    Codec.INT.optionalFieldOf("potency", 0).forGetter(ImbuedRitual::potency))
            .apply(instance, ImbuedRitual::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ImbuedRitual> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, ImbuedRitual::recipeId,
                    ByteBufCodecs.VAR_INT, ImbuedRitual::burnTicksRemaining,
                    ByteBufCodecs.VAR_INT, ImbuedRitual::potency,
                    ImbuedRitual::new);

    /** One beat burned away. Never goes below zero, so a long interval cannot underflow. */
    public ImbuedRitual burned(int ticks) {
        return new ImbuedRitual(recipeId, Math.max(0, burnTicksRemaining - ticks), potency);
    }

    public boolean spent() {
        return burnTicksRemaining <= 0;
    }
}
