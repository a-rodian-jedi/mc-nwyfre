package com.nwyfre.nwyfre.registry;

import com.nwyfre.nwyfre.Nwyfre;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Tags the ritual patterns match against.
 *
 * <p>Patterns reference these rather than concrete blocks, which is the whole reason v1 can add
 * basalt and gold bowls, or four elemental glyphs, without touching a single pattern file.
 */
public final class NwyfreTags {

    public static final class Blocks {

        /** Anything that can anchor a ritual circle. */
        public static final TagKey<Block> RITUAL_ALTARS = tag("ritual_altars");

        /** Anything that can hold an offering for a ritual. */
        public static final TagKey<Block> RITUAL_BOWLS = tag("ritual_bowls");

        /** Anything that counts as a drawn symbol. */
        public static final TagKey<Block> CHALK_GLYPHS = tag("chalk_glyphs");

        private static TagKey<Block> tag(String path) {
            return TagKey.create(Registries.BLOCK, Nwyfre.id(path));
        }

        private Blocks() {
        }
    }

    private NwyfreTags() {
    }
}
