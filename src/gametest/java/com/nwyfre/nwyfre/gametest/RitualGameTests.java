package com.nwyfre.nwyfre.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.block.entity.RitualAltarBlockEntity;
import com.nwyfre.nwyfre.block.entity.RitualBowlBlockEntity;
import com.nwyfre.nwyfre.registry.NwyfreAttachments;
import com.nwyfre.nwyfre.registry.NwyfreBlocks;
import com.nwyfre.nwyfre.ritual.RitualState;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The ritual lifecycle against a live server.
 *
 * <p>These cover only what the headless suite structurally cannot: that the pieces are actually
 * wired together, that the Data Attachment really persists, and that an interrupt really gives the
 * offerings back. The rules themselves -- matching, prerequisites, augment selection, offering
 * arithmetic -- are all unit-tested, which is why there are so few tests here.
 *
 * <p>Data lives in the {@code nwyfre_test} namespace, so these never depend on the shipped content
 * and cannot be broken by editing it.
 *
 * <p>Players come from {@link #unplacedPlayer}, never {@code makeMockServerPlayerInLevel()}, which
 * breaks every test in this project via KubeJS's join handler. See docs/ARCHITECTURE.md.
 */
@GameTestHolder(Nwyfre.MODID)
@PrefixGameTestTemplate(false)
public final class RitualGameTests {

    /** Matches the stub pattern in src/gametest/resources: altar centre, one bowl north. */
    private static final BlockPos BOWL_OFFSET = new BlockPos(0, 0, -1);
    private static final BlockPos GLYPH_OFFSET = new BlockPos(1, 0, 0);

    private RitualGameTests() {
    }

    // ---- setup helpers ----------------------------------------------------------------------

    /**
     * Builds the stub circle at a relative position and returns the altar.
     *
     * <p>A floor is laid first because the glyph block pops off without a sturdy face beneath it,
     * which would otherwise make these tests fail for a reason that has nothing to do with rituals.
     */
    private static BlockPos buildCircle(GameTestHelper helper, BlockPos relativeAltar) {
        helper.setBlock(relativeAltar.below(), Blocks.STONE);
        helper.setBlock(relativeAltar.offset(GLYPH_OFFSET).below(), Blocks.STONE);
        helper.setBlock(relativeAltar.offset(BOWL_OFFSET).below(), Blocks.STONE);

        helper.setBlock(relativeAltar, NwyfreBlocks.RITUAL_ALTAR.get());
        helper.setBlock(relativeAltar.offset(GLYPH_OFFSET), NwyfreBlocks.CHALK_GLYPH.get());
        helper.setBlock(relativeAltar.offset(BOWL_OFFSET), NwyfreBlocks.RITUAL_BOWL.get());
        return relativeAltar;
    }

    private static RitualAltarBlockEntity altarAt(GameTestHelper helper, BlockPos relativeAltar) {
        if (helper.getBlockEntity(relativeAltar) instanceof RitualAltarBlockEntity altar) {
            return altar;
        }
        throw new IllegalStateException("no altar at " + relativeAltar);
    }

    private static void fillBowl(GameTestHelper helper, BlockPos relativeAltar, ItemStack offering) {
        if (helper.getBlockEntity(relativeAltar.offset(BOWL_OFFSET)) instanceof RitualBowlBlockEntity bowl) {
            bowl.setHeld(offering);
            return;
        }
        throw new IllegalStateException("no bowl north of the altar");
    }

    /**
     * Builds a real {@link ServerPlayer} without placing it.
     *
     * <p>Copied from {@code StageGameTests}: {@code placeNewPlayer} fires every mod's join handler,
     * and KubeJS then pushes a payload down a connection that cannot accept it, failing every test
     * before it reaches its own assertions.
     */
    private static ServerPlayer unplacedPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return new ServerPlayer(
                level.getServer(),
                level,
                new GameProfile(UUID.randomUUID(), "nwyfre-test-player"),
                ClientInformation.createDefault());
    }

    // ---- tests ------------------------------------------------------------------------------

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void oneShotRitualStartsAndCompletes(GameTestHelper helper) {
        BlockPos altarPos = buildCircle(helper, new BlockPos(1, 1, 1));
        fillBowl(helper, altarPos, new ItemStack(Items.WHEAT));
        RitualAltarBlockEntity altar = altarAt(helper, altarPos);

        altar.setHeld(new ItemStack(Items.CLOCK));
        if (!altar.tryStart(unplacedPlayer(helper))) {
            helper.fail("the stub ritual should have started with the circle built and the bowl filled");
            return;
        }
        if (!altar.isRunning()) {
            helper.fail("the altar should report a running ritual");
        }
        // LIT is the player-facing signal that something is happening.
        BlockState lit = helper.getBlockState(altarPos);
        if (!lit.getValue(com.nwyfre.nwyfre.block.RitualAltarBlock.LIT)) {
            helper.fail("a running ritual should light its altar");
        }

        // The stub recipe has a duration of 20 ticks; give it room and then assert it finished.
        helper.runAfterDelay(60, () -> {
            if (altar.isRunning()) {
                helper.fail("the ritual should have completed within its duration");
                return;
            }
            if (!altar.getHeld().isEmpty()) {
                helper.fail("completing should consume the activation item");
                return;
            }
            if (helper.getBlockState(altarPos)
                    .getValue(com.nwyfre.nwyfre.block.RitualAltarBlock.LIT)) {
                helper.fail("a finished ritual should extinguish its altar");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void offeringIsConsumedFromItsBowl(GameTestHelper helper) {
        BlockPos altarPos = buildCircle(helper, new BlockPos(1, 1, 1));
        fillBowl(helper, altarPos, new ItemStack(Items.WHEAT));
        RitualAltarBlockEntity altar = altarAt(helper, altarPos);

        altar.setHeld(new ItemStack(Items.CLOCK));
        altar.tryStart(unplacedPlayer(helper));

        helper.runAfterDelay(60, () -> {
            if (helper.getBlockEntity(altarPos.offset(BOWL_OFFSET))
                    instanceof RitualBowlBlockEntity bowl && !bowl.getHeld().isEmpty()) {
                helper.fail("the offering should have been taken from its bowl");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ritualStatePersistsInSavedData(GameTestHelper helper) {
        BlockPos altarPos = buildCircle(helper, new BlockPos(1, 1, 1));
        fillBowl(helper, altarPos, new ItemStack(Items.WHEAT));
        RitualAltarBlockEntity altar = altarAt(helper, altarPos);

        altar.setHeld(new ItemStack(Items.CLOCK));
        altar.tryStart(unplacedPlayer(helper));

        // Prove the state really rides along in the block entity's saved NBT, which is what makes
        // a ritual survive a restart. Attachments serialise under "neoforge:attachments", each
        // under its own registry id.
        CompoundTag saved = altar.saveWithoutMetadata(helper.getLevel().registryAccess());
        CompoundTag attachments = saved.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        if (attachments.isEmpty()) {
            helper.fail("the altar should have written an attachments tag");
            return;
        }
        if (!attachments.contains(Nwyfre.id("ritual_state").toString())) {
            helper.fail("ritual_state should be present in saved data, got keys " + attachments.getAllKeys());
            return;
        }

        RitualState state = altar.getRitualState();
        if (state == null || !state.recipeId().getNamespace().equals("nwyfre_test")) {
            helper.fail("the running state should name the stub recipe");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void breakingABowlInterruptsAndRefunds(GameTestHelper helper) {
        BlockPos altarPos = buildCircle(helper, new BlockPos(1, 1, 1));
        fillBowl(helper, altarPos, new ItemStack(Items.WHEAT));
        RitualAltarBlockEntity altar = altarAt(helper, altarPos);

        altar.setHeld(new ItemStack(Items.CLOCK));
        altar.tryStart(unplacedPlayer(helper));

        // Tear the circle after it has started but before it can finish.
        helper.runAfterDelay(5, () -> {
            helper.setBlock(altarPos.offset(GLYPH_OFFSET), Blocks.AIR);

            helper.runAfterDelay(10, () -> {
                if (altar.isRunning()) {
                    helper.fail("removing a pattern block should interrupt the ritual");
                    return;
                }
                // The activation item must come back: a ritual that eats the cost of a circle the
                // player accidentally broke would be infuriating.
                if (!altar.getHeld().isEmpty()) {
                    helper.fail("the altar should be empty after an interrupt, having dropped its item");
                    return;
                }
                boolean refunded = !helper.getLevel()
                        .getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                                new net.minecraft.world.phys.AABB(helper.absolutePos(altarPos))
                                        .inflate(3.0))
                        .isEmpty();
                if (!refunded) {
                    helper.fail("an interrupted ritual should drop what it had taken");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void clickingAFullBowlWithAnItemSwapsRatherThanEmptyingIt(GameTestHelper helper) {
        // Regression. The bowl used to return PASS_TO_DEFAULT_BLOCK_INTERACTION when full, which
        // falls through to useWithoutItem -- the TAKE-OUT path. So right-clicking a filled bowl
        // while holding anything threw the offering back out, which reads as the bowl refusing it.
        BlockPos altarPos = buildCircle(helper, new BlockPos(1, 1, 1));
        BlockPos bowlPos = altarPos.offset(BOWL_OFFSET);
        fillBowl(helper, altarPos, new ItemStack(Items.WHEAT));

        ServerPlayer player = unplacedPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL));
        helper.useBlock(bowlPos, player);

        if (!(helper.getBlockEntity(bowlPos) instanceof RitualBowlBlockEntity bowl)) {
            helper.fail("no bowl");
            return;
        }
        if (bowl.getHeld().isEmpty()) {
            helper.fail("the bowl was emptied instead of taking the new offering");
            return;
        }
        if (!bowl.getHeld().is(Items.COAL)) {
            helper.fail("the bowl should now hold the new offering, holds " + bowl.getHeld());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void clickingARunningAltarWithAnItemDoesNotCancelIt(GameTestHelper helper) {
        // Regression, and the worse half of the same fall-through: a running altar reached
        // useWithoutItem, where an empty hand means "call the rite off". Merely holding something
        // and clicking a working circle used to cancel it.
        BlockPos altarPos = buildCircle(helper, new BlockPos(1, 1, 1));
        fillBowl(helper, altarPos, new ItemStack(Items.WHEAT));
        RitualAltarBlockEntity altar = altarAt(helper, altarPos);

        altar.setHeld(new ItemStack(Items.CLOCK));
        if (!altar.tryStart(unplacedPlayer(helper))) {
            helper.fail("the stub ritual should have started");
            return;
        }

        ServerPlayer player = unplacedPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
        helper.useBlock(altarPos, player);

        if (!altar.isRunning()) {
            helper.fail("holding an item and clicking should not cancel a running ritual");
            return;
        }
        // An empty hand still cancels, which is the deliberate statement of intent.
        ServerPlayer emptyHanded = unplacedPlayer(helper);
        helper.useBlock(altarPos, emptyHanded);
        if (altar.isRunning()) {
            helper.fail("an empty-handed click should still cancel");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void persistentRitualImbuesACandleThatTogglesAndBurnsOut(GameTestHelper helper) {
        BlockPos altarPos = buildCircle(helper, new BlockPos(1, 1, 1));
        fillBowl(helper, altarPos, new ItemStack(Items.WHEAT));
        RitualAltarBlockEntity altar = altarAt(helper, altarPos);

        altar.setHeld(new ItemStack(Items.CANDLE));
        if (!altar.tryStart(unplacedPlayer(helper))) {
            helper.fail("the persistent stub ritual should have started");
            return;
        }

        helper.runAfterDelay(50, () -> {
            if (altar.isRunning()) {
                helper.fail("the persistent ritual should have finished its build phase");
                return;
            }
            if (altar.heldImbuedRitual() == null) {
                helper.fail("completing a persistent ritual should imbue the candle in the altar");
                return;
            }
            if (!altar.isLit()) {
                helper.fail("an imbued candle should be left alight");
                return;
            }

            int before = altar.heldImbuedRitual().burnTicksRemaining();

            // Snuffing it must preserve the candle rather than merely pause the effect.
            altar.toggleLit(helper.getLevel());
            if (altar.isLit()) {
                helper.fail("toggling should have snuffed the candle");
                return;
            }

            helper.runAfterDelay(40, () -> {
                if (altar.heldImbuedRitual() == null) {
                    helper.fail("an unlit candle should not burn away");
                    return;
                }
                if (altar.heldImbuedRitual().burnTicksRemaining() != before) {
                    helper.fail("burn time should freeze while the candle is out, was " + before
                            + " now " + altar.heldImbuedRitual().burnTicksRemaining());
                    return;
                }

                // Relight it and let it run down to nothing.
                altar.toggleLit(helper.getLevel());
                helper.runAfterDelay(120, () -> {
                    if (altar.heldImbuedRitual() != null) {
                        helper.fail("a lit candle should burn away once its time is spent");
                        return;
                    }
                    if (altar.isLit()) {
                        helper.fail("a spent candle should leave the altar dark");
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void prerequisitesBlockStartAndAreDiagnosed(GameTestHelper helper) {
        // The gated stub ritual requires a stage the test player does not have.
        BlockPos altarPos = buildCircle(helper, new BlockPos(1, 1, 1));
        fillBowl(helper, altarPos, new ItemStack(Items.WHEAT));
        RitualAltarBlockEntity altar = altarAt(helper, altarPos);

        ServerPlayer player = unplacedPlayer(helper);
        altar.setHeld(new ItemStack(Items.NETHER_STAR));

        if (altar.tryStart(player)) {
            helper.fail("a stage-gated ritual must not start for a player without the stage");
            return;
        }
        if (altar.isRunning()) {
            helper.fail("nothing should be running after a refused start");
            return;
        }

        // Automation has no stages at all, so it must be refused too.
        if (altar.tryStart(null)) {
            helper.fail("a stage-gated ritual must not start unattended by default");
            return;
        }

        helper.succeed();
    }
}
