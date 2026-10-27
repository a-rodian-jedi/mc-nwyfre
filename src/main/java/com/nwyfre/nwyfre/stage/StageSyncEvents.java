package com.nwyfre.nwyfre.stage;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.network.NwyfreNetwork;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Re-sends a player's stages at the points where a client's copy would otherwise be stale or
 * missing: first join, respawn, and dimension change. Changes made during play are pushed by
 * {@link Stages} as they happen.
 */
@EventBusSubscriber(modid = Nwyfre.MODID)
public final class StageSyncEvents {

    private StageSyncEvents() {
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NwyfreNetwork.syncStages(player);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NwyfreNetwork.syncStages(player);
        }
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NwyfreNetwork.syncStages(player);
        }
    }
}
