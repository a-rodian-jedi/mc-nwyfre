package com.nwyfre.nwyfre.datagen;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.nwyfre.nwyfre.registry.NwyfreBlocks;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Loot tables for the ritual blocks. All three drop themselves; the inventory of the altar and the
 * bowl is dropped by the blocks themselves in {@code onRemove}, not by loot.
 */
public class NwyfreBlockLootSubProvider extends BlockLootSubProvider {

    public NwyfreBlockLootSubProvider(HolderLookup.Provider registries) {
        super(Set.<Item>of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        dropSelf(NwyfreBlocks.RITUAL_ALTAR.get());
        dropSelf(NwyfreBlocks.RITUAL_BOWL.get());
        dropSelf(NwyfreBlocks.CHALK_GLYPH.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return NwyfreBlocks.all().stream().map(DeferredBlock::get).map(block -> (Block) block).toList();
    }
}
