package com.nwyfre.nwyfre.ritual.effect;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nwyfre.nwyfre.block.entity.SingleSlotBlockEntity;
import com.nwyfre.nwyfre.ritual.RitualContext;
import com.nwyfre.nwyfre.ritual.RitualEffect;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The effects that produce something: an item, or an entity.
 *
 * <p>Neither works as a persistent ritual. A lit candle that kept handing out diamonds or summoning
 * wolves is almost certainly a pack author mistake rather than an intention, so both refuse at load
 * time rather than at play time.
 */
public final class ItemEffects {

    private ItemEffects() {
    }

    /**
     * Produces an item.
     *
     * <p>Covers both "Item Creation" and "Enchantment" from the spec, because the result is a full
     * {@link ItemStack} with data components: an enchanted sword is just a result that carries
     * {@code minecraft:enchantments}.
     *
     * <p>Prefers to place the result in a bowl sitting on top of the altar, falling back to
     * dropping it. That means an automated setup has somewhere predictable to pull from, rather
     * than having to vacuum up item entities.
     */
    public record GiveItem(ItemStack result) implements RitualEffect {

        public static final MapCodec<GiveItem> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ItemStack.CODEC.fieldOf("result").forGetter(GiveItem::result))
                .apply(instance, GiveItem::new));

        @Override
        public MapCodec<? extends RitualEffect> codec() {
            return CODEC;
        }

        @Override
        public void apply(RitualContext context) {
            deliver(context, result.copy());
        }

        /** Into a bowl above the altar if there is room, otherwise onto the ground. */
        static void deliver(RitualContext context, ItemStack stack) {
            ServerLevel level = context.level();
            BlockPos above = context.altarPos().above();
            BlockEntity blockEntity = level.getBlockEntity(above);

            if (blockEntity instanceof SingleSlotBlockEntity holder && holder.getHeld().isEmpty()) {
                holder.setHeld(stack);
                return;
            }

            ItemEntity entity = new ItemEntity(level,
                    context.centre().x, context.centre().y + 0.75, context.centre().z, stack);
            entity.setDeltaMovement(0.0, 0.2, 0.0);
            entity.setPickUpDelay(10);
            level.addFreshEntity(entity);
        }

        @Override
        public boolean supportsPersistent() {
            return false;
        }

        @Override
        public Component describe() {
            return Component.translatable("nwyfre.ritual.effect.give_item", result.getHoverName());
        }
    }

    /**
     * Summons entities around the altar.
     *
     * <p>{@link RitualContext#potency()} adds to the count, so an augment can make a summoning
     * larger without the recipe re-declaring what is summoned.
     */
    public record Summon(EntityType<?> entity, int count, Optional<CompoundTag> nbt, boolean tame)
            implements RitualEffect {

        public static final MapCodec<Summon> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.byNameCodec()
                                .fieldOf("entity").forGetter(Summon::entity),
                        Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Summon::count),
                        CompoundTag.CODEC.optionalFieldOf("nbt").forGetter(Summon::nbt),
                        Codec.BOOL.optionalFieldOf("tame", false).forGetter(Summon::tame))
                .apply(instance, Summon::new));

        @Override
        public MapCodec<? extends RitualEffect> codec() {
            return CODEC;
        }

        @Override
        public void apply(RitualContext context) {
            ServerLevel level = context.level();
            int total = Math.min(64, count + Math.max(0, context.potency()));

            for (int i = 0; i < total; i++) {
                Entity spawned = entity.create(level);
                if (spawned == null) {
                    return;
                }

                nbt.ifPresent(tag -> {
                    CompoundTag merged = spawned.saveWithoutId(new CompoundTag());
                    merged.merge(tag);
                    spawned.load(merged);
                });

                // Spread them around the altar rather than stacking them inside it.
                double angle = (Math.PI * 2 * i) / total;
                spawned.moveTo(
                        context.centre().x + Math.cos(angle) * 1.5,
                        context.altarPos().getY() + 1,
                        context.centre().z + Math.sin(angle) * 1.5,
                        (float) Math.toDegrees(angle),
                        0.0F);

                if (spawned instanceof Mob mob) {
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                            MobSpawnType.MOB_SUMMONED, null);
                }
                if (tame && spawned instanceof TamableAnimal tamable) {
                    context.caster().ifPresent(tamable::tame);
                }

                level.addFreshEntity(spawned);
            }
        }

        @Override
        public boolean supportsPersistent() {
            return false;
        }

        @Override
        public Component describe() {
            return Component.translatable("nwyfre.ritual.effect.summon",
                    Component.translatable(entity.getDescriptionId()), count);
        }
    }
}
