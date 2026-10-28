package com.nwyfre.nwyfre;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.minecraft.resources.ResourceLocation;

import com.nwyfre.nwyfre.registry.NwyfreAttachments;
import com.nwyfre.nwyfre.registry.NwyfreBlockEntities;
import com.nwyfre.nwyfre.registry.NwyfreBlocks;
import com.nwyfre.nwyfre.registry.NwyfreCreativeTab;
import com.nwyfre.nwyfre.registry.NwyfreDataComponents;
import com.nwyfre.nwyfre.registry.NwyfreItems;
import com.nwyfre.nwyfre.registry.NwyfreParticles;
import com.nwyfre.nwyfre.registry.NwyfrePrerequisites;
import com.nwyfre.nwyfre.registry.NwyfreRecipes;
import com.nwyfre.nwyfre.registry.NwyfreRitualEffects;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Nwyfre.MODID)
public class Nwyfre {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "nwyfre";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Nwyfre(IEventBus modEventBus, ModContainer modContainer) {
        // Register the common config so NeoForge creates and loads nwyfre-common.toml.
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        NwyfreAttachments.ATTACHMENT_TYPES.register(modEventBus);

        // Blocks before items: the block items are derived from the blocks.
        NwyfreBlocks.BLOCKS.register(modEventBus);
        NwyfreItems.ITEMS.register(modEventBus);
        NwyfreBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        NwyfreParticles.PARTICLE_TYPES.register(modEventBus);
        NwyfreCreativeTab.CREATIVE_MODE_TABS.register(modEventBus);
        NwyfrePrerequisites.PREREQUISITES.register(modEventBus);
        NwyfreRitualEffects.EFFECTS.register(modEventBus);
        NwyfreRecipes.RECIPE_TYPES.register(modEventBus);
        NwyfreRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        NwyfreDataComponents.DATA_COMPONENT_TYPES.register(modEventBus);
    }

    /** Shorthand for a {@code nwyfre:}-namespaced id. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
