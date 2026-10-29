package com.nwyfre.nwyfre.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.block.ChalkGlyphBlock;
import com.nwyfre.nwyfre.block.RitualAltarBlock;
import com.nwyfre.nwyfre.block.RitualBowlBlock;
import com.nwyfre.nwyfre.block.ogham.OghamGlyphBlock;
import com.nwyfre.nwyfre.block.ogham.OghamLetter;

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
            chalkProperties());

    /**
     * A bare stem with no character on it. Punctuation: it carries the line between words and out
     * to the ends of an inscription, which real Ogham does with the edge of the stone itself.
     */
    public static final DeferredBlock<OghamGlyphBlock> OGHAM_STEM = BLOCKS.registerBlock(
            "ogham_stem",
            OghamGlyphBlock::new,
            chalkProperties());

    /** One block per character, so a ritual pattern can demand a specific letter. */
    public static final Map<OghamLetter, DeferredBlock<OghamGlyphBlock>> OGHAM = registerOgham();

    private NwyfreBlocks() {
    }

    private static Map<OghamLetter, DeferredBlock<OghamGlyphBlock>> registerOgham() {
        Map<OghamLetter, DeferredBlock<OghamGlyphBlock>> blocks = new EnumMap<>(OghamLetter.class);
        for (OghamLetter letter : OghamLetter.values()) {
            blocks.put(letter, BLOCKS.registerBlock(
                    letter.blockName(), OghamGlyphBlock::new, chalkProperties()));
        }
        return Collections.unmodifiableMap(blocks);
    }

    /**
     * Fresh properties per block. These are mutable builders, so handing the same instance to two
     * blocks makes them share state -- always build a new one.
     */
    private static BlockBehaviour.Properties chalkProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.QUARTZ)
                .instabreak()
                .sound(SoundType.WOOL)
                .noCollission()
                .noOcclusion()
                .pushReaction(PushReaction.DESTROY);
    }

    /** Every glyph block, in writing order: the bare stem, then the alphabet. */
    public static List<DeferredBlock<OghamGlyphBlock>> oghamGlyphs() {
        List<DeferredBlock<OghamGlyphBlock>> glyphs = new ArrayList<>();
        glyphs.add(OGHAM_STEM);
        for (OghamLetter letter : OghamLetter.values()) {
            glyphs.add(OGHAM.get(letter));
        }
        return List.copyOf(glyphs);
    }

    /** Every block registered here, for datagen providers that iterate the whole set. */
    public static List<DeferredBlock<? extends Block>> all() {
        List<DeferredBlock<? extends Block>> blocks = new ArrayList<>(
                List.of(RITUAL_ALTAR, RITUAL_BOWL, CHALK_GLYPH));
        blocks.addAll(oghamGlyphs());
        return List.copyOf(blocks);
    }
}
