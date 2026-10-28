package com.nwyfre.nwyfre.registry;

import com.nwyfre.nwyfre.Nwyfre;

import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Items the ritual system ships.
 *
 * <p>v0 has no bespoke items: the three ritual blocks get plain block items and everything a ritual
 * consumes is vanilla. The chalk glyph block item in particular is how a glyph gets drawn in v0; v1
 * replaces it with a real chalk item that places glyphs on use and carries an element. See
 * {@code specs/rituals.md}, Placeholders.
 */
public final class NwyfreItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Nwyfre.MODID);

    public static final DeferredItem<BlockItem> RITUAL_ALTAR = ITEMS.registerSimpleBlockItem(NwyfreBlocks.RITUAL_ALTAR);
    public static final DeferredItem<BlockItem> RITUAL_BOWL = ITEMS.registerSimpleBlockItem(NwyfreBlocks.RITUAL_BOWL);
    public static final DeferredItem<BlockItem> CHALK_GLYPH = ITEMS.registerSimpleBlockItem(NwyfreBlocks.CHALK_GLYPH);

    private NwyfreItems() {
    }
}
