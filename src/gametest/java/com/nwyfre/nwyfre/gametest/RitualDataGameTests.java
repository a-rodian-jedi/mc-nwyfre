package com.nwyfre.nwyfre.gametest;

import java.util.ArrayList;
import java.util.List;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.registry.NwyfreRecipes;
import com.nwyfre.nwyfre.registry.NwyfreRegistries;
import com.nwyfre.nwyfre.ritual.Element;
import com.nwyfre.nwyfre.ritual.RitualAugment;
import com.nwyfre.nwyfre.ritual.RitualRecipe;
import com.nwyfre.nwyfre.ritual.pattern.RitualPattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Proves the shipped ritual data actually loads on a real server.
 *
 * <p>This is the layer the headless JUnit suite structurally cannot reach. A bootstrap-only test
 * has no tags and no datapack registries, so it can neither load a datapack registry nor resolve a
 * {@code "#some:tag"} ingredient. Both are load-bearing here: the patterns and elements <em>are</em>
 * datapack registries, and the shipped recipes use tag-form ingredients and prerequisites.
 *
 * <p>Equally important, a malformed datapack entry does not crash the game -- it logs a line and
 * the entry is silently absent, which is exactly the failure that would otherwise reach a release.
 */
@GameTestHolder(Nwyfre.MODID)
@PrefixGameTestTemplate(false)
public final class RitualDataGameTests {

    private RitualDataGameTests() {
    }

