package com.nwyfre.nwyfre.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nwyfre.nwyfre.block.entity.SingleSlotBlockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws whatever a bowl or an altar is holding.
 *
 * <p>Without this the contents are completely invisible: the block entity holds the stack and the
 * HUD reports it, but the model is just an empty bowl. A player cannot lay out a circle they cannot
 * see, so this is load-bearing rather than decoration.
 *
 * <p>Shared by both blocks because the job is identical; only the height and the spin differ.
 *
 * @param height where the item sits above the block origin
 * @param spinDegreesPerTick 0 for a still item
 */
public record HeldOfferingRenderer(float height, float scale, float spinDegreesPerTick)
        implements BlockEntityRenderer<SingleSlotBlockEntity> {

    /** An offering hovers and turns slowly, so a filled bowl reads at a glance across a circle. */
    public static BlockEntityRendererProvider<SingleSlotBlockEntity> bowl() {
        return context -> new HeldOfferingRenderer(0.42F, 0.5F, 1.2F);
    }

    /** The altar's item sits still on top: it is a token being offered up, not a thing on display. */
    public static BlockEntityRendererProvider<SingleSlotBlockEntity> altar() {
        return context -> new HeldOfferingRenderer(0.95F, 0.6F, 0.0F);
    }

    @Override
    public void render(SingleSlotBlockEntity blockEntity, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        ItemStack stack = blockEntity.getHeld();
        if (stack.isEmpty() || blockEntity.getLevel() == null) {
            return;
        }

        ItemRenderer itemRenderer = net.minecraft.client.Minecraft.getInstance().getItemRenderer();

        pose.pushPose();
        pose.translate(0.5F, height, 0.5F);

        if (spinDegreesPerTick != 0.0F) {
            float age = blockEntity.getLevel().getGameTime() + partialTick;
            pose.mulPose(Axis.YP.rotationDegrees(age * spinDegreesPerTick));
        }

        // Block items read better lying flat; a flat item sprite would otherwise face edge-on and
        // all but vanish from above, which is the angle a player lays out a circle from.
        BakedModel model = itemRenderer.getModel(stack, blockEntity.getLevel(), null, 0);
        if (!model.isGui3d()) {
            pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        }

        pose.scale(scale, scale, scale);
        itemRenderer.render(stack, ItemDisplayContext.FIXED, false, pose, buffers,
                packedLight, packedOverlay, model);
        pose.popPose();
    }
}
