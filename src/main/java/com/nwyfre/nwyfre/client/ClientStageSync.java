package com.nwyfre.nwyfre.client;

import com.nwyfre.nwyfre.network.StageSyncPayload;
import com.nwyfre.nwyfre.registry.NwyfreAttachments;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Applies a synced stage set onto the local player.
 *
 * <p>Client-only: must never be loaded on a dedicated server. It is referenced solely from inside
 * the network handler body in {@code NwyfreNetwork}, which only executes clientside.
 */
public final class ClientStageSync {

    private ClientStageSync() {
    }

    public static void accept(StageSyncPayload payload) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            // Payload arrived before the local player existed; the post-login sync will resend.
            return;
        }
        player.setData(NwyfreAttachments.PLAYER_STAGES.get(), payload.toPlayerStages());
    }
}
