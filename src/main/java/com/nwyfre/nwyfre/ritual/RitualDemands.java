package com.nwyfre.nwyfre.ritual;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * What the player has to do <em>while</em> a ritual runs, as opposed to the prerequisites that are
 * checked before it starts.
 *
 * <p>The distinction matters: a prerequisite is a gate, and failing one means the ritual never
 * begins. A demand is a participation requirement, and the ritual sits visibly waiting until it is
 * satisfied. Keeping them as separate concepts is what lets the diagnostics say the right thing.
 *
 * @param sacrifice a mob that must be killed near the altar
 * @param itemToUse an item that must be used near the altar
 */
public record RitualDemands(Optional<Sacrifice> sacrifice, Optional<Ingredient> itemToUse) {

    public static final RitualDemands NONE = new RitualDemands(Optional.empty(), Optional.empty());

    public static final MapCodec<RitualDemands> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Sacrifice.CODEC.optionalFieldOf("sacrifice").forGetter(RitualDemands::sacrifice),
                    Ingredient.CODEC.optionalFieldOf("item_to_use").forGetter(RitualDemands::itemToUse))
            .apply(instance, RitualDemands::new));

    public boolean requiresSacrifice() {
        return sacrifice.isPresent();
    }

    public boolean requiresItemUse() {
        return itemToUse.isPresent();
    }

    /** True when the ritual asks nothing of the player once it has begun. */
    public boolean isEmpty() {
        return sacrifice.isEmpty() && itemToUse.isEmpty();
    }

    public boolean isValidSacrifice(LivingEntity entity) {
        return sacrifice.isPresent() && entity.getType().is(sacrifice.get().tag());
    }

    public boolean isValidItemUse(ItemStack stack) {
        return itemToUse.isPresent() && itemToUse.get().test(stack);
    }

    /**
     * A mob kind that has to die near the altar.
     *
     * <p>The display name is carried as a translation key rather than derived from the tag, because
     * {@code #minecraft:zombies} has no sensible automatic name and the author knows what they mean
     * by it.
     *
     * @param tag which entities qualify
     * @param displayName translation key naming them, e.g. {@code ritual.nwyfre.sacrifice.zombies}
     */
    public record Sacrifice(TagKey<EntityType<?>> tag, String displayName) {

        public static final Codec<Sacrifice> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        TagKey.codec(Registries.ENTITY_TYPE).fieldOf("tag").forGetter(Sacrifice::tag),
                        Codec.STRING.fieldOf("display_name").forGetter(Sacrifice::displayName))
                .apply(instance, Sacrifice::new));

        public Component describe() {
            return Component.translatable(displayName);
        }
    }
}
