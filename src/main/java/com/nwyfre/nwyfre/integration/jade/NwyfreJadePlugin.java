package com.nwyfre.nwyfre.integration.jade;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.block.RitualAltarBlock;
import com.nwyfre.nwyfre.block.RitualBowlBlock;
import com.nwyfre.nwyfre.block.entity.RitualAltarBlockEntity;
import com.nwyfre.nwyfre.block.entity.RitualBowlBlockEntity;
import com.nwyfre.nwyfre.ritual.ImbuedRitual;
import com.nwyfre.nwyfre.ritual.RitualState;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade HUD information for ritual blocks.
 *
 * <p>Answers the two questions a player standing in a circle actually has: what is this altar
 * doing, and what is in that bowl. Without it the only way to inspect a running ritual is to
 * right-click it, which cancels it.
 *
 * <p><strong>The altar reads server data, not the client block entity.</strong> That is not
 * belt-and-braces: NeoForge's {@code deserializeAttachments} only writes back attachment keys that
 * are <em>present</em> in the incoming tag, and never clears ones that are absent. So when the
 * server removes {@code ritual_state} at the end of a rite, the client's copy survives -- and the
 * HUD kept reporting "Working" until the block entity was rebuilt by a respawn or a chunk reload.
 * Asking the server each time sidesteps the whole question.
 *
 * <p>Loaded only when Jade is present: reached solely through the {@code @WailaPlugin} annotation
 * Jade itself scans for, so nothing in Nwyfre references it (CLAUDE.md).
 */
@WailaPlugin
public class NwyfreJadePlugin implements IWailaPlugin {

    private static final AltarProvider ALTAR = new AltarProvider();
    private static final BowlProvider BOWL = new BowlProvider();

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ALTAR, RitualAltarBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ALTAR, RitualAltarBlock.class);
        registration.registerBlockComponent(BOWL, RitualBowlBlock.class);
    }

    /** Keys in the altar's server-data tag. Private to this file; never persisted. */
    private static final class Keys {
        static final String RUNNING = "running";
        static final String RITUAL = "ritual";
        static final String CONSUMED = "consumed";
        static final String WAITING = "waiting";
        static final String CANDLE = "candle";
        static final String BURN = "burn";
        static final String LIT = "lit";

        private Keys() {
        }
    }

    /** What the altar is doing: running a rite, hosting a candle, or idle. */
    private static class AltarProvider
            implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

        private static final ResourceLocation UID = Nwyfre.id("ritual_altar");

        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof RitualAltarBlockEntity altar)) {
                return;
            }

            RitualState state = altar.getRitualState();
            if (state != null) {
                tag.putBoolean(Keys.RUNNING, true);
                tag.putString(Keys.RITUAL, state.recipeId().toString());
                tag.putInt(Keys.CONSUMED, state.consumedCount());
                // Only meaningful while the rite is visibly stalled on the player.
                tag.putBoolean(Keys.WAITING, !state.sacrificeDone() || !state.itemUseDone());
                return;
            }

            ImbuedRitual imbued = altar.heldImbuedRitual();
            if (imbued != null) {
                tag.putString(Keys.CANDLE, imbued.recipeId().toString());
                tag.putInt(Keys.BURN, imbued.burnTicksRemaining());
                tag.putBoolean(Keys.LIT, altar.isLit());
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();

            if (data.getBoolean(Keys.RUNNING)) {
                tooltip.add(Component.translatable("nwyfre.jade.ritual_running",
                        ritualName(data.getString(Keys.RITUAL))).withStyle(ChatFormatting.AQUA));
                tooltip.add(Component.translatable("nwyfre.jade.offerings_taken",
                        data.getInt(Keys.CONSUMED)).withStyle(ChatFormatting.GRAY));
                if (data.getBoolean(Keys.WAITING)) {
                    tooltip.add(Component.translatable("nwyfre.jade.waiting")
                            .withStyle(ChatFormatting.GOLD));
                }
                return;
            }

            if (data.contains(Keys.CANDLE)) {
                tooltip.add(Component.translatable("nwyfre.jade.candle",
                        ritualName(data.getString(Keys.CANDLE))).withStyle(ChatFormatting.LIGHT_PURPLE));
                // Seconds, not ticks: nobody budgets a candle in ticks.
                tooltip.add(Component.translatable("nwyfre.jade.burn_remaining",
                        data.getInt(Keys.BURN) / 20).withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable(data.getBoolean(Keys.LIT)
                        ? "nwyfre.jade.candle_lit"
                        : "nwyfre.jade.candle_unlit").withStyle(ChatFormatting.GRAY));
                return;
            }

            // Idle. The held stack is part of the block entity's normal inventory sync, so it is
            // safe to read from the client copy.
            if (accessor.getBlockEntity() instanceof RitualAltarBlockEntity altar) {
                ItemStack held = altar.getHeld();
                if (!held.isEmpty()) {
                    tooltip.add(Component.translatable("nwyfre.jade.holding", held.getHoverName())
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    /**
     * What a bowl is holding, so a player can check a circle without disturbing it.
     *
     * <p>No server data needed: a bowl's contents ride along in the block entity's own inventory
     * sync, which is a plain NBT field rather than an attachment and so is cleared correctly.
     */
    private static class BowlProvider implements IBlockComponentProvider {

        private static final ResourceLocation UID = Nwyfre.id("ritual_bowl");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof RitualBowlBlockEntity bowl)) {
                return;
            }
            ItemStack held = bowl.getHeld();
            tooltip.add(held.isEmpty()
                    ? Component.translatable("nwyfre.jade.bowl_empty").withStyle(ChatFormatting.DARK_GRAY)
                    : Component.translatable("nwyfre.jade.bowl_holding", held.getHoverName())
                            .withStyle(ChatFormatting.GRAY));
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    /**
     * A readable name for a ritual id.
     *
     * <p>Falls back to the raw id rather than showing nothing: a pack author with an untranslated
     * ritual is better served by seeing which one it is.
     */
    private static Component ritualName(String recipeId) {
        ResourceLocation id = ResourceLocation.tryParse(recipeId);
        if (id == null) {
            return Component.literal(recipeId);
        }
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        String leaf = slash < 0 ? path : path.substring(slash + 1);
        return Component.translatableWithFallback("nwyfre.entry." + leaf, id.toString());
    }
}
