package com.nwyfre.nwyfre.integration.patchouli;

import com.nwyfre.nwyfre.Nwyfre;

import net.minecraft.core.RegistryAccess;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Hooks the generated pattern previews up to the game.
 *
 * <p>Guarded at runtime with {@code ModList.isLoaded}, and every Patchouli type is reached only
 * from inside {@link RitualPatternMultiblocks}, so that class is never loaded when Patchouli is
 * absent (CLAUDE.md).
 *
 * <p>Two hooks, because patterns are a datapack registry and so only exist once something has
 * loaded them: {@link ServerStartedEvent} covers world load, and {@link OnDatapackSyncEvent} covers
 * {@code /reload} and a client receiving synced registries. Registering the same ids again is how
 * an edited pattern shows its new shape.
 */
@EventBusSubscriber(modid = Nwyfre.MODID)
public final class NwyfrePatchouliIntegration {

    private static final String PATCHOULI = "patchouli";

    private NwyfrePatchouliIntegration() {
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        register(event.getServer().registryAccess());
    }

    @SubscribeEvent
    static void onDatapackSync(OnDatapackSyncEvent event) {
        register(event.getPlayerList().getServer().registryAccess());
    }

    private static void register(RegistryAccess registryAccess) {
        if (!ModList.get().isLoaded(PATCHOULI)) {
            return;
        }
        int count = RitualPatternMultiblocks.registerAll(registryAccess);
        Nwyfre.LOGGER.debug("Registered {} ritual pattern previews with Patchouli", count);
    }
}
