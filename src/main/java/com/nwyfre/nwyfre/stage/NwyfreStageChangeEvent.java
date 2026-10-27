package com.nwyfre.nwyfre.stage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired on the NeoForge game bus just before a player gains or loses a stage, and
 * <strong>cancellable</strong> so KubeJS scripts and other mods can veto a progression change.
 *
 * <p>Fired server-side only, and only when the change would actually alter the player's stage set
 * -- re-granting a stage the player already has fires nothing. A cancelled event leaves the stage
 * set untouched and causes the originating call to report {@code false}.
 */
public class NwyfreStageChangeEvent extends Event implements ICancellableEvent {

    public enum Action {
        GRANT,
        REVOKE
    }

    private final ServerPlayer player;
    private final ResourceLocation stage;
    private final Action action;

    public NwyfreStageChangeEvent(ServerPlayer player, ResourceLocation stage, Action action) {
        this.player = player;
        this.stage = stage;
        this.action = action;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ResourceLocation getStage() {
        return stage;
    }

    public Action getAction() {
        return action;
    }

    public boolean isGrant() {
        return action == Action.GRANT;
    }
}
