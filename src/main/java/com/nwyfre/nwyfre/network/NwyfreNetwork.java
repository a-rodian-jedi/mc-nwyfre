package com.nwyfre.nwyfre.network;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.client.ClientStageSync;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.nwyfre.nwyfre.stage.Stages;

@EventBusSubscriber(modid = Nwyfre.MODID)
public final class NwyfreNetwork {

    private NwyfreNetwork() {
    }

    @SubscribeEvent
    static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // ClientStageSync is only touched inside the handler body, which never runs on a
        // dedicated server, so no client-only class is loaded there.
        registrar.playToClient(
                StageSyncPayload.TYPE,
                StageSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientStageSync.accept(payload)));
    }

    /**
     * Pushes the player's current stages to their client.
     *
     * <p>No-ops for a {@link ServerPlayer} with no live connection. Those are real and common in
     * modded play -- machine-driven "fake players" are full ServerPlayer instances that were never
     * placed on a connection -- and there is nothing to sync to, so attempting a send would throw
     * rather than do anything useful.
     */
    public static void syncStages(ServerPlayer player) {
        if (player.connection == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, StageSyncPayload.of(Stages.get(player)));
    }
}
