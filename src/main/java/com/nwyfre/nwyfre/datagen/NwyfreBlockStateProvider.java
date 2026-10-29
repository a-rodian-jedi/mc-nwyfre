package com.nwyfre.nwyfre.datagen;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.block.RitualAltarBlock;
import com.nwyfre.nwyfre.block.ogham.OghamGlyphBlock;
import com.nwyfre.nwyfre.registry.NwyfreBlocks;

import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Block and block-item models.
 *
 * <p>Every model here is built from <strong>vanilla textures</strong>. v0 ships no art, and
 * pointing at textures that do not exist yet would fill the client log with load errors. v1 swaps
 * the texture references for real Nwyfre art without changing the model shapes.
 */
public class NwyfreBlockStateProvider extends BlockStateProvider {

    private static final ResourceLocation ALTAR_TEXTURE =
            ResourceLocation.withDefaultNamespace("block/chiseled_stone_bricks");
    private static final ResourceLocation ALTAR_TOP_TEXTURE =
            ResourceLocation.withDefaultNamespace("block/smooth_stone");
    private static final ResourceLocation BOWL_TEXTURE =
            ResourceLocation.withDefaultNamespace("block/oak_planks");
    private static final ResourceLocation GLYPH_TEXTURE =
            ResourceLocation.withDefaultNamespace("block/calcite");

    public NwyfreBlockStateProvider(PackOutput output, ExistingFileHelper helper) {
        super(output, Nwyfre.MODID, helper);
    }

    @Override
    protected void registerStatesAndModels() {
        ModelFile altar = altarModel();
        // Both LIT states share one model in v0: the state already drives the light level, and the
        // running ritual is read from its particles. A lit variant is v1 art work.
        getVariantBuilder(NwyfreBlocks.RITUAL_ALTAR.get())
                .forAllStates(state -> net.neoforged.neoforge.client.model.generators.ConfiguredModel
                        .builder().modelFile(altar).build());
        simpleBlockItem(NwyfreBlocks.RITUAL_ALTAR.get(), altar);

        ModelFile bowl = bowlModel();
        simpleBlock(NwyfreBlocks.RITUAL_BOWL.get(), bowl);
        simpleBlockItem(NwyfreBlocks.RITUAL_BOWL.get(), bowl);

        ModelFile glyph = glyphModel();
        simpleBlock(NwyfreBlocks.CHALK_GLYPH.get(), glyph);
        // A flat block reads as nothing in an inventory slot, so the item uses a sprite instead.
        itemModels().withExistingParent("chalk_glyph", "item/generated")
                .texture("layer0", ResourceLocation.withDefaultNamespace("item/paper"));

        NwyfreBlocks.oghamGlyphs().forEach(this::oghamGlyph);
    }

    /**
     * One Ogham glyph: a flat plate wearing its own sprite, turned to match the stem axis.
     *
     * <p>Only one model and one texture per character. The texture is drawn with its stem running
     * east-west, and the Z variant is the same model rotated a quarter turn -- so there is no
     * second sprite to keep in step, and the stem stays dead centre under rotation, which is what
     * keeps a line of glyphs joined.
     *
     * <p>No {@code uvlock}: the texture must turn <em>with</em> the model, not stay put.
     */
    private void oghamGlyph(DeferredBlock<OghamGlyphBlock> block) {
        String name = block.getId().getPath();
        ResourceLocation texture = Nwyfre.id("block/" + name);

        BlockModelBuilder model = models().getBuilder("block/" + name)
                .texture("particle", texture)
                .texture("side", texture)
                .texture("top", texture)
                .renderType("cutout");
        box(model, 0, 0, 0, 16, 0.25F, 16);

        getVariantBuilder(block.get()).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(model)
                .rotationY(state.getValue(OghamGlyphBlock.AXIS) == Direction.Axis.Z ? 90 : 0)
                .build());

        // The glyph sprite is already a flat symbol, so it makes a better item icon than the
        // block ever could -- unlike chalk_glyph above, which would be a blank square.
        itemModels().withExistingParent(name, "item/generated").texture("layer0", texture);
    }

    private ModelFile altarModel() {
        BlockModelBuilder model = models().getBuilder("block/ritual_altar")
                .texture("particle", ALTAR_TEXTURE)
                .texture("side", ALTAR_TEXTURE)
                .texture("top", ALTAR_TOP_TEXTURE)
                .renderType("solid");
        box(model, 1, 0, 1, 15, 3, 15);
        box(model, 3, 3, 3, 13, 11, 13);
        box(model, 1, 11, 1, 15, 14, 15);
        return model;
    }

    private ModelFile bowlModel() {
        BlockModelBuilder model = models().getBuilder("block/ritual_bowl")
                .texture("particle", BOWL_TEXTURE)
                .texture("side", BOWL_TEXTURE)
                .texture("top", BOWL_TEXTURE)
                .renderType("solid");
        box(model, 2, 0, 2, 14, 2, 14);
        box(model, 2, 2, 2, 3, 5, 14);
        box(model, 13, 2, 2, 14, 5, 14);
        box(model, 3, 2, 2, 13, 5, 3);
        box(model, 3, 2, 13, 13, 5, 14);
        return model;
    }

    private ModelFile glyphModel() {
        BlockModelBuilder model = models().getBuilder("block/chalk_glyph")
                .texture("particle", GLYPH_TEXTURE)
                .texture("side", GLYPH_TEXTURE)
                .texture("top", GLYPH_TEXTURE)
                .renderType("cutout");
        box(model, 0, 0, 0, 16, 0.25F, 16);
        return model;
    }

    /** Adds one cuboid textured on every face, so the model matches the collision shape. */
    private static void box(BlockModelBuilder model, float x1, float y1, float z1, float x2, float y2, float z2) {
        BlockModelBuilder.ElementBuilder element = model.element().from(x1, y1, z1).to(x2, y2, z2);
        for (Direction direction : Direction.values()) {
            String texture = switch (direction) {
                case UP, DOWN -> "#top";
                default -> "#side";
            };
            element.face(direction).texture(texture).end();
        }
        element.end();
    }
}