    @GameTest(template = "empty")
    public static void everyShippedIngredientResolvesToSomething(GameTestHelper helper) {
        // A tag that does not exist produces an ingredient matching NOTHING, with no warning
        // anywhere: the ritual loads, appears in JEI, and then silently refuses to ever start.
        // This exists because the shipped sunrise rite asked for "c:coals", which is not a real
        // tag -- the vanilla one is "minecraft:coals".
        //
        // Test hasNoItems(), NOT getItems().length. Vanilla deliberately renders an empty
        // ingredient as a single minecraft:barrier named "Empty Tag: ...", so getItems() reports
        // one item for a tag that matches nothing -- which is exactly how the original bug hid.
        //
        // Only a live server can tell either way: tags are not loaded in a headless test.
        List<String> empty = new ArrayList<>();

        for (RecipeHolder<RitualRecipe> holder : helper.getLevel().getServer().getRecipeManager()
                .getAllRecipesFor(NwyfreRecipes.RITUAL_TYPE.get())) {
            RitualRecipe recipe = holder.value();

            if (recipe.placement().activationItem().hasNoItems()) {
                empty.add(holder.id() + " activation_item");
            }
            recipe.placement().offerings().forEach((element, ingredient) -> {
                if (ingredient.hasNoItems()) {
                    empty.add(holder.id() + " offering " + element.location());
                }
            });
            recipe.demands().itemToUse().ifPresent(itemToUse -> {
                if (itemToUse.hasNoItems()) {
                    empty.add(holder.id() + " item_to_use");
                }
            });
            for (RitualAugment augment : recipe.augments()) {
                for (Ingredient required : augment.requires()) {
                    if (required.hasNoItems()) {
                        empty.add(holder.id() + " augment requirement");
                    }
                }
            }
        }

        if (!empty.isEmpty()) {
            helper.fail("these ingredients match no items at all, so the rituals can never fire: "
                    + empty);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyShippedSacrificeTagResolves(GameTestHelper helper) {
        // Same failure shape for entity tags: a sacrifice nothing can satisfy stalls the ritual
        // forever rather than refusing it.
        List<String> empty = new ArrayList<>();
        var entityTypes = helper.getLevel().registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.ENTITY_TYPE);

        for (RecipeHolder<RitualRecipe> holder : helper.getLevel().getServer().getRecipeManager()
                .getAllRecipesFor(NwyfreRecipes.RITUAL_TYPE.get())) {
            holder.value().demands().sacrifice().ifPresent(sacrifice -> {
                if (entityTypes.getTag(sacrifice.tag()).map(tag -> tag.size() == 0).orElse(true)) {
                    empty.add(holder.id() + " -> " + sacrifice.tag().location());
                }
            });
        }

        if (!empty.isEmpty()) {
            helper.fail("these sacrifice tags are empty or missing: " + empty);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shippedElementsLoad(GameTestHelper helper) {
        Registry<Element> elements = NwyfreRegistries.elements(helper.getLevel().registryAccess());

        for (String name : List.of("earth", "air", "fire", "water")) {
            ResourceKey<Element> key = ResourceKey.create(NwyfreRegistries.ELEMENT, Nwyfre.id(name));
            if (elements.getOptional(key).isEmpty()) {
                helper.fail("element " + name + " did not load; it belongs at "
                        + "data/nwyfre/nwyfre/element/" + name + ".json -- a datapack registry whose key "
                        + "is not in the minecraft namespace nests under that namespace");
            }
        }

        // The element-by-direction convention the whole offering model rests on.
        Element earth = elements.getOrThrow(ResourceKey.create(NwyfreRegistries.ELEMENT, Nwyfre.id("earth")));
        if (earth.direction() != Direction.NORTH) {
            helper.fail("earth should face north, got " + earth.direction());
        }

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void firstCirclePatternLoadsAndResolves(GameTestHelper helper) {
        // The starter circle the guide walks a new player through. If its offsets ever shift, the
        // written instructions become wrong, which is worse than a bug nobody has documented.
        RitualPattern pattern = NwyfreRegistries.pattern(helper.getLevel().registryAccess(),
                        ResourceKey.create(NwyfreRegistries.RITUAL_PATTERN, Nwyfre.id("first_circle")))
                .orElse(null);
        if (pattern == null) {
            helper.fail("first_circle did not load");
            return;
        }
        if (pattern.elements().size() != 1) {
            helper.fail("first_circle should have exactly one offering slot, got "
                    + pattern.elements().size());
        }
        BlockPos bowl = pattern.offeringSlotOffset(
                ResourceKey.create(NwyfreRegistries.ELEMENT, Nwyfre.id("earth"))).orElseThrow();
        if (!bowl.equals(new BlockPos(0, 0, -1))) {
            helper.fail("the guide says the bowl sits one block due north of the altar, got " + bowl);
        }
        // Four corner marks: the guide tells the player to place exactly four.
        if (pattern.demandingCellCount() != 5) {
            helper.fail("first_circle should demand four marks and one bowl, got "
                    + pattern.demandingCellCount());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lesserCirclePatternLoadsAndResolves(GameTestHelper helper) {
        ResourceKey<RitualPattern> key = ResourceKey.create(NwyfreRegistries.RITUAL_PATTERN,
                Nwyfre.id("lesser_circle"));

        RitualPattern pattern = NwyfreRegistries.pattern(helper.getLevel().registryAccess(), key)
                .orElse(null);
        if (pattern == null) {
            helper.fail("lesser_circle did not load; a malformed pattern is skipped, not fatal");
            return;
        }

        // Resolution is what turns the authored grid into offsets, so an empty cell map means the
        // anchor was never found even though the file parsed.
        if (pattern.cells().isEmpty()) {
            helper.fail("lesser_circle loaded but resolved to no cells");
        }
        if (pattern.elements().size() != 4) {
            helper.fail("lesser_circle should have four offering slots, got " + pattern.elements().size());
        }

        // North is -Z: if this ever flips, every shipped circle silently puts its bowls at the
        // wrong elements.
        BlockPos earthSlot = pattern.offeringSlotOffset(
                ResourceKey.create(NwyfreRegistries.ELEMENT, Nwyfre.id("earth"))).orElseThrow();
        if (earthSlot.getZ() >= 0) {
            helper.fail("the earth bowl should sit north of the altar, got " + earthSlot);
        }
        if (!pattern.cells().containsKey(BlockPos.ZERO)) {
            helper.fail("the anchor should resolve to the origin");
        }

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shippedRitualRecipesLoad(GameTestHelper helper) {
        List<RecipeHolder<RitualRecipe>> recipes = helper.getLevel().getServer().getRecipeManager()
                .getAllRecipesFor(NwyfreRecipes.RITUAL_TYPE.get());

        // Recipe ids carry the subfolder: data/nwyfre/recipe/ritual/sunrise.json is
        // nwyfre:ritual/sunrise, not nwyfre:sunrise.
        for (String name : List.of("ritual/rite_of_first_breath", "ritual/sunrise",
                "ritual/clear_skies", "ritual/vigil_of_mending")) {
            ResourceLocation id = Nwyfre.id(name);
            if (recipes.stream().noneMatch(holder -> holder.id().equals(id))) {
                helper.fail("ritual recipe " + id + " did not load; a malformed recipe is skipped, not fatal");
            }
        }

        // clear_skies gates on a tag-form block_at prerequisite, and vigil_of_mending is the
        // persistent example. Both forms are unreachable from the headless suite, so this is the
        // only place they are proven to parse.
        RitualRecipe clearSkies = recipes.stream()
                .filter(holder -> holder.id().equals(Nwyfre.id("ritual/clear_skies")))
                .map(RecipeHolder::value)
                .findFirst()
                .orElse(null);
        if (clearSkies == null || clearSkies.prerequisites().isEmpty()) {
            helper.fail("clear_skies should carry prerequisites, including a tag-form block_at term");
            return;
        }

        RitualRecipe vigil = recipes.stream()
                .filter(holder -> holder.id().equals(Nwyfre.id("ritual/vigil_of_mending")))
                .map(RecipeHolder::value)
                .findFirst()
                .orElseThrow();
        if (!vigil.timing().persistent()) {
            helper.fail("vigil_of_mending should be a persistent ritual");
        }
        if (!vigil.effect().supportsPersistent()) {
            helper.fail("a persistent recipe must not load with an effect that cannot repeat");
        }

        helper.succeed();
    }
}
