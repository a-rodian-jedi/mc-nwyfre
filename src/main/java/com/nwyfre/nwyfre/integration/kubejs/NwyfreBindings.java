package com.nwyfre.nwyfre.integration.kubejs;

import java.util.List;
import java.util.Optional;

import com.nwyfre.nwyfre.registry.NwyfrePrerequisites;
import com.nwyfre.nwyfre.registry.NwyfreRecipes;
import com.nwyfre.nwyfre.registry.NwyfreRegistries;
import com.nwyfre.nwyfre.registry.NwyfreRitualEffects;
import com.nwyfre.nwyfre.stage.Stages;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The {@code Nwyfre} object KubeJS scripts see.
 *
 * <p>Writing rituals needs none of this -- the recipe type is a normal recipe type, so
 * {@code event.custom({...})} works with no integration at all. This exists for the other half of
 * pack scripting: reading and changing progression, and discovering what ritual vocabulary is
 * available so a script can be written against it.
 *
 * <p>Every mutating call goes through {@link Stages}, never the attachment, so persistence, client
 * sync and the cancellable change event all still happen (CLAUDE.md).
 */
public class NwyfreBindings {

    // ---- stages ------------------------------------------------------------------------------

    /** Safe on both sides, though a client only knows what has been synced to it. */
    public boolean hasStage(Player player, String stage) {
        ResourceLocation id = ResourceLocation.tryParse(stage);
        return id != null && Stages.has(player, id);
    }

    /** @return whether the player's stages actually changed */
    public boolean grantStage(ServerPlayer player, String stage) {
        ResourceLocation id = ResourceLocation.tryParse(stage);
        return id != null && Stages.grant(player, id);
    }

    /** @return whether the player's stages actually changed */
    public boolean revokeStage(ServerPlayer player, String stage) {
        ResourceLocation id = ResourceLocation.tryParse(stage);
        return id != null && Stages.revoke(player, id);
    }

    /** @return how many stages were actually removed */
    public int clearStages(ServerPlayer player) {
        return Stages.clear(player);
    }

    public List<String> getStages(Player player) {
        return Stages.get(player).view().stream().map(ResourceLocation::toString).toList();
    }

    public boolean hasAllStages(Player player, List<String> stages) {
        return Stages.get(player).hasAll(parse(stages));
    }

    public boolean hasAnyStage(Player player, List<String> stages) {
        return Stages.get(player).hasAny(parse(stages));
    }

    // ---- ritual vocabulary -------------------------------------------------------------------

    /**
     * Every ritual effect type a recipe may name.
     *
     * <p>Worth exposing because the set is extensible: another mod can add effects, so a script
     * cannot assume the built-in six are all there is.
     */
    public List<String> getRitualEffectTypes() {
        return NwyfreRitualEffects.REGISTRY.keySet().stream().map(ResourceLocation::toString).sorted().toList();
    }

    /** Every prerequisite term type a recipe may name. */
    public List<String> getRitualPrerequisiteTypes() {
        return NwyfrePrerequisites.REGISTRY.keySet().stream().map(ResourceLocation::toString).sorted().toList();
    }

    /** Patterns currently loaded, so a script can check a circle exists before referencing it. */
    public List<String> getRitualPatterns(ServerLevel level) {
        return NwyfreRegistries.patterns(level.registryAccess()).keySet().stream()
                .map(ResourceLocation::toString).sorted().toList();
    }

    public List<String> getElements(ServerLevel level) {
        return NwyfreRegistries.elements(level.registryAccess()).keySet().stream()
                .map(ResourceLocation::toString).sorted().toList();
    }

    /** Ritual recipe ids currently loaded. Remember these include the subfolder. */
    public List<String> getRituals(ServerLevel level) {
        return level.getServer().getRecipeManager()
                .getAllRecipesFor(NwyfreRecipes.RITUAL_TYPE.get()).stream()
                .map(RecipeHolder::id)
                .map(ResourceLocation::toString)
                .sorted()
                .toList();
    }

    /** Whether a ritual exists, for a script that wants to fail loudly rather than silently. */
    public boolean ritualExists(ServerLevel level, String recipeId) {
        ResourceLocation id = ResourceLocation.tryParse(recipeId);
        return id != null && level.getServer().getRecipeManager()
                .getAllRecipesFor(NwyfreRecipes.RITUAL_TYPE.get()).stream()
                .anyMatch(holder -> holder.id().equals(id));
    }

    private static List<ResourceLocation> parse(List<String> ids) {
        return ids.stream()
                .map(ResourceLocation::tryParse)
                .map(Optional::ofNullable)
                .flatMap(Optional::stream)
                .toList();
    }
}
