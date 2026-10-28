package com.nwyfre.nwyfre.ritual;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Ritual lifecycle events, fired on the NeoForge <strong>game bus</strong>, server-side only.
 *
 * <p>Follows the conventions in CLAUDE.md: {@link Starting} is cancellable because a pack could
 * plausibly veto a ritual, and a cancelled event leaves state untouched and makes the originating
 * call report {@code false}. {@link Completed} and {@link Stopped} are informational -- by then the
 * decision has been made, and a pack that wants to prevent an outcome vetoes the start.
 */
public abstract class NwyfreRitualEvent extends Event {

    private final ServerLevel level;
    private final BlockPos altarPos;
    private final ResourceLocation recipeId;
    private final Optional<ServerPlayer> caster;

    protected NwyfreRitualEvent(ServerLevel level, BlockPos altarPos, ResourceLocation recipeId,
            @Nullable ServerPlayer caster) {
        this.level = level;
        this.altarPos = altarPos;
        this.recipeId = recipeId;
        this.caster = Optional.ofNullable(caster);
    }

    public ServerLevel getLevel() {
        return level;
    }

    public BlockPos getAltarPos() {
        return altarPos;
    }

    public ResourceLocation getRecipeId() {
        return recipeId;
    }

    /** Empty when automation started the ritual rather than a player. */
    public Optional<ServerPlayer> getCaster() {
        return caster;
    }

    /**
     * Fired once a ritual has matched and passed its prerequisites, just before any state changes.
     *
     * <p><strong>Cancellable.</strong> Cancelling consumes nothing, starts nothing and makes the
     * activation report failure, so a vetoed ritual is indistinguishable from one that never
     * matched.
     *
     * @param potency the augment strength about to be used, so a listener can see what it is vetoing
     */
    public static class Starting extends NwyfreRitualEvent implements ICancellableEvent {

        private final int potency;

        public Starting(ServerLevel level, BlockPos altarPos, ResourceLocation recipeId,
                @Nullable ServerPlayer caster, int potency) {
            super(level, altarPos, recipeId, caster);
            this.potency = potency;
        }

        public int getPotency() {
            return potency;
        }
    }

    /** Fired after a ritual has run its course and its effect has been applied. */
    public static class Completed extends NwyfreRitualEvent {

        private final boolean persistent;

        public Completed(ServerLevel level, BlockPos altarPos, ResourceLocation recipeId,
                @Nullable ServerPlayer caster, boolean persistent) {
            super(level, altarPos, recipeId, caster);
            this.persistent = persistent;
        }

        /** True when the ritual left a lit candle behind rather than finishing outright. */
        public boolean isPersistent() {
            return persistent;
        }
    }

    /** Fired when a ritual ends without completing, or when a persistent one burns out. */
    public static class Stopped extends NwyfreRitualEvent {

        /** Why a ritual ended. Drives the message the player is shown. */
        public enum Reason {
            /** The circle, offerings or sustained prerequisites stopped holding. */
            INTERRUPTED,
            /** A player deliberately called it off. */
            CANCELLED,
            /** A persistent ritual burned its candle down. */
            EXPIRED
        }

        private final Reason reason;

        public Stopped(ServerLevel level, BlockPos altarPos, ResourceLocation recipeId,
                @Nullable ServerPlayer caster, Reason reason) {
            super(level, altarPos, recipeId, caster);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }
}
