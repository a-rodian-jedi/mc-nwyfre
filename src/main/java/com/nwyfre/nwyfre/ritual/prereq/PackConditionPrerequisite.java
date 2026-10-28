package com.nwyfre.nwyfre.ritual.prereq;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Escape hatch that wraps a NeoForge {@link ICondition}, so packs keep
 * {@code neoforge:mod_loaded}, {@code neoforge:item_exists}, {@code neoforge:tag_empty} and the
 * boolean combinators they already know.
 *
 * <p>This is the one term that <strong>cannot explain itself</strong>. {@code ICondition} carries no
 * human-readable description, so a failure here can only produce a generic line. That limitation is
 * exactly why the rest of the prerequisite system is not built on {@code ICondition} -- see
 * {@link RitualPrerequisite}. Prefer a native term whenever one exists; reach for this only for
 * gates Nwyfre has no term for.
 *
 * <p>These are evaluated at <em>ritual start</em>, not at datapack load, and
 * {@link ICondition.IContext} only exposes tag lookups. Conditions that depend on nothing else
 * (mod loaded, item exists, tag empty, and the combinators over those) behave correctly; an exotic
 * third-party condition expecting load-time context may not.
 */
public record PackConditionPrerequisite(ICondition condition) implements RitualPrerequisite {

    public static final MapCodec<PackConditionPrerequisite> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                            ICondition.CODEC.fieldOf("condition").forGetter(PackConditionPrerequisite::condition))
                    .apply(instance, PackConditionPrerequisite::new));

    @Override
    public MapCodec<? extends RitualPrerequisite> codec() {
        return CODEC;
    }

    @Override
    public boolean test(PrerequisiteContext context) {
        return condition.test(new LiveTagContext(context.registries()));
    }

    @Override
    public Component describe() {
        return Component.translatable("nwyfre.ritual.prereq.pack_condition");
    }

    /**
     * Serves tag lookups out of the live server registries.
     *
     * <p>{@code ICondition.IContext} normally comes from the datapack loader, which is not around
     * by the time a ritual starts, so this rebuilds the one capability it offers from the running
     * world instead.
     */
    private record LiveTagContext(HolderLookup.Provider registries) implements ICondition.IContext {

        @Override
        public <T> Map<ResourceLocation, Collection<Holder<T>>> getAllTags(
                ResourceKey<? extends Registry<T>> registry) {
            return registries.lookup(registry)
                    .map(lookup -> lookup.listTags().collect(Collectors.toMap(
                            named -> named.key().location(),
                            named -> (Collection<Holder<T>>) named.stream().toList())))
                    .orElseGet(Map::of);
        }
    }
}
