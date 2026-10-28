package com.nwyfre.nwyfre.datagen;

import java.util.concurrent.CompletableFuture;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.registry.NwyfreBlocks;
import com.nwyfre.nwyfre.registry.NwyfreTags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Block tags. The two ritual tags are the extension points patterns match against; the vanilla
 * mining tags are ordinary housekeeping.
 */
public class NwyfreBlockTagsProvider extends BlockTagsProvider {

    public NwyfreBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
            @Nullable ExistingFileHelper helper) {
        super(output, lookup, Nwyfre.MODID, helper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(NwyfreTags.Blocks.RITUAL_ALTARS).add(NwyfreBlocks.RITUAL_ALTAR.get());
        tag(NwyfreTags.Blocks.RITUAL_BOWLS).add(NwyfreBlocks.RITUAL_BOWL.get());
        tag(NwyfreTags.Blocks.CHALK_GLYPHS).add(NwyfreBlocks.CHALK_GLYPH.get());

        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(NwyfreBlocks.RITUAL_ALTAR.get());
        tag(BlockTags.NEEDS_STONE_TOOL).add(NwyfreBlocks.RITUAL_ALTAR.get());
        tag(BlockTags.MINEABLE_WITH_AXE).add(NwyfreBlocks.RITUAL_BOWL.get());
    }
}
