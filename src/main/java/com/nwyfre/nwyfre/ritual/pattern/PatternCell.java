package com.nwyfre.nwyfre.ritual.pattern;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nwyfre.nwyfre.registry.NwyfreTags;
import com.nwyfre.nwyfre.ritual.Element;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What one cell of a ritual pattern demands of the block in that position, plus optionally the
 * element whose offering bowl this cell is.
 *
 * <p>Predicates are block-or-tag only, deliberately. Matching on a tag is what lets a pattern
 * written in v0 keep working once v1 adds basalt bowls and elemental glyphs into the same tags.
 *
 * <p>The predicate is inlined via {@code dispatchMap}, so a cell reads flat in JSON:
 * {@code {"type": "nwyfre:tag", "tag": "nwyfre:ritual_bowls", "offering": "nwyfre:earth"}}.
 *
 * @param predicate what must be at this position
 * @param offering the element whose bowl this is, if any
 * @param display a concrete block to show in previews when the predicate is a tag
 */
public record PatternCell(Predicate predicate, Optional<ResourceKey<Element>> offering,
        Optional<Block> display) {

    public static final Codec<PatternCell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Predicate.CODEC.forGetter(PatternCell::predicate),
                    ResourceKey.codec(RitualPattern.ELEMENT_REGISTRY).optionalFieldOf("offering")
                            .forGetter(PatternCell::offering),
                    BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("display")
                            .forGetter(PatternCell::display))
            .apply(instance, PatternCell::new));

    public boolean test(BlockState state) {
        return predicate.test(state);
    }

    public boolean isOfferingSlot() {
        return offering.isPresent();
    }

    /** True for cells that place no demand at all, which never need reporting as missing. */
    public boolean isWildcard() {
        return predicate instanceof Predicate.Any;
    }

    /** True for the single cell that fixes the pattern origin. */
    public boolean isAnchor() {
        return predicate instanceof Predicate.IsAltar;
    }

    /** The block a preview should draw for this cell. */
    public Optional<Block> previewBlock() {
        if (display.isPresent()) {
            return display;
        }
        return predicate instanceof Predicate.IsBlock isBlock ? Optional.of(isBlock.block()) : Optional.empty();
    }

    /** The block predicate for a cell. Sealed: the pattern format has exactly these five options. */
    public sealed interface Predicate {

        MapCodec<Predicate> CODEC = Type.CODEC.dispatchMap("type", Predicate::type, Type::codec);

        Type type();

        boolean test(BlockState state);

        /** Matches anything, including air. The pattern filler. */
        record Any() implements Predicate {
            static final MapCodec<Any> CODEC = MapCodec.unit(Any::new);

            @Override
            public Type type() {
                return Type.ANY;
            }

            @Override
            public boolean test(BlockState state) {
                return true;
            }
        }

        /** Matches only air, for cells that must be kept clear. */
        record IsAir() implements Predicate {
            static final MapCodec<IsAir> CODEC = MapCodec.unit(IsAir::new);

            @Override
            public Type type() {
                return Type.AIR;
            }

            @Override
            public boolean test(BlockState state) {
                return state.isAir();
            }
        }

        record IsBlock(Block block) implements Predicate {
            static final MapCodec<IsBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(IsBlock::block))
                    .apply(instance, IsBlock::new));

            @Override
            public Type type() {
                return Type.BLOCK;
            }

            @Override
            public boolean test(BlockState state) {
                return state.is(block);
            }
        }

        record IsTag(TagKey<Block> tag) implements Predicate {
            static final MapCodec<IsTag> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                            TagKey.codec(Registries.BLOCK).fieldOf("tag").forGetter(IsTag::tag))
                    .apply(instance, IsTag::new));

            @Override
            public Type type() {
                return Type.TAG;
            }

            @Override
            public boolean test(BlockState state) {
                return state.is(tag);
            }
        }

        /**
         * The anchor. Exactly one cell in a pattern must be this.
         *
         * <p>Matches the altar <em>tag</em> rather than the concrete block so v1 can add a greater
         * altar without reissuing every pattern. Note the matcher never actually evaluates this
         * predicate: the anchor only fixes the origin, and the caller already found an altar there.
         */
        record IsAltar() implements Predicate {
            static final MapCodec<IsAltar> CODEC = MapCodec.unit(IsAltar::new);

            @Override
            public Type type() {
                return Type.ALTAR;
            }

            @Override
            public boolean test(BlockState state) {
                return state.is(NwyfreTags.Blocks.RITUAL_ALTARS);
            }
        }

        enum Type implements StringRepresentable {
            ANY("nwyfre:any", Any.CODEC),
            AIR("nwyfre:air", IsAir.CODEC),
            BLOCK("nwyfre:block", IsBlock.CODEC),
            TAG("nwyfre:tag", IsTag.CODEC),
            ALTAR("nwyfre:altar", IsAltar.CODEC);

            static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

            private final String name;
            private final MapCodec<? extends Predicate> codec;

            Type(String name, MapCodec<? extends Predicate> codec) {
                this.name = name;
                this.codec = codec;
            }

            MapCodec<? extends Predicate> codec() {
                return codec;
            }

            @Override
            public String getSerializedName() {
                return name;
            }
        }
    }
}
