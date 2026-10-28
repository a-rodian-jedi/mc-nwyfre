package com.nwyfre.nwyfre.integration.jei;

import java.util.ArrayList;
import java.util.List;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.registry.NwyfreItems;
import com.nwyfre.nwyfre.registry.NwyfreRecipes;
import com.nwyfre.nwyfre.ritual.RitualRecipe;
import com.nwyfre.nwyfre.ritual.prereq.RitualPrerequisite;
import com.nwyfre.nwyfre.ritual.prereq.StagePrerequisite;
import com.nwyfre.nwyfre.stage.PlayerStages;
import com.nwyfre.nwyfre.stage.Stages;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * JEI integration: a ritual category, and stage-aware filtering.
 *
 * <p>Client-only and loaded solely through the {@code @JeiPlugin} annotation JEI scans for, so
 * nothing in Nwyfre references this class (CLAUDE.md).
 *
 * <p><strong>Stages hide, everything else informs.</strong> A ritual whose stage requirements are
 * unmet is withheld entirely -- that is CLAUDE.md's stage-aware JEI rule, and it keeps unobtained
 * tiers out of a player's search results. Every other prerequisite is displayed as text instead: a
 * night-only ritual must not disappear from JEI in the daytime, because the player has not failed
 * to progress, they have merely looked at the wrong hour.
 */
@JeiPlugin
public class NwyfreJeiPlugin implements IModPlugin {

    private static final ResourceLocation ID = Nwyfre.id("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new RitualRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        List<RecipeHolder<RitualRecipe>> visible = new ArrayList<>(
                minecraft.level.getRecipeManager().getAllRecipesFor(NwyfreRecipes.RITUAL_TYPE.get()));
        visible.removeIf(NwyfreJeiPlugin::hiddenByStages);

        registration.addRecipes(RitualRecipeCategory.TYPE, visible);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // The altar is how a player finds the category: look up the altar, see every rite.
        registration.addRecipeCatalyst(new ItemStack(NwyfreItems.RITUAL_ALTAR.get()),
                RitualRecipeCategory.TYPE);
    }

    /**
     * Whether this ritual should be withheld because the player lacks its stages.
     *
     * <p>Reads the client's synced stage set, which is exactly what it is for: display and
     * filtering. Authority stays server-side, so hiding a recipe is cosmetic and cannot be used to
     * bypass anything.
     */
    private static boolean hiddenByStages(RecipeHolder<RitualRecipe> holder) {
        List<StagePrerequisite> gates = new ArrayList<>();
        holder.value().prerequisites().ifPresent(tree -> collectStageTerms(tree, gates));
        if (gates.isEmpty()) {
            return false;
        }

        var player = Minecraft.getInstance().player;
        if (player == null) {
            // No player to ask. Showing the recipe is the safer default: a missing panel reads as
            // a broken mod, while an extra one reads as a ritual you have not unlocked.
            return false;
        }

        PlayerStages stages = Stages.get(player);
        return gates.stream().anyMatch(gate -> gate.mode() == StagePrerequisite.Mode.ALL
                ? !stages.hasAll(gate.stages())
                : !stages.hasAny(gate.stages()));
    }

    /** Walks the prerequisite tree for stage terms, wherever they are nested. */
    private static void collectStageTerms(RitualPrerequisite term, List<StagePrerequisite> out) {
        switch (term) {
            case StagePrerequisite stage -> out.add(stage);
            case RitualPrerequisite.AllOf allOf ->
                    allOf.terms().forEach(child -> collectStageTerms(child, out));
            // Not descended into deliberately. Inside any_of a stage term is one alternative among
            // several, and inside not it is an inverted requirement; in neither case does an unmet
            // stage mean the ritual is unreachable, so hiding would be wrong.
            default -> {
            }
        }
    }
}
