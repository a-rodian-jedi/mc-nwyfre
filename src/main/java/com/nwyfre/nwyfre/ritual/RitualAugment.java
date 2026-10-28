package com.nwyfre.nwyfre.ritual;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * An optional modifier on a ritual, keyed off items sitting in a container near the altar.
 *
 * <p>Two mechanisms, because they answer two different questions. {@link #effect} replaces the
 * outcome outright, which is how "a different thing happens"; {@link #potency} is an additive
 * number effects read from {@link RitualContext#potency()}, which is how "the same thing happens,
 * harder" works without a pack author re-declaring the whole effect to change one value.
 *
 * <p>Augments are resolved <strong>once, when the ritual starts</strong>, and the result is
 * snapshotted. Emptying the chest half way through must not change what the ritual turns into.
 *
 * @param requires every one of these must be present in the container
 * @param effect replaces the recipe effect when this augment matches
 * @param potency added to the effect strength
 */
public record RitualAugment(List<Ingredient> requires, Optional<RitualEffect> effect, int potency) {

    public static final Codec<RitualAugment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Ingredient.CODEC.listOf().fieldOf("requires").forGetter(RitualAugment::requires),
                    RitualEffect.CODEC.optionalFieldOf("effect").forGetter(RitualAugment::effect),
                    Codec.INT.optionalFieldOf("potency", 0).forGetter(RitualAugment::potency))
            .apply(instance, RitualAugment::new));

    public RitualAugment(List<Ingredient> requires, Optional<RitualEffect> effect, int potency) {
        this.requires = List.copyOf(requires);
        this.effect = effect;
        this.potency = potency;
    }

    /**
     * Whether everything this augment asks for is present.
     *
     * <p>Each requirement must be met by a <em>distinct</em> stack, so asking for two diamonds
     * genuinely means two, not one counted twice. An augment with no requirements never matches:
     * it would otherwise fire on every ritual, which no author means.
     */
    public boolean matches(List<ItemStack> available) {
        if (requires.isEmpty()) {
            return false;
        }

        List<ItemStack> remaining = new ArrayList<>(available);
        for (Ingredient requirement : requires) {
            int found = -1;
            for (int i = 0; i < remaining.size(); i++) {
                if (requirement.test(remaining.get(i))) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                return false;
            }
            remaining.remove(found);
        }
        return true;
    }

    /**
     * The first augment in declaration order that matches.
     *
     * <p>First-match-wins rather than best-match, so the author controls precedence by ordering the
     * list. Guessing at specificity would make the outcome depend on rules nobody can see.
     */
    public static Optional<RitualAugment> select(List<RitualAugment> augments, List<ItemStack> available) {
        return augments.stream().filter(augment -> augment.matches(available)).findFirst();
    }

    /** Convenience for the serializer and for tests. */
    public static MapCodec<List<RitualAugment>> listField() {
        return CODEC.listOf().optionalFieldOf("augments", List.of());
    }
}
