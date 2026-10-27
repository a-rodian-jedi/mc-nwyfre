package com.nwyfre.nwyfre.stage;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;

import net.minecraft.resources.ResourceLocation;

/**
 * The set of game stages a single player has unlocked.
 *
 * <p>Stage identifiers are deliberately free-form {@link ResourceLocation}s rather than entries in
 * a fixed registry: a pack author can invent {@code mypack:bronze_age} from a KubeJS script with no
 * Java change, which is the point of the stage system. Nothing here hardcodes a stage name.
 *
 * <p>Insertion order is preserved so {@link #view()} reads predictably in commands and HUDs; stage
 * semantics themselves do not depend on order.
 */
public final class PlayerStages {

    /** Serialised as a plain list of ids, so datapack and KubeJS tooling can read it directly. */
    public static final Codec<PlayerStages> CODEC = ResourceLocation.CODEC
            .listOf()
            .xmap(PlayerStages::of, stages -> List.copyOf(stages.stages));

    private final Set<ResourceLocation> stages;

    public PlayerStages() {
        this.stages = new LinkedHashSet<>();
    }

    private PlayerStages(Collection<ResourceLocation> initial) {
        this.stages = new LinkedHashSet<>(initial);
    }

    public static PlayerStages of(Collection<ResourceLocation> initial) {
        return new PlayerStages(initial);
    }

    /**
     * Adds a stage.
     *
     * @return {@code true} if the stage was not already present. Callers use this to avoid firing
     *         change events and network syncs for no-op grants.
     */
    public boolean grant(ResourceLocation stage) {
        return stages.add(stage);
    }

    /**
     * Removes a stage.
     *
     * @return {@code true} if the stage had been present.
     */
    public boolean revoke(ResourceLocation stage) {
        return stages.remove(stage);
    }

    public boolean has(ResourceLocation stage) {
        return stages.contains(stage);
    }

    /** @return {@code true} only if every given stage is present. An empty collection is trivially satisfied. */
    public boolean hasAll(Collection<ResourceLocation> required) {
        return stages.containsAll(required);
    }

    /** @return {@code true} if at least one of the given stages is present. */
    public boolean hasAny(Collection<ResourceLocation> candidates) {
        for (ResourceLocation candidate : candidates) {
            if (stages.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** @return {@code true} if the player has unlocked nothing at all. */
    public boolean isEmpty() {
        return stages.isEmpty();
    }

    public int size() {
        return stages.size();
    }

    /** Clears every stage. @return {@code true} if anything was actually removed. */
    public boolean clear() {
        if (stages.isEmpty()) {
            return false;
        }
        stages.clear();
        return true;
    }

    /** An unmodifiable live view, in insertion order. */
    public Set<ResourceLocation> view() {
        return Collections.unmodifiableSet(stages);
    }

    /** A detached copy, used when respawning a player or snapshotting for a network payload. */
    public PlayerStages copy() {
        return new PlayerStages(stages);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PlayerStages that && stages.equals(that.stages);
    }

    @Override
    public int hashCode() {
        return stages.hashCode();
    }

    @Override
    public String toString() {
        return "PlayerStages" + stages;
    }
}
