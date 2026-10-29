package com.nwyfre.nwyfre.block.ogham;

import com.mojang.serialization.MapCodec;
import com.nwyfre.nwyfre.block.ChalkGlyphBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One drawn Ogham character.
 *
 * <p>Inherits the flat-on-the-ground behaviour of a {@link ChalkGlyphBlock} and adds the one thing
 * writing needs: a <strong>stem axis</strong>. Every glyph texture carries its stem across the full
 * width of the tile at the exact centre, so glyphs sharing an axis join into one unbroken line the
 * way real Ogham runs along the edge of a stone.
 *
 * <p>There is no letter field. Which character this is comes from the registry id, and the one
 * blockstate property is the only thing that varies in play -- so a pattern can demand a specific
 * letter with {@code nwyfre:block} or any glyph at all with {@code nwyfre:tag}, and neither has to
 * care which way the writing runs.
 */
public class OghamGlyphBlock extends ChalkGlyphBlock {

    public static final MapCodec<OghamGlyphBlock> CODEC = simpleCodec(OghamGlyphBlock::new);

    /** Which way the stem runs. The texture is drawn for X; Z is the same model turned 90°. */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    public OghamGlyphBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected @NotNull MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, axisAt(context.getLevel(), context.getClickedPos(),
                context.getHorizontalDirection().getAxis()));
    }

    @Override
    protected @NotNull BlockState rotate(@NotNull BlockState state, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> state.setValue(AXIS,
                    state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
            default -> state;
        };
    }

    /**
     * The axis a glyph placed here should take.
     *
     * <p>Joins a line already running through this position in preference to anything else, so
     * writing a word is a matter of walking along placing glyphs. A neighbour whose own stem runs
     * <em>across</em> our shared axis is a different line passing by and is deliberately ignored.
     */
    private static Direction.Axis axisAt(BlockGetter level, BlockPos pos, Direction.Axis fallback) {
        return chooseAxis(
                continues(level, pos, Direction.WEST) || continues(level, pos, Direction.EAST),
                continues(level, pos, Direction.NORTH) || continues(level, pos, Direction.SOUTH),
                fallback);
    }

    /** Whether the neighbour that way is a stem already running in that same direction. */
    private static boolean continues(BlockGetter level, BlockPos pos, Direction direction) {
        BlockState neighbour = level.getBlockState(pos.relative(direction));
        return neighbour.hasProperty(AXIS) && neighbour.getValue(AXIS) == direction.getAxis();
    }

    /**
     * Picks the stem axis from what is already written nearby.
     *
     * <p>Split out from the world lookup so it can be unit-tested: the interesting behaviour is the
     * tie-break, not the block reading. With a line on exactly one axis we join it; with none or
     * with a crossing of two, the writer's own facing decides, because nothing else can.
     */
    static Direction.Axis chooseAxis(boolean continuesAlongX, boolean continuesAlongZ,
            Direction.Axis fallback) {
        if (continuesAlongX == continuesAlongZ) {
            return fallback;
        }
        return continuesAlongX ? Direction.Axis.X : Direction.Axis.Z;
    }
}
