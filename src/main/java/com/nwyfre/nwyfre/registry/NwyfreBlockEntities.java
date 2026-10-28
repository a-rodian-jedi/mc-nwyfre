package com.nwyfre.nwyfre.registry;

import java.util.function.Supplier;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.block.entity.RitualAltarBlockEntity;
import com.nwyfre.nwyfre.block.entity.RitualBowlBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class NwyfreBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Nwyfre.MODID);

    public static final Supplier<BlockEntityType<RitualAltarBlockEntity>> RITUAL_ALTAR =
            BLOCK_ENTITY_TYPES.register("ritual_altar", () -> BlockEntityType.Builder
                    .of(RitualAltarBlockEntity::new, NwyfreBlocks.RITUAL_ALTAR.get())
                    .build(null));

    public static final Supplier<BlockEntityType<RitualBowlBlockEntity>> RITUAL_BOWL =
            BLOCK_ENTITY_TYPES.register("ritual_bowl", () -> BlockEntityType.Builder
                    .of(RitualBowlBlockEntity::new, NwyfreBlocks.RITUAL_BOWL.get())
                    .build(null));

    private NwyfreBlockEntities() {
    }
}
