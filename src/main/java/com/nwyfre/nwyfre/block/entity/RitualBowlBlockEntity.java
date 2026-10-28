package com.nwyfre.nwyfre.block.entity;

import com.nwyfre.nwyfre.registry.NwyfreBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds a single offering. Deliberately dumb: the bowl does not know which element it stands for.
 * That is decided by the {@code RitualPattern} cell it happens to sit under, which is what lets one
 * bowl block serve every element and lets v1 add material variants without touching this class.
 */
public class RitualBowlBlockEntity extends SingleSlotBlockEntity {

    public RitualBowlBlockEntity(BlockPos pos, BlockState state) {
        super(NwyfreBlockEntities.RITUAL_BOWL.get(), pos, state);
    }
}
