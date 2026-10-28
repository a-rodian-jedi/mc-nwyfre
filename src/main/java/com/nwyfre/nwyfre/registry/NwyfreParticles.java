package com.nwyfre.nwyfre.registry;

import java.util.function.Supplier;

import com.nwyfre.nwyfre.Nwyfre;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Ritual particles. All three are plain {@link SimpleParticleType}s driven server-side via
 * {@code ServerLevel.sendParticles}, so the only client-side work is binding a sprite set.
 */
public final class NwyfreParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Nwyfre.MODID);

    /** Drifts over an altar that is waiting on a sacrifice or an item use. */
    public static final Supplier<SimpleParticleType> RITUAL_WAITING =
            PARTICLE_TYPES.register("ritual_waiting", () -> new SimpleParticleType(false));

    /** Marks a block position the player is missing. Used only by the diagnostics. */
    public static final Supplier<SimpleParticleType> RITUAL_MARKER =
            PARTICLE_TYPES.register("ritual_marker", () -> new SimpleParticleType(false));

    /** Puffs from a bowl whose offering was just taken. */
    public static final Supplier<SimpleParticleType> OFFERING_CONSUMED =
            PARTICLE_TYPES.register("offering_consumed", () -> new SimpleParticleType(false));

    private NwyfreParticles() {
    }
}
