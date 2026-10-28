package com.nwyfre.nwyfre.ritual.prereq;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nwyfre.nwyfre.registry.NwyfrePrerequisites;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;

/**
 * A condition that must hold for a ritual to start.
 *
 * <p>Deliberately <strong>not</strong> NeoForge {@code ICondition}. That type is built for
 * datapack load-time filtering and cannot explain itself, which is why Occultism needed a whole
 * {@code conditionextension} package of wrappers and visitors to turn a failing condition into a
 * sentence a player could read. Since {@code specs/rituals.md} mandates diagnostics anyway, every
 * term here carries {@link #describe()} instead and the problem disappears: one method serves the
 * failure message, the JEI panel and the Patchouli entry alike.
 *
 * <p>{@code ICondition} is still reachable as one term -- see {@link PackConditionPrerequisite} --
 * so packs keep {@code neoforge:mod_loaded} and friends.
 *
 * <p>Prerequisites are evaluated when a ritual <em>starts</em>, not when the datapack loads. That
 * is what makes biome, weather and time-of-day gating possible at all. A recipe can opt into
 * re-checking every tick with {@code sustain_prerequisites}; the default is off so a long
 * night-only ritual does not destroy itself at sunrise.
 */
public interface RitualPrerequisite {

    Codec<RitualPrerequisite> CODEC = Codec.lazyInitialized(() -> NwyfrePrerequisites.REGISTRY
            .byNameCodec()
            .dispatch("type", RitualPrerequisite::codec, mapCodec -> mapCodec));

    /** The entry in {@link NwyfrePrerequisites} this term decodes from. */
    MapCodec<? extends RitualPrerequisite> codec();

    boolean test(PrerequisiteContext context);

    /**
     * What this term requires, phrased for a player.
     *
     * <p>Written as a requirement ("It must be night."), never as an error, because the same text
     * is shown in JEI and the guidebook for rituals the player has not attempted yet.
     */
    Component describe();

    /**
     * Adds the descriptions of whichever terms are actually responsible for failure.
     *
     * <p>Composites override this to recurse, so a player is told the one thing they are missing
     * rather than the whole tree.
     */
    default void collectFailures(PrerequisiteContext context, List<Component> out) {
        if (!test(context)) {
            out.add(describe());
        }
    }

    /** Convenience for callers that just want the lines. */
    default List<Component> failures(PrerequisiteContext context) {
        List<Component> out = new ArrayList<>();
        collectFailures(context, out);
        return out;
    }

    /**
     * Every term must hold.
     *
     * <p>An empty {@code all_of} passes, matching {@code PlayerStages.hasAll}, which already set
     * that precedent in this codebase.
     */
    record AllOf(List<RitualPrerequisite> terms) implements RitualPrerequisite {

        public static final MapCodec<AllOf> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        RitualPrerequisite.CODEC.listOf().fieldOf("terms").forGetter(AllOf::terms))
                .apply(instance, AllOf::new));

        public AllOf(List<RitualPrerequisite> terms) {
            this.terms = List.copyOf(terms);
        }

        @Override
        public MapCodec<? extends RitualPrerequisite> codec() {
            return CODEC;
        }

        @Override
        public boolean test(PrerequisiteContext context) {
            return terms.stream().allMatch(term -> term.test(context));
        }

        @Override
        public void collectFailures(PrerequisiteContext context, List<Component> out) {
            terms.forEach(term -> term.collectFailures(context, out));
        }

        @Override
        public Component describe() {
            return ComponentUtils.formatList(terms.stream().map(RitualPrerequisite::describe).toList(),
                    Component.literal(" "));
        }
    }

    /**
     * At least one term must hold.
     *
     * <p>An empty {@code any_of} fails, matching {@code PlayerStages.hasAny}.
     */
    record AnyOf(List<RitualPrerequisite> terms) implements RitualPrerequisite {

        public static final MapCodec<AnyOf> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        RitualPrerequisite.CODEC.listOf().fieldOf("terms").forGetter(AnyOf::terms))
                .apply(instance, AnyOf::new));

        public AnyOf(List<RitualPrerequisite> terms) {
            this.terms = List.copyOf(terms);
        }

        @Override
        public MapCodec<? extends RitualPrerequisite> codec() {
            return CODEC;
        }

        @Override
        public boolean test(PrerequisiteContext context) {
            return terms.stream().anyMatch(term -> term.test(context));
        }

        /**
         * Reports one combined line rather than recursing.
         *
         * <p>Listing every unmet alternative as a separate failure would read as though the player
         * needed all of them, which is the opposite of what this term means.
         */
        @Override
        public void collectFailures(PrerequisiteContext context, List<Component> out) {
            if (!test(context)) {
                out.add(describe());
            }
        }

        @Override
        public Component describe() {
            return Component.translatable("nwyfre.ritual.prereq.any_of",
                    ComponentUtils.formatList(terms.stream().map(RitualPrerequisite::describe).toList(),
                            Component.literal(", ")));
        }
    }

    /** The term must not hold. */
    record Not(RitualPrerequisite term) implements RitualPrerequisite {

        public static final MapCodec<Not> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        RitualPrerequisite.CODEC.fieldOf("term").forGetter(Not::term))
                .apply(instance, Not::new));

        @Override
        public MapCodec<? extends RitualPrerequisite> codec() {
            return CODEC;
        }

        @Override
        public boolean test(PrerequisiteContext context) {
            return !term.test(context);
        }

        @Override
        public Component describe() {
            return Component.translatable("nwyfre.ritual.prereq.not", term.describe());
        }
    }
}
