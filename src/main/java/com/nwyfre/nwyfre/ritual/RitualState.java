package com.nwyfre.nwyfre.ritual;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * The state of a ritual that is currently running on an altar.
 *
 * <p>Held as a Data Attachment on the altar block entity, so persistence is a codec rather than
 * hand-rolled NBT -- which is how all four reference mods do it, and how bugs get in. The
 * attachment is registered with {@code .serialize(CODEC)} in {@code NwyfreAttachments}.
 *
 * <p>Immutable: every change produces a new instance, which the altar writes back with
 * {@code setData}. Mutating in place would not mark the block entity dirty (CLAUDE.md).
 *
 * @param recipeId which ritual is running
 * @param casterId who started it, absent when automation did
 * @param ticksRun how long it has been going
 * @param potency the augment strength resolved at start, snapshotted so a looted chest cannot change it
 * @param augmentEffectReplaced whether the matched augment swapped the outcome
 * @param sacrificeDone whether the required sacrifice has been made
 * @param itemUseDone whether the required item has been used
 * @param consumedOfferings offerings taken so far, kept so an interrupt can give them all back
 */
public record RitualState(ResourceLocation recipeId, Optional<UUID> casterId, int ticksRun,
        int potency, boolean augmentEffectReplaced, boolean sacrificeDone, boolean itemUseDone,
        List<ItemStack> consumedOfferings) {

    public static final Codec<RitualState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("recipe").forGetter(RitualState::recipeId),
                    UUIDUtil.CODEC.optionalFieldOf("caster").forGetter(RitualState::casterId),
                    Codec.INT.optionalFieldOf("ticks_run", 0).forGetter(RitualState::ticksRun),
                    Codec.INT.optionalFieldOf("potency", 0).forGetter(RitualState::potency),
                    Codec.BOOL.optionalFieldOf("augment_effect", false)
                            .forGetter(RitualState::augmentEffectReplaced),
                    Codec.BOOL.optionalFieldOf("sacrifice_done", false).forGetter(RitualState::sacrificeDone),
                    Codec.BOOL.optionalFieldOf("item_use_done", false).forGetter(RitualState::itemUseDone),
                    ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("consumed", List.of())
                            .forGetter(RitualState::consumedOfferings))
            .apply(instance, RitualState::new));

    public RitualState(ResourceLocation recipeId, Optional<UUID> casterId, int ticksRun, int potency,
            boolean augmentEffectReplaced, boolean sacrificeDone, boolean itemUseDone,
            List<ItemStack> consumedOfferings) {
        this.recipeId = recipeId;
        this.casterId = casterId;
        this.ticksRun = ticksRun;
        this.potency = potency;
        this.augmentEffectReplaced = augmentEffectReplaced;
        this.sacrificeDone = sacrificeDone;
        this.itemUseDone = itemUseDone;
        this.consumedOfferings = List.copyOf(consumedOfferings);
    }

    public static RitualState starting(ResourceLocation recipeId, Optional<UUID> casterId,
            int potency, boolean augmentEffectReplaced) {
        return new RitualState(recipeId, casterId, 0, potency, augmentEffectReplaced,
                false, false, List.of());
    }

    public RitualState advanced() {
        return withTicksRun(ticksRun + 1);
    }

    public RitualState withTicksRun(int ticks) {
        return new RitualState(recipeId, casterId, ticks, potency, augmentEffectReplaced,
                sacrificeDone, itemUseDone, consumedOfferings);
    }

    public RitualState withSacrificeDone() {
        return new RitualState(recipeId, casterId, ticksRun, potency, augmentEffectReplaced,
                true, itemUseDone, consumedOfferings);
    }

    public RitualState withItemUseDone() {
        return new RitualState(recipeId, casterId, ticksRun, potency, augmentEffectReplaced,
                sacrificeDone, true, consumedOfferings);
    }

    public RitualState withConsumed(ItemStack offering) {
        List<ItemStack> updated = new ArrayList<>(consumedOfferings);
        updated.add(offering.copy());
        return new RitualState(recipeId, casterId, ticksRun, potency, augmentEffectReplaced,
                sacrificeDone, itemUseDone, updated);
    }

    public int consumedCount() {
        return consumedOfferings.size();
    }

    /**
     * Whether the player has done everything the ritual asks of them while it runs.
     *
     * <p>A ritual with no demands is trivially satisfied, which is the common case.
     */
    public boolean demandsSatisfied(RitualDemands demands) {
        return (!demands.requiresSacrifice() || sacrificeDone)
                && (!demands.requiresItemUse() || itemUseDone);
    }
}
