package com.nwyfre.nwyfre.registry;

import java.util.List;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.block.ChalkGlyphBlock;
import com.nwyfre.nwyfre.block.RitualAltarBlock;
import com.nwyfre.nwyfre.block.RitualBowlBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Blocks the ritual system is built from.
 *
 * <p>v0 ships the minimum viable set: one altar, one neutral bowl, one neutral glyph. Bowl
 * materials and elemental glyph variants are v1 content, and arrive as <em>additional</em> blocks
 * added into the ritual_bowls / chalk_glyphs tags. Ritual patterns match on block-or-tag
 * predicates, so adding them needs no change to the pattern format.
 */
public final class NwyfreBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Nwyfre.MODID);

    /** The centre of every ritual: holds the activation item and drives the whole lifecycle. */
    public static final DeferredBlock<RitualAltarBlock> RITUAL_ALTAR = BLOCKS.registerBlock(
            "ritual_altar",
            RitualAltarBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(RitualAltarBlock.LIT) ? 10 : 0));

    /** Holds one offering. Which element it stands for is decided by the pattern, not the block. */
    public static final DeferredBlock<RitualBowlBlock> RITUAL_BOWL = BLOCKS.registerBlock(
            "ritual_bowl",
            RitualBowlBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.5F)
                    .sound(SoundType.WOOD)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion());

    /** A drawn symbol. Purely a pattern component: no block entity, no behaviour. */
    public static final DeferredBlock<ChalkGlyphBlock> CHALK_GLYPH = BLOCKS.registerBlock(
            "chalk_glyph",
            ChalkGlyphBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ)
                    .instabreak()
                    .sound(SoundType.WOOL)
                    .noCollission()
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY));

    private NwyfreBlocks() {
    }

    /** Every block registered here, for datagen providers that iterate the whole set. */
    public static List<DeferredBlock<? extends Block>> all() {
        return List.of(RITUAL_ALTAR, RITUAL_BOWL, CHALK_GLYPH);
    }
}
