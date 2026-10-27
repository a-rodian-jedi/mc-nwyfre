package com.nwyfre.nwyfre.stage;

import com.nwyfre.nwyfre.network.NwyfreNetwork;
import com.nwyfre.nwyfre.registry.NwyfreAttachments;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The entry point for reading and changing player game stages.
 *
 * <p>Everything that gates on progression -- recipe locks, item usage, JEI visibility, Patchouli
 * entries, InControl! rules -- should ask through here rather than touching the attachment.
 * Mutating calls are server-side and fire a cancellable {@link NwyfreStageChangeEvent}.
 */
public final class Stages {

    private Stages() {
    }

    /** The player's stage set. Never null; a player who has unlocked nothing gets an empty set. */
    public static PlayerStages get(Player player) {
        return player.getData(NwyfreAttachments.PLAYER_STAGES.get());
    }

    /** Safe on both sides, though the client only knows what has been synced to it. */
    public static boolean has(Player player, ResourceLocation stage) {
        return get(player).has(stage);
    }

    /**
     * Grants a stage.
     *
     * @return {@code true} if the player's stages actually changed. {@code false} if they already
     *         had the stage, or a listener cancelled {@link NwyfreStageChangeEvent}.
     */
    public static boolean grant(ServerPlayer player, ResourceLocation stage) {
        PlayerStages stages = get(player);
        if (stages.has(stage)) {
            return false;
        }
        if (NeoForge.EVENT_BUS.post(new NwyfreStageChangeEvent(player, stage, NwyfreStageChangeEvent.Action.GRANT))
                .isCanceled()) {
            return false;
        }
        stages.grant(stage);
        markChanged(player, stages);
        return true;
    }

    /**
     * Revokes a stage.
     *
     * @return {@code true} if the player's stages actually changed. {@code false} if they did not
     *         have the stage, or a listener cancelled {@link NwyfreStageChangeEvent}.
     */
    public static boolean revoke(ServerPlayer player, ResourceLocation stage) {
        PlayerStages stages = get(player);
        if (!stages.has(stage)) {
            return false;
        }
        if (NeoForge.EVENT_BUS.post(new NwyfreStageChangeEvent(player, stage, NwyfreStageChangeEvent.Action.REVOKE))
                .isCanceled()) {
            return false;
        }
        stages.revoke(stage);
        markChanged(player, stages);
        return true;
    }

    /**
     * Revokes every stage at once, firing one {@link NwyfreStageChangeEvent} per stage so listeners
     * still see each removal individually. Stages whose event is cancelled are kept.
     *
     * @return the number of stages actually removed.
     */
    public static int clear(ServerPlayer player) {
        int removed = 0;
        for (ResourceLocation stage : java.util.List.copyOf(get(player).view())) {
            if (revoke(player, stage)) {
                removed++;
            }
        }
        return removed;
    }

    /**
     * Writes the attachment back so NeoForge marks the holder dirty for saving, then mirrors the
     * new set to the player's client. The set is mutated in place, so this re-set is what
     * guarantees persistence rather than the mutation itself.
     */
    private static void markChanged(ServerPlayer player, PlayerStages stages) {
        player.setData(NwyfreAttachments.PLAYER_STAGES.get(), stages);
        NwyfreNetwork.syncStages(player);
    }
}
