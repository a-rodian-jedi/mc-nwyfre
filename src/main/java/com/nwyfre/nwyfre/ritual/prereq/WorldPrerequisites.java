package com.nwyfre.nwyfre.ritual.prereq;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * The prerequisite terms that ask about the world: when it is, where it is, and what is nearby.
 *
 * <p>Grouped in one file because each is a handful of lines and they are only ever read as a set.
 * The two terms with special behaviour live separately: {@link StagePrerequisite} (needs a player,
 * and JEI treats it differently) and {@link PackConditionPrerequisite} (the {@code ICondition}
 * bridge).
 *
 * <p>Block and fluid terms take a {@link HolderSet}, so an author may write a single id, a list of
 * ids, or {@code "#some:tag"} in the same field -- the same ergonomics as a vanilla ingredient.
 */
public final class WorldPrerequisites {

    private WorldPrerequisites() {
    }

    private static Component describeHolders(String key, HolderSet<?> holders) {
        return holders.unwrap().map(
                tag -> Component.translatable(key + ".tag", tag.location().toString()),
                list -> Component.translatable(key, ComponentUtils.formatList(
                        list.stream().map(holder -> holder.unwrapKey()
                                .map(resourceKey -> (Component) Component.literal(
                                        resourceKey.location().toString()))
                                .orElseGet(() -> Component.literal("?")))
                                .toList(),
                        Component.literal(", "))));
    }

    /**
     * Time of day, either by name or as an explicit tick window.
     *
     * <p>Windows wrap: {@code min 23000, max 1000} is the span across midnight, which is what makes
     * a usable "dawn" expressible at all.
     */
    public record TimeOfDay(Phase phase, int min, int max) implements RitualPrerequisite {

