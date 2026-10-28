package com.nwyfre.nwyfre.ritual.diagnostic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.nwyfre.nwyfre.ritual.Element;
import com.nwyfre.nwyfre.ritual.RitualRecipe;
import com.nwyfre.nwyfre.ritual.RitualSite;
import com.nwyfre.nwyfre.ritual.prereq.PrerequisiteContext;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Works out why a ritual did not start, and says it in a way a player can act on.
 *
 * <p>{@code specs/rituals.md} asks for diagnostics in the style of Occultism, which is the single
 * best idea in any of the reference mods: a half-built ritual circle is otherwise completely opaque,
 * because the game has no way to tell you which of forty blocks is wrong.
 *
 * <p>Deliberately a <strong>pure function</strong>. It takes already-gathered facts and returns a
 * {@link DiagnosticReport}; it never touches a level, sends a message or spawns a particle. That is
 * what lets the whole ladder be unit-tested, and it keeps the expensive part -- comparing the world
 * against every pattern -- in one place that the caller decides when to pay for.
 */
public final class RitualDiagnostics {

    private RitualDiagnostics() {
    }

    /**
     * Picks the most useful thing to say.
     *
     * @param candidates every ritual resolved against this altar, in recipe order
     * @param activationItem what the player offered, empty when they used a bare hand
     * @param context the world, for re-testing prerequisites
     * @param elementName renders an element for display, e.g. "Earth (north)"
     */
    public static DiagnosticReport diagnose(List<RitualSite.Candidate> candidates,
            ItemStack activationItem, PrerequisiteContext context,
            Function<ResourceKey<Element>, Component> elementName) {

        if (candidates.isEmpty()) {
            return DiagnosticReport.of(DiagnosticReport.Rung.NOTHING_CLOSE,
                    List.of(Component.translatable("nwyfre.ritual.diag.nothing_close")));
        }

        List<RitualSite.Candidate> built = candidates.stream()
                .filter(candidate -> candidate.match().matched())
                .toList();

        if (built.isEmpty()) {
            return explainIncompleteCircle(candidates);
        }

        // A circle is standing. From here on the problem is what the player is doing with it.
        List<RitualSite.Candidate> startable = built.stream()
                .filter(candidate -> candidate.recipe().value().placement().activationItem()
                        .test(activationItem))
                .toList();

        if (startable.isEmpty()) {
            return explainWrongActivationItem(built);
        }

        // Report against the first startable ritual: it is the one that would have fired.
        RitualSite.Candidate target = startable.getFirst();
        RitualRecipe recipe = target.recipe().value();

        Map<ResourceKey<Element>, Ingredient> missing =
                recipe.missingOfferings(target.bowlContents());
        if (!missing.isEmpty()) {
            return explainMissingOfferings(target, missing, elementName);
        }

        List<ResourceKey<Element>> unwanted =
                recipe.unwantedOfferings(target.pattern(), target.bowlContents());
        if (!unwanted.isEmpty()) {
            return explainUnwantedOfferings(target, unwanted, elementName);
        }

        if (recipe.prerequisites().isPresent()) {
            List<Component> failures = recipe.prerequisites().get().failures(context);
            if (!failures.isEmpty()) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("nwyfre.ritual.diag.prerequisites"));
                lines.addAll(failures);
                return DiagnosticReport.of(DiagnosticReport.Rung.PREREQUISITES, lines);
            }
        }

        return DiagnosticReport.of(DiagnosticReport.Rung.READY, List.of());
    }

    /**
     * Finds the circle the player was most likely building and marks what is missing from it.
     *
     * <p>Fewest missing blocks wins. A tie goes to the first in recipe order, which is stable.
     */
    private static DiagnosticReport explainIncompleteCircle(List<RitualSite.Candidate> candidates) {
        RitualSite.Candidate closest = candidates.stream()
                .min(Comparator.comparingInt(candidate -> candidate.match().missingCount()))
                .orElseThrow();

        List<BlockPos> marks = new ArrayList<>(closest.match().missing().keySet());

        // Bowls-only gets its own message: a player in this state usually does not yet know that
        // bowls are a separate thing they have to place.
        if (closest.match().missingOnlyOfferingSlots()) {
            return DiagnosticReport.of(DiagnosticReport.Rung.NO_BOWLS,
                    List.of(Component.translatable("nwyfre.ritual.diag.no_bowls",
                            closest.match().missingCount())),
                    marks);
        }

        return DiagnosticReport.of(DiagnosticReport.Rung.PATTERN_INCOMPLETE,
                List.of(Component.translatable("nwyfre.ritual.diag.pattern_incomplete",
                        closest.match().missingCount())),
                marks);
    }

    /** Names what the standing circle actually answers to. */
    private static DiagnosticReport explainWrongActivationItem(List<RitualSite.Candidate> built) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("nwyfre.ritual.diag.wrong_activation"));

        built.stream()
                .map(candidate -> candidate.recipe().value().placement().activationItem())
                .map(RitualDiagnostics::firstStackName)
                .flatMap(Optional::stream)
                .distinct()
                .limit(4)
                .forEach(name -> lines.add(
                        Component.translatable("nwyfre.ritual.diag.activation_option", name)));

        return DiagnosticReport.of(DiagnosticReport.Rung.WRONG_ACTIVATION, lines);
    }

    /** Lists what is wanted, by element and direction, and marks the bowls concerned. */
    private static DiagnosticReport explainMissingOfferings(RitualSite.Candidate target,
            Map<ResourceKey<Element>, Ingredient> missing,
            Function<ResourceKey<Element>, Component> elementName) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("nwyfre.ritual.diag.missing_offerings"));

        List<BlockPos> marks = new ArrayList<>();
        missing.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location().toString()))
                .forEach(entry -> {
                    Component wanted = firstStackName(entry.getValue())
                            .orElseGet(() -> Component.translatable("nwyfre.ritual.diag.something"));
                    lines.add(Component.translatable("nwyfre.ritual.diag.offering_line",
                            elementName.apply(entry.getKey()), wanted));
                    target.match().offeringSlot(entry.getKey()).ifPresent(marks::add);
                });

        return DiagnosticReport.of(DiagnosticReport.Rung.MISSING_OFFERINGS, lines, marks);
    }

    private static DiagnosticReport explainUnwantedOfferings(RitualSite.Candidate target,
            List<ResourceKey<Element>> unwanted,
            Function<ResourceKey<Element>, Component> elementName) {
        List<Component> lines = new ArrayList<>();
        List<BlockPos> marks = new ArrayList<>();

        unwanted.stream()
                .sorted(Comparator.comparing(key -> key.location().toString()))
                .forEach(element -> {
                    lines.add(Component.translatable("nwyfre.ritual.diag.unwanted_offering",
                            elementName.apply(element)));
                    target.match().offeringSlot(element).ifPresent(marks::add);
                });

        return DiagnosticReport.of(DiagnosticReport.Rung.UNWANTED_OFFERINGS, lines, marks);
    }

    /**
     * A readable name for an ingredient.
     *
     * <p>Shows the first matching item rather than trying to name a tag: vanilla has no display
     * names for tags, so "#c:coals" would be worse for a player than "Coal".
     */
    private static Optional<Component> firstStackName(Ingredient ingredient) {
        ItemStack[] stacks = ingredient.getItems();
        return stacks.length == 0 ? Optional.empty() : Optional.of(stacks[0].getHoverName());
    }
}
