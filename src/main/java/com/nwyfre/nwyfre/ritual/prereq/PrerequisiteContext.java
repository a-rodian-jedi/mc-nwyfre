package com.nwyfre.nwyfre.ritual.prereq;

import java.util.Optional;

import com.nwyfre.nwyfre.stage.PlayerStages;
import com.nwyfre.nwyfre.stage.Stages;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Everything a prerequisite is allowed to look at, as the narrowest set of queries that covers it.
 *
 * <p>An interface rather than a record wrapping {@link ServerLevel}, for the same reason
 * {@code PatternMatcher} takes a function instead of a level: a {@code ServerLevel} cannot be
 * built in a headless test, so depending on one directly would push every prerequisite rule into
 * the GameTest suite. Against this interface the whole tree is unit-testable, and the GameTest only
 * has to prove the wiring.
 *
 * <p>Note {@link #stages()} rather than a player. Terms never need the player itself -- only its
 * stage set -- and {@code PlayerStages} is a plain object a test can build.
 */
public interface PrerequisiteContext {

    /** The altar, which every positional term measures from. */
    BlockPos altarPos();

    /** Raw world time; callers take it modulo a day themselves. */
    long dayTime();

    /** Vanilla numbering, where <strong>0 is the full moon</strong>. */
    int moonPhase();

    boolean raining();

    boolean thundering();

    ResourceKey<Level> dimension();

    Holder<Biome> biomeAt(BlockPos pos);

    BlockState blockAt(BlockPos pos);

    FluidState fluidAt(BlockPos pos);

    /**
     * The starter's stages, or empty when a machine started the ritual.
     *
     * <p>Empty is a real and common case: automation can drop an activation item into an altar,
     * and there is then no player whose progression could be consulted.
     */
    Optional<PlayerStages> stages();

    /** Needed only by {@link PackConditionPrerequisite} for tag lookups. */
    HolderLookup.Provider registries();

    static PrerequisiteContext of(ServerLevel level, BlockPos altarPos, @Nullable ServerPlayer player) {
        return new LevelContext(level, altarPos, player);
    }

    /** The production implementation, reading straight off a live server level. */
    record LevelContext(ServerLevel level, BlockPos altarPos, @Nullable ServerPlayer player)
            implements PrerequisiteContext {

        @Override
        public long dayTime() {
            return level.getDayTime();
        }

        @Override
        public int moonPhase() {
            return level.getMoonPhase();
        }

        @Override
        public boolean raining() {
            return level.isRaining();
        }

        @Override
        public boolean thundering() {
            return level.isThundering();
        }

        @Override
        public ResourceKey<Level> dimension() {
            return level.dimension();
        }

        @Override
        public Holder<Biome> biomeAt(BlockPos pos) {
            return level.getBiome(pos);
        }

        @Override
        public BlockState blockAt(BlockPos pos) {
            return level.getBlockState(pos);
        }

        @Override
        public FluidState fluidAt(BlockPos pos) {
            return level.getFluidState(pos);
        }

        @Override
        public Optional<PlayerStages> stages() {
            return Optional.ofNullable(player).map(Stages::get);
        }

        @Override
        public HolderLookup.Provider registries() {
            return level.registryAccess();
        }
    }
}
