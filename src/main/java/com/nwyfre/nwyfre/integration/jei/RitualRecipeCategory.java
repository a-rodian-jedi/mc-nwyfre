package com.nwyfre.nwyfre.integration.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.nwyfre.nwyfre.registry.NwyfreItems;
import com.nwyfre.nwyfre.registry.NwyfreRegistries;
import com.nwyfre.nwyfre.ritual.Element;
import com.nwyfre.nwyfre.ritual.RitualAugment;
import com.nwyfre.nwyfre.ritual.RitualRecipe;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Shows a ritual in JEI, laid out the way the circle actually is.
 *
 * <p>Offering slots are placed on a <strong>compass</strong> rather than in a row, because where a
 * bowl sits is not decoration -- it is which element the offering is made to. A list of items would
 * lose the one piece of information a player most needs.
 */
public class RitualRecipeCategory implements IRecipeCategory<RecipeHolder<RitualRecipe>> {

    public static final RecipeType<RecipeHolder<RitualRecipe>> TYPE =
            RecipeType.createFromVanilla(com.nwyfre.nwyfre.registry.NwyfreRecipes.RITUAL_TYPE.get());

    private static final int WIDTH = 162;
    private static final int HEIGHT = 110;

    /** Centre of the compass, where the activation item sits. */
    private static final int CENTRE_X = 62;
    private static final int CENTRE_Y = 34;
    private static final int ARM = 20;

    private final IDrawable background;
    private final IDrawable icon;

    public RitualRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(NwyfreItems.RITUAL_ALTAR.get()));
    }

    @Override
    public RecipeType<RecipeHolder<RitualRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("nwyfre.jei.rituals");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<RitualRecipe> holder,
            IFocusGroup focuses) {
        RitualRecipe recipe = holder.value();

        // Centre: what you hold to begin.
        builder.addSlot(RecipeIngredientRole.CATALYST, CENTRE_X, CENTRE_Y)
                .addIngredients(recipe.placement().activationItem())
                .addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(Component.translatable("nwyfre.jei.token")
                                .withStyle(ChatFormatting.GRAY)));

        // The quarters, each at its element's compass position.
        recipe.placement().offerings().forEach((element, ingredient) -> {
            Direction direction = directionOf(element);
            int x = CENTRE_X + ARM * direction.getStepX();
            int y = CENTRE_Y + ARM * direction.getStepZ();
            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                    .addIngredients(ingredient)
                    .addRichTooltipCallback((view, tooltip) ->
                            tooltip.add(elementName(element).copy().withStyle(ChatFormatting.GRAY)));
        });

        // Output, if the ritual makes something. Many do not -- weather and time rituals have no
        // item at all, and showing an empty slot for them would be misleading.
        ItemStack result = recipe.icon();
        if (!result.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 134, CENTRE_Y).addItemStack(result);
        }

        // Augments along the bottom, so the panel shows that the ritual can be bent at all.
        int augmentX = 6;
        for (RitualAugment augment : recipe.augments()) {
            for (Ingredient required : augment.requires()) {
                builder.addSlot(RecipeIngredientRole.CATALYST, augmentX, 88)
                        .addIngredients(required)
                        .addRichTooltipCallback((view, tooltip) -> {
                            tooltip.add(Component.translatable("nwyfre.jei.augment")
                                    .withStyle(ChatFormatting.LIGHT_PURPLE));
                            augment.effect().ifPresent(effect -> tooltip.add(
                                    effect.describe().copy().withStyle(ChatFormatting.GRAY)));
                            if (augment.potency() != 0) {
                                tooltip.add(Component.translatable("nwyfre.jei.potency", augment.potency())
                                        .withStyle(ChatFormatting.GRAY));
                            }
                        });
                augmentX += 18;
                if (augmentX > WIDTH - 20) {
                    return;
                }
            }
        }
    }

    @Override
    public void draw(RecipeHolder<RitualRecipe> holder, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
            GuiGraphics graphics, double mouseX, double mouseY) {
        RitualRecipe recipe = holder.value();
        var font = Minecraft.getInstance().font;
        int y = 0;

        graphics.drawString(font, recipe.effect().describe(), 2, y, 0x404040, false);
        y += 10;

        // Prerequisites are shown as text, never used to hide the recipe (except stages -- see
        // NwyfreJeiPlugin). A night-only ritual must not vanish from JEI in the daytime.
        if (recipe.prerequisites().isPresent()) {
            graphics.drawString(font, recipe.prerequisites().get().describe(), 2, y, 0x707070, false);
            y += 10;
        }

        if (recipe.timing().persistent()) {
            graphics.drawString(font, Component.translatable("nwyfre.jei.persistent",
                    recipe.timing().burnTicks() / 20), 2, y, 0x7A3FA0, false);
            y += 10;
        }

        recipe.demands().sacrifice().ifPresent(sacrifice -> graphics.drawString(font,
                Component.translatable("nwyfre.jei.sacrifice", sacrifice.describe()),
                2, 66, 0x803030, false));
        recipe.demands().itemToUse().ifPresent(itemToUse -> graphics.drawString(font,
                Component.translatable("nwyfre.jei.item_to_use"), 2, 76, 0x803030, false));

        graphics.drawString(font, Component.translatable("nwyfre.jei.pattern",
                        patternName(recipe)), 2, 56, 0x707070, false);
    }

    @Override
    public List<Component> getTooltipStrings(RecipeHolder<RitualRecipe> holder,
            mezz.jei.api.gui.ingredient.IRecipeSlotsView slots, double mouseX, double mouseY) {
        // Full prerequisite detail on hover, since the drawn line is only a summary.
        RitualRecipe recipe = holder.value();
        if (recipe.prerequisites().isEmpty() || mouseY < 10 || mouseY > 20) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("nwyfre.jei.prerequisites").withStyle(ChatFormatting.GRAY));
        lines.add(recipe.prerequisites().get().describe());
        return lines;
    }

    @Override
    public net.minecraft.resources.ResourceLocation getRegistryName(RecipeHolder<RitualRecipe> holder) {
        return holder.id();
    }

    /**
     * The compass direction an element sits at.
     *
     * <p>Read from the synced element registry, which is why the registry declares a network codec.
     * Falls back to north so a pack element added without a direction still lays out somewhere
     * rather than crashing the panel.
     */
    private static Direction directionOf(ResourceKey<Element> element) {
        return elementRegistry()
                .flatMap(registry -> registry.getOptional(element))
                .map(Element::direction)
                .orElse(Direction.NORTH);
    }

    private static Component elementName(ResourceKey<Element> element) {
        return elementRegistry()
                .flatMap(registry -> registry.getOptional(element))
                .map(value -> value.displayNameWithDirection(element))
                .orElseGet(() -> Component.literal(element.location().toString()));
    }

    private static Optional<Registry<Element>> elementRegistry() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null
                ? Optional.empty()
                : Optional.of(NwyfreRegistries.elements(minecraft.level.registryAccess()));
    }

    private static Component patternName(RitualRecipe recipe) {
        var location = recipe.placement().pattern().location();
        return Component.translatableWithFallback(
                "nwyfre.pattern." + location.getNamespace() + "." + location.getPath(),
                location.toString());
    }

    /** Elements in a stable compass order, for anything that needs to list them. */
    static List<ResourceKey<Element>> inCompassOrder(Map<ResourceKey<Element>, Ingredient> offerings) {
        return offerings.keySet().stream()
                .sorted(Comparator.comparingInt(key -> directionOf(key).get2DDataValue()))
                .toList();
    }
}
