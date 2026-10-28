package com.nwyfre.nwyfre.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared plumbing for the two ritual block entities that each hold exactly one stack: the bowl
 * (its offering) and the altar (its activation item).
 *
 * <p>The contents are synced to clients because both blocks render what they hold. Subclasses
 * override {@link #createHandler()} to add behaviour on insertion.
 */
public abstract class SingleSlotBlockEntity extends BlockEntity {

    protected ItemStackHandler itemHandler;

    protected SingleSlotBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.itemHandler = createHandler();
    }

    protected ItemStackHandler createHandler() {
        return new ItemStackHandler(1) {
            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                contentsChanged();
            }
        };
    }

    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    public ItemStack getHeld() {
        return itemHandler.getStackInSlot(0);
    }

    public void setHeld(ItemStack stack) {
        itemHandler.setStackInSlot(0, stack);
    }

    /** Marks the block entity dirty and pushes the new contents to watching clients. */
    protected void contentsChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inventory")) {
            itemHandler.deserializeNBT(registries, tag.getCompound("inventory"));
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", itemHandler.serializeNBT(registries));
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(@NotNull Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
