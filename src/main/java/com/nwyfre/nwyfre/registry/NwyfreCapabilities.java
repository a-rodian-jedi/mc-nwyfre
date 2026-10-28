package com.nwyfre.nwyfre.registry;

import com.nwyfre.nwyfre.Nwyfre;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Item-handler capabilities for the ritual blocks.
 *
 * <p>This is a legitimate capability use: a behaviour lookup, not persistent data (CLAUDE.md).
 * Exposing the handlers is what lets hoppers fill bowls, lets automation drop an activation item
 * into an altar, and lets the augment scan read any nearby container through one interface.
 */
@EventBusSubscriber(modid = Nwyfre.MODID)
public final class NwyfreCapabilities {

    private NwyfreCapabilities() {
    }

    @SubscribeEvent
    static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                NwyfreBlockEntities.RITUAL_ALTAR.get(),
                (blockEntity, side) -> blockEntity.getItemHandler());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                NwyfreBlockEntities.RITUAL_BOWL.get(),
                (blockEntity, side) -> blockEntity.getItemHandler());
    }
}
