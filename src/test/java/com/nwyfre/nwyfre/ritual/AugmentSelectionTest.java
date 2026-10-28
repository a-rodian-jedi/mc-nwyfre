package com.nwyfre.nwyfre.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import com.nwyfre.nwyfre.ritual.effect.WorldEffects;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * How an augment is chosen from whatever is lying in the nearby container.
 *
 * <p>Worth testing carefully because the failure modes are quiet: an augment that matches when it
 * should not silently changes what a ritual does, and one that needs two of an item but accepts one
 * makes a pack cheaper than its author intended.
 */
class AugmentSelectionTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static RitualAugment augment(int potency, ItemStack... required) {
        return new RitualAugment(
                List.of(required).stream().map(Ingredient::of).toList(),
                Optional.empty(),
                potency);
    }

    private static RitualAugment augmentWithEffect(RitualEffect effect, ItemStack... required) {
        return new RitualAugment(
                List.of(required).stream().map(Ingredient::of).toList(),
                Optional.of(effect),
                0);
    }

    private static final RitualEffect SUNSET =
            new WorldEffects.SetTime(WorldEffects.SetTime.Target.SUNSET, 0);

    @Test
    void matchesWhenEverythingItAsksForIsPresent() {
        RitualAugment diamondAndStar = augment(0,
                new ItemStack(Items.DIAMOND), new ItemStack(Items.NETHER_STAR));

        assertTrue(diamondAndStar.matches(List.of(
                new ItemStack(Items.DIAMOND), new ItemStack(Items.NETHER_STAR))));
    }

    @Test
    void doesNotMatchOnAPartialSet() {
        RitualAugment diamondAndStar = augment(0,
                new ItemStack(Items.DIAMOND), new ItemStack(Items.NETHER_STAR));

        assertFalse(diamondAndStar.matches(List.of(new ItemStack(Items.DIAMOND))),
                "every requirement must be met, not just one");
    }

    @Test
    void repeatedRequirementsNeedSeparateStacks() {
        // Asking for two diamonds has to mean two, or a pack is cheaper than it reads.
        RitualAugment twoDiamonds = augment(0,
                new ItemStack(Items.DIAMOND), new ItemStack(Items.DIAMOND));

        assertFalse(twoDiamonds.matches(List.of(new ItemStack(Items.DIAMOND))),
                "one stack must not satisfy two requirements");
        assertTrue(twoDiamonds.matches(List.of(
                new ItemStack(Items.DIAMOND), new ItemStack(Items.DIAMOND))));
    }

    @Test
    void ignoresUnrelatedItemsInTheContainer() {
        RitualAugment diamond = augment(0, new ItemStack(Items.DIAMOND));

        assertTrue(diamond.matches(List.of(
                new ItemStack(Items.DIRT), new ItemStack(Items.DIAMOND), new ItemStack(Items.STICK))));
    }

    @Test
    void anAugmentWithNoRequirementsNeverMatches() {
        // It would otherwise fire on every ritual, which no author means. The recipe codec also
        // rejects this shape outright; this pins the runtime behaviour as well.
        RitualAugment empty = new RitualAugment(List.of(), Optional.empty(), 5);

        assertFalse(empty.matches(List.of(new ItemStack(Items.DIAMOND))));
        assertFalse(empty.matches(List.of()));
    }

    @Test
    void selectTakesTheFirstMatchInDeclarationOrder() {
        // First-match-wins, not best-match: the author controls precedence by ordering the list,
        // rather than the outcome depending on a specificity rule nobody can see.
        RitualAugment broad = augment(1, new ItemStack(Items.DIAMOND));
        RitualAugment narrow = augment(9, new ItemStack(Items.DIAMOND), new ItemStack(Items.NETHER_STAR));

        List<ItemStack> container = List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.NETHER_STAR));

        assertSame(broad, RitualAugment.select(List.of(broad, narrow), container).orElseThrow(),
                "declaration order decides, even though the second is a tighter match");
        assertSame(narrow, RitualAugment.select(List.of(narrow, broad), container).orElseThrow(),
                "reordering the list changes which augment wins");
    }

    @Test
    void selectReturnsEmptyWhenNothingMatches() {
        RitualAugment star = augment(1, new ItemStack(Items.NETHER_STAR));

        assertTrue(RitualAugment.select(List.of(star), List.of(new ItemStack(Items.DIRT))).isEmpty());
        assertTrue(RitualAugment.select(List.of(), List.of(new ItemStack(Items.DIRT))).isEmpty());
    }

    @Test
    void potencyIsZeroWithoutAnAugment() {
        RitualRecipe recipe = RitualRecipeCodecTest.minimalRecipe();
        assertEquals(0, recipe.potencyWith(Optional.empty()));
    }

    @Test
    void potencyComesFromTheMatchedAugment() {
        RitualRecipe recipe = RitualRecipeCodecTest.minimalRecipe();
        assertEquals(4, recipe.potencyWith(Optional.of(augment(4, new ItemStack(Items.DIAMOND)))));
    }

    @Test
    void anAugmentEffectReplacesTheRecipeEffect() {
        RitualRecipe recipe = RitualRecipeCodecTest.minimalRecipe();

        RitualEffect base = recipe.effectWith(Optional.empty());
        RitualEffect replaced = recipe.effectWith(Optional.of(
                augmentWithEffect(SUNSET, new ItemStack(Items.DIAMOND))));

        assertSame(recipe.effect(), base, "with no augment the recipe effect stands");
        assertSame(SUNSET, replaced, "a matched augment with an effect replaces the outcome");
    }

    @Test
    void anAugmentWithOnlyPotencyLeavesTheEffectAlone() {
        RitualRecipe recipe = RitualRecipeCodecTest.minimalRecipe();

        RitualAugment potencyOnly = augment(3, new ItemStack(Items.DIAMOND));

        assertSame(recipe.effect(), recipe.effectWith(Optional.of(potencyOnly)));
        assertEquals(3, recipe.potencyWith(Optional.of(potencyOnly)));
    }
}
