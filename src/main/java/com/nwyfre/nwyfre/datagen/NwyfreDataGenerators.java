package com.nwyfre.nwyfre.datagen;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.nwyfre.nwyfre.Nwyfre;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Datagen entry point. Output lands in {@code src/generated/resources}, which ships in the jar and
 * must never be hand-edited (CLAUDE.md). Run with {@code ./gradlew runData}.
 */
@EventBusSubscriber(modid = Nwyfre.MODID)
public final class NwyfreDataGenerators {

    private NwyfreDataGenerators() {
    }

    @SubscribeEvent
    static void onGatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper helper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();

        generator.addProvider(event.includeClient(), new NwyfreBlockStateProvider(output, helper));
        generator.addProvider(event.includeClient(),
                new NwyfreParticleDefinitionProvider(output, helper));

        generator.addProvider(event.includeServer(), new NwyfreBlockTagsProvider(output, lookup, helper));
        generator.addProvider(event.includeServer(), new LootTableProvider(
                output,
                Set.<ResourceKey<LootTable>>of(),
                List.of(new LootTableProvider.SubProviderEntry(
                        NwyfreBlockLootSubProvider::new, LootContextParamSets.BLOCK)),
                lookup));
    }
}
