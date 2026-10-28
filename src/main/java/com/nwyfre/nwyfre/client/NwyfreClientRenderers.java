package com.nwyfre.nwyfre.client;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.registry.NwyfreBlockEntities;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Binds the block entity renderers.
 *
 * <p>Without these the altar and the bowls hold their items invisibly: the data is there and Jade
 * reports it, but nothing draws it. A player cannot lay out a circle they cannot see.
 *
 * <p>Client-only, and never referenced from common code (CLAUDE.md).
 */
@EventBusSubscriber(modid = Nwyfre.MODID, value = Dist.CLIENT)
public final class NwyfreClientRenderers {

    private NwyfreClientRenderers() {
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(NwyfreBlockEntities.RITUAL_BOWL.get(),
                HeldOfferingRenderer.bowl());
        event.registerBlockEntityRenderer(NwyfreBlockEntities.RITUAL_ALTAR.get(),
                HeldOfferingRenderer.altar());
    }
}
