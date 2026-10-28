package com.nwyfre.nwyfre.ritual;

import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Everything a {@link RitualEffect} is handed when it fires.
 *
 * <p>Always server-side: effects mutate the world, so there is no client half to this.
 *
 * @param level the level the altar is in
 * @param altarPos the altar, which positional effects measure from
 * @param caster whoever started the ritual, absent when automation did
 * @param potency additive strength from the matched augment, 0 when there was none
 * @param offerings what was actually consumed, by element, so an effect can vary on the offering
 * @param ticksRun how long the ritual has been running, or how long a persistent one has burned
 */
public record RitualContext(ServerLevel level, BlockPos altarPos, Optional<ServerPlayer> caster,
        int potency, Map<ResourceKey<Element>, ItemStack> offerings, int ticksRun) {

    public RitualContext(ServerLevel level, BlockPos altarPos, Optional<ServerPlayer> caster,
            int potency, Map<ResourceKey<Element>, ItemStack> offerings, int ticksRun) {
        this.level = level;
        this.altarPos = altarPos;
        this.caster = caster;
        this.potency = potency;
        this.offerings = Map.copyOf(offerings);
        this.ticksRun = ticksRun;
    }

    public static RitualContext of(ServerLevel level, BlockPos altarPos, @Nullable ServerPlayer caster,
            int potency, Map<ResourceKey<Element>, ItemStack> offerings, int ticksRun) {
        return new RitualContext(level, altarPos, Optional.ofNullable(caster), potency, offerings, ticksRun);
    }

    /** The offering made to an element, if the recipe asked for one. */
    public Optional<ItemStack> offering(ResourceKey<Element> element) {
        return Optional.ofNullable(offerings.get(element));
    }

    /** Centre of the altar block, which is what most area effects actually want. */
    public net.minecraft.world.phys.Vec3 centre() {
        return altarPos.getCenter();
    }
}
