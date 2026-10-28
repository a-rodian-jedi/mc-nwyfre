package com.nwyfre.nwyfre.ritual;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.nwyfre.nwyfre.registry.NwyfreRitualEffects;

import net.minecraft.network.chat.Component;

/**
 * What a ritual actually does.
 *
 * <p>This is the half of the Occultism split that stays in Java: the recipe data picks an effect by
 * id and supplies its config, and the effect supplies the behaviour. Adding a new <em>kind</em> of
 * outcome needs Java; adding a new ritual does not.
 *
 * <p>The codec is a dispatch over {@link NwyfreRitualEffects}, so each effect gets its own
 * type-checked config with real error messages rather than a bag of loosely-related fields.
 *
 * <p>Effects read strength from {@link RitualContext#potency()} rather than having it baked in,
 * which is what lets an augment say {@code "potency": 2} and mean something without the pack author
 * re-declaring the whole effect.
 */
public interface RitualEffect {

    Codec<RitualEffect> CODEC = Codec.lazyInitialized(() -> NwyfreRitualEffects.REGISTRY
            .byNameCodec()
            .dispatch("type", RitualEffect::codec, mapCodec -> mapCodec));

    /** The entry in {@link NwyfreRitualEffects} this effect decodes from. */
    MapCodec<? extends RitualEffect> codec();

    /**
     * The payoff of a one-shot ritual, or one beat of a persistent one.
     *
     * <p>Called on the server only, once the ritual has run its course and every requirement has
     * been re-checked.
     */
    void apply(RitualContext context);

    /**
     * One beat of a persistent ritual, fired every {@code persistentTickInterval} while the candle
     * is lit.
     *
     * <p>Defaults to {@link #apply}, which is right for anything idempotent. An effect whose
     * one-shot form would be wrong to repeat (handing out an item, say) overrides this.
     */
    default void tickPersistent(RitualContext context) {
        apply(context);
    }

    /**
     * Flavour while a one-shot ritual is still running. Does nothing by default.
     *
     * <p>Not for gameplay: anything that changes the world belongs in {@link #apply}, so that an
     * interrupted ritual leaves nothing behind.
     */
    default void tickRunning(RitualContext context) {
    }

    /**
     * Whether this effect is sensible as a persistent ritual.
     *
     * <p>Checked when a recipe loads rather than when it fires, so a pack author hears about
     * "a candle that repeatedly grants an item" at load time instead of discovering it in play.
     */
    default boolean supportsPersistent() {
        return true;
    }

    /** One line describing the outcome, for JEI and the guidebook. */
    Component describe();
}
