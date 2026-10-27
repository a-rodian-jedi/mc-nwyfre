package com.nwyfre.nwyfre;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

import com.nwyfre.nwyfre.registry.NwyfreAttachments;

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
    }
}