        public static final MapCodec<TimeOfDay> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Phase.CODEC.optionalFieldOf("time", Phase.CUSTOM).forGetter(TimeOfDay::phase),
                        Codec.intRange(0, 23999).optionalFieldOf("min", 0).forGetter(TimeOfDay::min),
                        Codec.intRange(0, 23999).optionalFieldOf("max", 23999).forGetter(TimeOfDay::max))
                .apply(instance, TimeOfDay::new));

        /** Named spans, using vanilla boundaries so they agree with mob spawning and sleeping. */
        public enum Phase implements StringRepresentable {
            DAY("day", 0, 11999),
            DUSK("dusk", 12000, 12999),
            NIGHT("night", 13000, 22999),
            DAWN("dawn", 23000, 23999),
            CUSTOM("custom", 0, 23999);

            public static final Codec<Phase> CODEC = StringRepresentable.fromEnum(Phase::values);

            private final String name;
            final int min;
            final int max;

            Phase(String name, int min, int max) {
                this.name = name;
                this.min = min;
                this.max = max;
            }

            @Override
            public String getSerializedName() {
                return name;
            }
        }

        private int effectiveMin() {
            return phase == Phase.CUSTOM ? min : phase.min;
        }

        private int effectiveMax() {
            return phase == Phase.CUSTOM ? max : phase.max;
        }

        @Override
        public MapCodec<? extends RitualPrerequisite> codec() {
            return CODEC;
        }

        @Override
        public boolean test(PrerequisiteContext context) {
            long time = context.dayTime() % 24000L;
            int low = effectiveMin();
            int high = effectiveMax();
            return low <= high ? time >= low && time <= high : time >= low || time <= high;
        }

        @Override
        public Component describe() {
            return phase == Phase.CUSTOM
                    ? Component.translatable("nwyfre.ritual.prereq.time_of_day.custom", min, max)
                    : Component.translatable("nwyfre.ritual.prereq.time_of_day." + phase.getSerializedName());
        }
    }

    /**
     * Moon phase.
     *
     * <p><strong>Phase 0 is the full moon, not the new moon.</strong> That is vanilla's numbering
     * and it catches everyone; a pack author writing {@code "phases": [0]} means a full moon rite.
     */
    public record MoonPhase(List<Integer> phases) implements RitualPrerequisite {

        public static final MapCodec<MoonPhase> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.intRange(0, 7).listOf().fieldOf("phases").forGetter(MoonPhase::phases))
                .apply(instance, MoonPhase::new));

        public MoonPhase(List<Integer> phases) {
            this.phases = List.copyOf(phases);
        }

        @Override
        public MapCodec<? extends RitualPrerequisite> codec() {
            return CODEC;
        }

        @Override
        public boolean test(PrerequisiteContext context) {
            return phases.contains(context.moonPhase());
        }

        @Override
        public Component describe() {
            return Component.translatable("nwyfre.ritual.prereq.moon_phase",
                    ComponentUtils.formatList(phases.stream()
                                    .map(phase -> (Component) Component.translatable(
                                            "nwyfre.moon_phase." + phase))
                                    .toList(),
                            Component.literal(", ")));
        }
    }

    /**
     * Weather.
     *
     * <p>Order matters here: vanilla reports {@code isRaining} as true during a thunderstorm too,
     * so thunder has to be checked first or {@code "rain"} would silently also accept storms.
     */
    public record Weather(Kind kind) implements RitualPrerequisite {

        public static final MapCodec<Weather> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Kind.CODEC.fieldOf("weather").forGetter(Weather::kind))
                .apply(instance, Weather::new));

        public enum Kind implements StringRepresentable {
            CLEAR("clear"),
            RAIN("rain"),
            THUNDER("thunder");

            public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

            private final String name;

            Kind(String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return name;
            }
        }

        /** Collapses vanilla's overlapping flags into exactly one kind. */
        private static Kind current(PrerequisiteContext context) {
            if (context.thundering()) {
                return Kind.THUNDER;
            }
            return context.raining() ? Kind.RAIN : Kind.CLEAR;
        }

        @Override
        public MapCodec<? extends RitualPrerequisite> codec() {
            return CODEC;
        }

        @Override
        public boolean test(PrerequisiteContext context) {
            return current(context) == kind;
        }

        @Override
        public Component describe() {
            return Component.translatable("nwyfre.ritual.prereq.weather." + kind.getSerializedName());
        }
    }

    /** Biome at the altar, by id or tag. */
    public record InBiome(HolderSet<Biome> biomes) implements RitualPrerequisite {

        public static final MapCodec<InBiome> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes")
                                .forGetter(InBiome::biomes))
                .apply(instance, InBiome::new));

        @Override
        public MapCodec<? extends RitualPrerequisite> codec() {
            return CODEC;
        }

        @Override
        public boolean test(PrerequisiteContext context) {
            return biomes.contains(context.biomeAt(context.altarPos()));
        }

        @Override
        public Component describe() {
            return describeHolders("nwyfre.ritual.prereq.biome", biomes);
        }
    }

    /** Dimension the altar is in. */
    public record InDimension(ResourceKey<Level> dimension) implements RitualPrerequisite {

        public static final MapCodec<InDimension> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension")
                                .forGetter(InDimension::dimension))
                .apply(instance, InDimension::new));

        @Override
        public MapCodec<? extends RitualPrerequisite> codec() {
            return CODEC;
        }

        @Override
        public boolean test(PrerequisiteContext context) {
            return context.dimension().equals(dimension);
        }

        @Override
        public Component describe() {
            return Component.translatable("nwyfre.ritual.prereq.dimension", dimension.location().toString());
        }
    }

    /** Altar Y, inclusive on both ends. */
    public record Elevation(int min, int max) implements RitualPrerequisite {

        public static final MapCodec<Elevation> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.INT.optionalFieldOf("min", Integer.MIN_VALUE).forGetter(Elevation::min),
                        Codec.INT.optionalFieldOf("max", Integer.MAX_VALUE).forGetter(Elevation::max))
                .apply(instance, Elevation::new));

        @Override
        public MapCodec<? extends RitualPrerequisite> codec() {
            return CODEC;
        }

        @Override
        public boolean test(PrerequisiteContext context) {
            int y = context.altarPos().getY();
            return y >= min && y <= max;
        }

        @Override
        public Component describe() {
            if (min == Integer.MIN_VALUE) {
                return Component.translatable("nwyfre.ritual.prereq.elevation.below", max);
            }
            if (max == Integer.MAX_VALUE) {
                return Component.translatable("nwyfre.ritual.prereq.elevation.above", min);
            }
            return Component.translatable("nwyfre.ritual.prereq.elevation.between", min, max);
        }
    }

    /**
     * The block at an offset from the altar. Covers "on dirt".
     *
     * <p>Defaults to the block directly below the altar, since that is what "standing on"
     * overwhelmingly means in practice.
     */
    public record BlockAt(Vec3i offset, HolderSet<Block> blocks) implements RitualPrerequisite {

        private static final Vec3i BELOW = new Vec3i(0, -1, 0);

        public static final MapCodec<BlockAt> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Vec3i.CODEC.optionalFieldOf("offset", BELOW).forGetter(BlockAt::offset),
                        RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("blocks")
                                .forGetter(BlockAt::blocks))
                .apply(instance, BlockAt::new));

        @Override
        public MapCodec<? extends RitualPrerequisite> codec() {
            return CODEC;
        }

        @Override
        public boolean test(PrerequisiteContext context) {
            BlockPos target = context.altarPos().offset(offset.getX(), offset.getY(), offset.getZ());
            return blocks.contains(context.blockAt(target).getBlockHolder());
        }

        @Override
        public Component describe() {
            return describeHolders("nwyfre.ritual.prereq.block_at", blocks);
        }
    }

    /**
     * The fluid at an offset from the altar. Covers "in water".
     *
     * <p>Defaults to the altar position itself, since that is what "in" means.
     */
    public record FluidAt(Vec3i offset, HolderSet<Fluid> fluids) implements RitualPrerequisite {

        public static final MapCodec<FluidAt> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Vec3i.CODEC.optionalFieldOf("offset", Vec3i.ZERO).forGetter(FluidAt::offset),
                        RegistryCodecs.homogeneousList(Registries.FLUID).fieldOf("fluids")
                                .forGetter(FluidAt::fluids))
                .apply(instance, FluidAt::new));

        @Override
        public MapCodec<? extends RitualPrerequisite> codec() {
            return CODEC;
        }

        @Override
        public boolean test(PrerequisiteContext context) {
            BlockPos target = context.altarPos().offset(offset.getX(), offset.getY(), offset.getZ());
            return fluids.contains(BuiltInRegistries.FLUID.wrapAsHolder(
                    context.fluidAt(target).getType()));
        }

        @Override
        public Component describe() {
            return describeHolders("nwyfre.ritual.prereq.fluid_at", fluids);
        }
    }
}
