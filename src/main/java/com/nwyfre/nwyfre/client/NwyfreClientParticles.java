package com.nwyfre.nwyfre.client;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.registry.NwyfreParticles;

import net.minecraft.client.particle.EndRodParticle;
import net.minecraft.client.particle.FlameParticle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Client-side providers for the ritual particles.
 *
 * <p>Registering the {@code ParticleType} server-side is only half the job: without a provider the
 * client cannot render it, and each type also needs a definition under
 * {@code assets/nwyfre/particles/} naming its sprites. Both halves use vanilla sprites for v0, for
 * the same reason the block models do -- pointing at art that does not exist yet fills the log
 * with load errors.
 *
 * <p>Client-only, and never referenced from common code (CLAUDE.md).
 */
@EventBusSubscriber(modid = Nwyfre.MODID, value = Dist.CLIENT)
public final class NwyfreClientParticles {

    private NwyfreClientParticles() {
    }

    @SubscribeEvent
    static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(NwyfreParticles.RITUAL_WAITING.get(), FlameParticle.Provider::new);
        event.registerSpriteSet(NwyfreParticles.RITUAL_MARKER.get(), EndRodParticle.Provider::new);
        event.registerSpriteSet(NwyfreParticles.OFFERING_CONSUMED.get(), FlameParticle.Provider::new);
    }
}
