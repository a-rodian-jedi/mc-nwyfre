package com.nwyfre.nwyfre.ritual.prereq;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.nwyfre.nwyfre.stage.PlayerStages;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * A hand-built world for prerequisite tests.
 *
 * <p>This exists because {@link PrerequisiteContext} is an interface rather than a wrapper around
 * {@code ServerLevel}: every prerequisite rule can therefore be checked headlessly in
 * milliseconds, instead of needing a live server.
 */
final class TestPrerequisiteContext implements PrerequisiteContext {

    static final BlockPos ALTAR = new BlockPos(8, 70, -3);

    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final Map<BlockPos, FluidState> fluids = new HashMap<>();

    private BlockPos altarPos = ALTAR;
    private long dayTime;
    private int moonPhase;
    private boolean raining;
    private boolean thundering;
    private ResourceKey<Level> dimension = Level.OVERWORLD;
    private Optional<PlayerStages> stages = Optional.empty();

    static TestPrerequisiteContext create() {
        return new TestPrerequisiteContext();
    }

    TestPrerequisiteContext at(BlockPos pos) {
        this.altarPos = pos;
        return this;
    }

    TestPrerequisiteContext time(long time) {
        this.dayTime = time;
        return this;
    }

    TestPrerequisiteContext moon(int phase) {
        this.moonPhase = phase;
        return this;
    }

    TestPrerequisiteContext withClearSkies() {
        this.raining = false;
        this.thundering = false;
        return this;
    }

    TestPrerequisiteContext withRain() {
        this.raining = true;
        this.thundering = false;
        return this;
    }

    /** Mirrors vanilla, where a thunderstorm also reports as raining. */
    TestPrerequisiteContext withThunder() {
        this.raining = true;
        this.thundering = true;
        return this;
    }

    TestPrerequisiteContext dimension(ResourceKey<Level> key) {
        this.dimension = key;
        return this;
    }

    TestPrerequisiteContext withStages(PlayerStages playerStages) {
        this.stages = Optional.of(playerStages);
        return this;
    }

    /** Leaves the context player-less, as if a hopper had started the ritual. */
    TestPrerequisiteContext unattended() {
        this.stages = Optional.empty();
        return this;
    }

    TestPrerequisiteContext block(BlockPos pos, BlockState state) {
        blocks.put(pos, state);
        return this;
    }

    TestPrerequisiteContext fluid(BlockPos pos, FluidState state) {
        fluids.put(pos, state);
        return this;
    }

    @Override
    public BlockPos altarPos() {
        return altarPos;
    }

    @Override
    public long dayTime() {
        return dayTime;
    }

    @Override
    public int moonPhase() {
        return moonPhase;
    }

    @Override
    public boolean raining() {
        return raining;
    }

    @Override
    public boolean thundering() {
        return thundering;
    }

    @Override
    public ResourceKey<Level> dimension() {
        return dimension;
    }

    /**
     * Unsupported on purpose.
     *
     * <p>A {@link Biome} cannot be built without a full registry load, so biome gating is covered
     * by the codec round trip and the manual pass instead. Throwing here rather than returning
     * null means a future test that does need biomes gets a clear message instead of an NPE from
     * deep inside a {@code HolderSet}.
     */
    @Override
    public Holder<Biome> biomeAt(BlockPos pos) {
        throw new UnsupportedOperationException(
                "biomeAt is not available headlessly; cover biome terms with a GameTest");
    }

    @Override
    public BlockState blockAt(BlockPos pos) {
        return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public FluidState fluidAt(BlockPos pos) {
        return fluids.getOrDefault(pos, Fluids.EMPTY.defaultFluidState());
    }

    @Override
    public Optional<PlayerStages> stages() {
        return stages;
    }

    @Override
    public HolderLookup.Provider registries() {
        return net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    /** Convenience for tests that need a dimension key other than the three vanilla ones. */
    static ResourceKey<Level> dimensionKey(String namespace, String path) {
        return ResourceKey.create(Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
