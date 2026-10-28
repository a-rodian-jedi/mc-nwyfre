package com.nwyfre.nwyfre.block;

import com.mojang.serialization.MapCodec;
import com.nwyfre.nwyfre.block.entity.RitualBowlBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds one offering for a ritual.
 *
 * <p>Right-click with an item to put it in, right-click empty-handed to take it back, and
 * right-click a full bowl with something else to swap. Exactly one item at a time, which is what
 * makes the per-element offering model readable: a bowl either holds the right thing or it does not.
 *
 * <p>An offering is matched by <em>kind</em>, never by count, so one coal is as good as sixty-four
 * -- there is deliberately no way to stack an offering, and no need to.
 */
public class RitualBowlBlock extends BaseEntityBlock {

    public static final MapCodec<RitualBowlBlock> CODEC = simpleCodec(RitualBowlBlock::new);

    private static final VoxelShape SHAPE = Shapes.join(
            Block.box(2.0, 0.0, 2.0, 14.0, 5.0, 14.0),
            Block.box(3.0, 2.0, 3.0, 13.0, 5.0, 13.0),
            BooleanOp.ONLY_FIRST);

    public RitualBowlBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new RitualBowlBlockEntity(pos, state);
    }

    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
            @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state,
            @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
            @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RitualBowlBlockEntity bowl)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // An empty hand passes through to useWithoutItem, which is the single take-out path.
        // That route is reached ONLY via PASS_TO_DEFAULT_BLOCK_INTERACTION, hand empty or not.
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide()) {
            // Holding an item: fill an empty bowl, or swap a full one. Falling through instead is
            // what used to throw the offering back out, which reads as the bowl rejecting it.
            ItemStack previous = bowl.getHeld().copy();
            bowl.setHeld(stack.split(1));
            if (!previous.isEmpty() && !player.addItem(previous)) {
                player.drop(previous, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 1.2F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
            @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RitualBowlBlockEntity bowl) || bowl.getHeld().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack taken = bowl.getHeld().copy();
            bowl.setHeld(ItemStack.EMPTY);
            if (!player.addItem(taken)) {
                player.drop(taken, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 1.2F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
            @NotNull BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof RitualBowlBlockEntity bowl) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), bowl.getHeld());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
