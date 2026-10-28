package com.nwyfre.nwyfre.block;

import com.mojang.serialization.MapCodec;
import com.nwyfre.nwyfre.block.entity.RitualAltarBlockEntity;
import com.nwyfre.nwyfre.registry.NwyfreBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The centre of every ritual circle.
 *
 * <p>LIT means two different things depending on what the altar holds, which is deliberate: for a
 * running one-shot ritual it reads as working, and for a persistent ritual it is the candle being
 * alight. Both read the same way to a player, and both emit light.
 */
public class RitualAltarBlock extends BaseEntityBlock {

    public static final MapCodec<RitualAltarBlock> CODEC = simpleCodec(RitualAltarBlock::new);

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1.0, 0.0, 1.0, 15.0, 3.0, 15.0),
            Block.box(3.0, 3.0, 3.0, 13.0, 11.0, 13.0),
            Block.box(1.0, 11.0, 1.0, 15.0, 14.0, 15.0));

    public RitualAltarBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new RitualAltarBlockEntity(pos, state);
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
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level,
            @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return createTickerHelper(type, NwyfreBlockEntities.RITUAL_ALTAR.get(), RitualAltarBlockEntity::tick);
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state,
            @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
            @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (hand == InteractionHand.OFF_HAND
                || !(level.getBlockEntity(pos) instanceof RitualAltarBlockEntity altar)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // An empty hand must pass through: useWithoutItem is reached ONLY via
        // PASS_TO_DEFAULT_BLOCK_INTERACTION, even when the player is holding nothing. Returning
        // anything else here would silently disable cancelling, toggling a candle, and taking an
        // item back -- all of which deliberately require an empty hand.
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // Holding something, and the altar is busy: do nothing at all. Falling through here is
        // what used to let a stray right-click with any item in hand call off a running rite.
        if (altar.isRunning() || !altar.getHeld().isEmpty()) {
            return ItemInteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            // Put the item in first, then try to start: the handler and the player path then go
            // through exactly the same code, so a hopper and a hand behave identically.
            ItemStack offered = stack.copyWithCount(1);
            altar.setHeld(offered);
            if (altar.tryStart(player instanceof ServerPlayer serverPlayer ? serverPlayer : null)) {
                stack.shrink(1);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 0.9F);
            } else {
                // Nothing matched. Hand it back rather than leaving it stuck in the altar, and let
                // the diagnostics explain why.
                altar.setHeld(ItemStack.EMPTY);
                altar.explainFailure(player instanceof ServerPlayer serverPlayer ? serverPlayer : null, offered);
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
            @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RitualAltarBlockEntity altar)) {
            return InteractionResult.PASS;
        }

        // One gesture, three meanings, disambiguated by what the altar is doing: a running ritual
        // is called off, an idle altar gives its contents back, and an empty one explains itself.
        if (altar.isRunning()) {
            if (!level.isClientSide()) {
                altar.cancel(player instanceof ServerPlayer serverPlayer ? serverPlayer : null);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        // An imbued candle is toggled rather than taken out, so the common gesture on a working
        // persistent ritual is the one that turns it off and on.
        if (altar.heldImbuedRitual() != null) {
            if (!level.isClientSide()) {
                altar.toggleLit((net.minecraft.server.level.ServerLevel) level);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (altar.getHeld().isEmpty()) {
            if (!level.isClientSide()) {
                altar.explainFailure(player instanceof ServerPlayer serverPlayer ? serverPlayer : null,
                        ItemStack.EMPTY);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (!level.isClientSide()) {
            ItemStack taken = altar.getHeld().copy();
            altar.setHeld(ItemStack.EMPTY);
            if (!player.addItem(taken)) {
                player.drop(taken, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 0.9F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /**
     * A redstone pulse starts a ready ritual.
     *
     * <p>Borrowed from the Ars Nouveau brazier. It costs almost nothing and it is what lets a pack
     * build an automated ritual without a dedicated machine.
     */
    @Override
    protected void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
            @NotNull Block neighbour, @NotNull BlockPos neighbourPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbour, neighbourPos, movedByPiston);
        if (!level.isClientSide()
                && level.hasNeighborSignal(pos)
                && level.getBlockEntity(pos) instanceof RitualAltarBlockEntity altar) {
            altar.tryStart(null);
        }
    }

    @Override
    protected void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
            @NotNull BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof RitualAltarBlockEntity altar) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), altar.getHeld());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
