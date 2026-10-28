package com.nwyfre.nwyfre.ritual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nwyfre.nwyfre.Config;
import com.nwyfre.nwyfre.registry.NwyfreRecipes;
import com.nwyfre.nwyfre.ritual.pattern.RitualPattern;
import com.nwyfre.nwyfre.ritual.prereq.RitualPrerequisite;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * A ritual, entirely as data.
 *
 * <p>This is the heart of the Occultism split Nwyfre adopts: everything about <em>a</em> ritual
 * lives in a datapack file, and only the <em>kinds</em> of thing a ritual can do live in Java. A
 * pack author adds rituals with no compilation; they reach for Java only to add a new effect or a
 * new prerequisite term.
 *
 * <p>Fields are grouped into records so the codec stays readable rather than becoming a wall of
 * fifteen positional arguments.
 *
 * <p>It is a vanilla {@link Recipe} purely to inherit datapack loading, reload and client sync.
 * None of the vanilla crafting contract applies: {@link #matches(SingleRecipeInput, Level)} always
 * returns false, and real matching goes through {@link #matches(RitualPattern, java.util.Map,
 * ItemStack)}. {@link #isSpecial()} keeps it out of the recipe book.
 */
public record RitualRecipe(Placement placement, Timing timing, RitualDemands demands,
        Optional<RitualPrerequisite> prerequisites, boolean sustainPrerequisites,
        RitualEffect effect, List<RitualAugment> augments, ItemStack icon)
        implements Recipe<SingleRecipeInput> {

    /** What must be built and placed, and what starts it. */
    public record Placement(ResourceKey<RitualPattern> pattern, Ingredient activationItem,
            Map<ResourceKey<Element>, Ingredient> offerings) {

        public static final MapCodec<Placement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ResourceKey.codec(RitualPattern.REGISTRY).fieldOf("pattern").forGetter(Placement::pattern),
                        Ingredient.CODEC.fieldOf("activation_item").forGetter(Placement::activationItem),
                        Codec.unboundedMap(ResourceKey.codec(RitualPattern.ELEMENT_REGISTRY), Ingredient.CODEC)
                                .optionalFieldOf("offerings", Map.of()).forGetter(Placement::offerings))
                .apply(instance, Placement::new));

        public Placement(ResourceKey<RitualPattern> pattern, Ingredient activationItem,
                Map<ResourceKey<Element>, Ingredient> offerings) {
            this.pattern = pattern;
            this.activationItem = activationItem;
            this.offerings = Map.copyOf(offerings);
        }
    }

    /** How long it takes, and whether it leaves a lit candle behind. */
    public record Timing(int duration, boolean persistent, int burnTicks) {

        public static final MapCodec<Timing> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.intRange(1, 1_000_000).optionalFieldOf("duration", 100).forGetter(Timing::duration),
                        Codec.BOOL.optionalFieldOf("persistent", false).forGetter(Timing::persistent),
                        Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("burn_ticks", 24000)
                                .forGetter(Timing::burnTicks))
                .apply(instance, Timing::new));

        /** Duration after the server-wide multiplier, never less than a single tick. */
        public int scaledDuration() {
            return Math.max(1, (int) Math.round(duration * Config.RITUALS.durationMultiplier.get()));
        }
    }

    private static final MapCodec<RitualRecipe> RAW_MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Placement.CODEC.forGetter(RitualRecipe::placement),
                    Timing.CODEC.forGetter(RitualRecipe::timing),
                    RitualDemands.CODEC.forGetter(RitualRecipe::demands),
                    RitualPrerequisite.CODEC.optionalFieldOf("prerequisites")
                            .forGetter(RitualRecipe::prerequisites),
                    Codec.BOOL.optionalFieldOf("sustain_prerequisites", false)
                            .forGetter(RitualRecipe::sustainPrerequisites),
                    RitualEffect.CODEC.fieldOf("effect").forGetter(RitualRecipe::effect),
                    RitualAugment.CODEC.listOf().optionalFieldOf("augments", List.of())
                            .forGetter(RitualRecipe::augments),
                    ItemStack.OPTIONAL_CODEC.optionalFieldOf("icon", ItemStack.EMPTY)
                            .forGetter(RitualRecipe::icon))
            .apply(instance, RitualRecipe::new));

    /** What the recipe serializer wants: the recipe object fields, without a wrapper. */
    public static final MapCodec<RitualRecipe> MAP_CODEC = RAW_MAP_CODEC.validate(RitualRecipe::validate);

    public static final Codec<RitualRecipe> CODEC = MAP_CODEC.codec();

    /**
     * Catches at load time the mistakes that would otherwise only show up in play.
     *
     * <p>A persistent ritual whose effect cannot sensibly repeat is the main one: a candle handing
     * out an item every twenty ticks is a pack author error, not an intention.
     */
    private static DataResult<RitualRecipe> validate(RitualRecipe recipe) {
        List<String> problems = new ArrayList<>();

        if (recipe.timing.persistent() && !recipe.effect.supportsPersistent()) {
            problems.add("effect cannot be used as a persistent ritual");
        }
        recipe.augments.stream()
                .filter(augment -> augment.requires().isEmpty())
                .findAny()
                .ifPresent(augment -> problems.add("an augment with no requires would match every ritual"));
        recipe.augments.stream()
                .flatMap(augment -> augment.effect().stream())
                .filter(effect -> recipe.timing.persistent() && !effect.supportsPersistent())
                .findAny()
                .ifPresent(effect -> problems.add(
                        "an augment replaces the effect with one that cannot be persistent"));

        return problems.isEmpty()
                ? DataResult.success(recipe)
                : DataResult.error(() -> "Invalid ritual recipe: " + String.join("; ", problems));
    }

    // ---- ritual matching -------------------------------------------------------------------

    /**
     * Whether this recipe is the one being attempted.
     *
     * <p>The pattern is checked by the caller, which already had to match it to find the offering
     * slots; this covers the activation item and the offerings.
     *
     * @param pattern the pattern this recipe names, already resolved
     * @param bowlContents what is actually sitting in each of the pattern's offering bowls
     * @param activationItem what the player is holding
     */
    public boolean matches(RitualPattern pattern, Map<ResourceKey<Element>, ItemStack> bowlContents,
            ItemStack activationItem) {
        return placement.activationItem().test(activationItem) && offeringsSatisfied(pattern, bowlContents);
    }

    /**
     * Whether the bowls hold what this recipe asks for.
     *
     * <p>Under {@code strictOfferings} a bowl the pattern defines but this recipe does not name must
     * be <em>empty</em>. Without that rule two similar rituals become ambiguous -- a circle laid out
     * for the four-element rite would also satisfy a two-element one -- and which fires would come
     * down to recipe iteration order.
     */
    public boolean offeringsSatisfied(RitualPattern pattern, Map<ResourceKey<Element>, ItemStack> bowlContents) {
        for (Map.Entry<ResourceKey<Element>, Ingredient> wanted : placement.offerings().entrySet()) {
            ItemStack inBowl = bowlContents.getOrDefault(wanted.getKey(), ItemStack.EMPTY);
            if (!wanted.getValue().test(inBowl)) {
                return false;
            }
        }

        if (!Config.RITUALS.strictOfferings.get()) {
            return true;
        }

        for (ResourceKey<Element> element : pattern.elements()) {
            if (placement.offerings().containsKey(element)) {
                continue;
            }
            if (!bowlContents.getOrDefault(element, ItemStack.EMPTY).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Elements this recipe wants an offering for, in no particular order. */
    public java.util.Set<ResourceKey<Element>> offeringElements() {
        return placement.offerings().keySet();
    }

    /**
     * Which elements are unsatisfied, and what each wanted. Drives the diagnostics.
     *
     * @return element to the ingredient it is missing, empty when the offerings are complete
     */
    public Map<ResourceKey<Element>, Ingredient> missingOfferings(
            Map<ResourceKey<Element>, ItemStack> bowlContents) {
        Map<ResourceKey<Element>, Ingredient> missing = new HashMap<>();
        placement.offerings().forEach((element, wanted) -> {
            if (!wanted.test(bowlContents.getOrDefault(element, ItemStack.EMPTY))) {
                missing.put(element, wanted);
            }
        });
        return missing;
    }

    /**
     * Bowls holding something this recipe did not ask for, which only matters in strict mode.
     *
     * @return the elements whose bowls should be emptied
     */
    public List<ResourceKey<Element>> unwantedOfferings(RitualPattern pattern,
            Map<ResourceKey<Element>, ItemStack> bowlContents) {
        if (!Config.RITUALS.strictOfferings.get()) {
            return List.of();
        }
        return pattern.elements().stream()
                .filter(element -> !placement.offerings().containsKey(element))
                .filter(element -> !bowlContents.getOrDefault(element, ItemStack.EMPTY).isEmpty())
                .toList();
    }

    /**
     * Ticks between offerings being consumed.
     *
     * <p>The {@code +1} leaves a beat at the end with nothing left to take, so the final offering
     * does not vanish at the same instant the ritual completes.
     */
    public float ticksPerOffering() {
        return (float) timing.scaledDuration() / (placement.offerings().size() + 1);
    }

    /** The effect to use, after any augment has had its say. */
    public RitualEffect effectWith(Optional<RitualAugment> augment) {
        return augment.flatMap(RitualAugment::effect).orElse(effect);
    }

    /** Additive strength contributed by the matched augment. */
    public int potencyWith(Optional<RitualAugment> augment) {
        return augment.map(RitualAugment::potency).orElse(0);
    }

    // ---- vanilla Recipe contract, deliberately inert ---------------------------------------

    @Override
    public boolean matches(@NotNull SingleRecipeInput input, @NotNull Level level) {
        // A ritual is matched against the world, not a container. See the other matches().
        return false;
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull SingleRecipeInput input,
            net.minecraft.core.HolderLookup.@NotNull Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public @NotNull ItemStack getResultItem(net.minecraft.core.HolderLookup.@NotNull Provider registries) {
        return icon;
    }

    /** Keeps it out of the vanilla recipe book, which has no idea what a ritual is. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return NwyfreRecipes.RITUAL_SERIALIZER.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return NwyfreRecipes.RITUAL_TYPE.get();
    }
}
