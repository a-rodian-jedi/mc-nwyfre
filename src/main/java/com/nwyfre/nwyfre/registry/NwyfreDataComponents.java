package com.nwyfre.nwyfre.registry;

import java.util.function.Supplier;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.ritual.ImbuedRitual;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Data Components for ritual items.
 *
 * <p>Components, not legacy NBT: CLAUDE.md requires it on 1.21, and here it is also what makes an
 * imbued candle a portable thing rather than a property of one altar.
 */
public final class NwyfreDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Nwyfre.MODID);

    /** Marks a candle as carrying a persistent ritual. */
    public static final Supplier<DataComponentType<ImbuedRitual>> IMBUED_RITUAL =
            DATA_COMPONENT_TYPES.register("imbued_ritual", () -> DataComponentType.<ImbuedRitual>builder()
                    .persistent(ImbuedRitual.CODEC)
                    .networkSynchronized(ImbuedRitual.STREAM_CODEC)
                    .build());

    private NwyfreDataComponents() {
    }
}
