package com.nwyfre.nwyfre.ritual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.nwyfre.nwyfre.Config;
import com.nwyfre.nwyfre.block.entity.SingleSlotBlockEntity;
import com.nwyfre.nwyfre.registry.NwyfreRecipes;
import com.nwyfre.nwyfre.registry.NwyfreRegistries;
import com.nwyfre.nwyfre.ritual.pattern.MatchResult;
import com.nwyfre.nwyfre.ritual.pattern.PatternMatcher;
import com.nwyfre.nwyfre.ritual.pattern.RitualPattern;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Reads the world around an altar: what the bowls hold, what augments are nearby, and which recipe
 * is being attempted.
 *
 * <p>Pulled out of the block entity so the lifecycle code reads as a sequence of decisions rather
 * than a mix of decisions and world traversal. The decisions themselves live on
 * {@link RitualRecipe} and are unit-tested there; this is the thin layer that has to touch a level.
 */
public final class RitualSite {

    private RitualSite() {
    }

    /** What a candidate ritual looks like once the world has been read. */
    public record Candidate(RecipeHolder<RitualRecipe> recipe, RitualPattern pattern,
            MatchResult match, Map<ResourceKey<Element>, ItemStack> bowlContents) {
    }

    /**
     * Finds the first ritual whose circle is built, activation item matches, and offerings are
     * right.
     *
     * <p>Recipe order decides ties, which is the same contract vanilla crafting gives. Returns
     * empty rather than throwing when a recipe names a pattern that no longer exists, since a
     * datapack can be reloaded out from under a recipe.
     */
    public static Optional<Candidate> findRitual(ServerLevel level, BlockPos altarPos,
            ItemStack activationItem) {
        for (RecipeHolder<RitualRecipe> holder : allRituals(level)) {
            Optional<Candidate> candidate = evaluate(level, altarPos, holder);
            if (candidate.isPresent()
                    && candidate.get().match().matched()
                    && holder.value().matches(candidate.get().pattern(),
                            candidate.get().bowlContents(), activationItem)) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    /** Resolves one recipe against the world without judging whether it matches. */
    public static Optional<Candidate> evaluate(ServerLevel level, BlockPos altarPos,
            RecipeHolder<RitualRecipe> holder) {
        return NwyfreRegistries.pattern(level.registryAccess(), holder.value().placement().pattern())
                .map(pattern -> {
                    MatchResult match = PatternMatcher.match(pattern, altarPos, PatternMatcher.of(level));
                    return new Candidate(holder, pattern, match, readBowls(level, match));
                });
    }

    public static List<RecipeHolder<RitualRecipe>> allRituals(ServerLevel level) {
        return level.getServer().getRecipeManager().getAllRecipesFor(NwyfreRecipes.RITUAL_TYPE.get());
    }

    /**
     * What each offering bowl currently holds.
     *
     * <p>A slot whose bowl is missing or is not a single-slot holder reads as empty, so a half-built
     * circle produces sensible diagnostics rather than an exception.
     */
    public static Map<ResourceKey<Element>, ItemStack> readBowls(ServerLevel level, MatchResult match) {
        Map<ResourceKey<Element>, ItemStack> contents = new HashMap<>();
        match.offeringSlots().forEach((element, pos) -> {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            contents.put(element, blockEntity instanceof SingleSlotBlockEntity holder
                    ? holder.getHeld()
                    : ItemStack.EMPTY);
        });
        return contents;
    }

    /**
     * Takes one offering from its bowl.
     *
     * @return the stack removed, or empty if the bowl was not holding anything
     */
    public static ItemStack takeOffering(ServerLevel level, BlockPos bowlPos) {
        if (!(level.getBlockEntity(bowlPos) instanceof SingleSlotBlockEntity holder)) {
            return ItemStack.EMPTY;
        }
        ItemStack held = holder.getHeld();
        if (held.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = held.copy();
        holder.setHeld(ItemStack.EMPTY);
        return taken;
    }

    /**
     * Every item in every container within the configured radius of the altar.
     *
     * <p>Read through the item handler capability rather than by block type, so any container from
     * any mod works -- a vanilla chest, a barrel, a modded crate -- with no integration code. The
     * altar and its own bowls are skipped: a bowl holding an offering must not double as an augment.
     */
    public static List<ItemStack> readAugmentItems(ServerLevel level, BlockPos altarPos) {
        int radius = Config.RITUALS.augmentSearchRadius.get();
        List<ItemStack> items = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(
                altarPos.offset(-radius, -radius, -radius),
                altarPos.offset(radius, radius, radius))) {
            if (level.getBlockEntity(pos) instanceof SingleSlotBlockEntity) {
                continue;
            }
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            }
        }
        return items;
    }

    /**
     * Consumes the augment items if the pack has asked for that.
     *
     * <p>Off by default: augments sit in a container rather than in a bowl, which reads as a
     * reusable catalyst rather than an ingredient.
     */
    public static void consumeAugment(ServerLevel level, BlockPos altarPos, RitualAugment augment) {
        if (!Config.RITUALS.consumeAugments.get()) {
            return;
        }
        int radius = Config.RITUALS.augmentSearchRadius.get();
        List<net.minecraft.world.item.crafting.Ingredient> outstanding =
                new ArrayList<>(augment.requires());

        for (BlockPos pos : BlockPos.betweenClosed(
                altarPos.offset(-radius, -radius, -radius),
                altarPos.offset(radius, radius, radius))) {
            if (outstanding.isEmpty()) {
                return;
            }
            if (level.getBlockEntity(pos) instanceof SingleSlotBlockEntity) {
                continue;
            }
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots() && !outstanding.isEmpty(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                int index = indexOfMatch(outstanding, stack);
                if (index >= 0) {
                    handler.extractItem(slot, 1, false);
                    outstanding.remove(index);
                }
            }
        }
    }

    private static int indexOfMatch(List<net.minecraft.world.item.crafting.Ingredient> outstanding,
            ItemStack stack) {
        for (int i = 0; i < outstanding.size(); i++) {
            if (outstanding.get(i).test(stack)) {
                return i;
            }
        }
        return -1;
    }
}
