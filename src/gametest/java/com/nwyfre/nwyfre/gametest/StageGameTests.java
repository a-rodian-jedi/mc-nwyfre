package com.nwyfre.nwyfre.gametest;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.authlib.GameProfile;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.stage.NwyfreStageChangeEvent;
import com.nwyfre.nwyfre.stage.Stages;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * End-to-end checks for the stage system against a live server and a real {@link ServerPlayer}.
 *
 * <p>These cover what the headless JUnit suite cannot: that the Data Attachment is actually
 * registered and serialises onto a player, that {@link Stages} drives it correctly through live
 * NeoForge APIs, and that cancelling {@link NwyfreStageChangeEvent} really does veto a change.
 *
 * <p>Stage ids here use the {@code nwyfre_test} namespace so these tests never depend on -- and can
 * never be broken by -- whatever stages the mod or a pack actually defines.
 */
@GameTestHolder(Nwyfre.MODID)
@PrefixGameTestTemplate(false)
public final class StageGameTests {

    private static final ResourceLocation TEST_STAGE =
            ResourceLocation.fromNamespaceAndPath("nwyfre_test", "gametest_stage");
    private static final ResourceLocation OTHER_STAGE =
            ResourceLocation.fromNamespaceAndPath("nwyfre_test", "other_stage");

    /** Armed only for the duration of the cancellation test; inert otherwise. */
    private static final AtomicBoolean VETO_GRANTS = new AtomicBoolean(false);
    private static boolean vetoListenerAttached = false;

    private StageGameTests() {
    }

    @GameTest(template = "empty")
    public static void grantAddsAndPersistsTheStage(GameTestHelper helper) {
        ServerPlayer player = unplacedPlayer(helper);

        check(helper, Stages.grant(player, TEST_STAGE), "grant should report a change");
        check(helper, Stages.has(player, TEST_STAGE), "player should have the granted stage");
        check(helper, Stages.get(player).size() == 1, "exactly one stage should be present");

        // Prove the stage really rides along on the player's saved data, which is what makes
        // progression survive a restart. Done through the live attachment system rather than a
        // direct codec call, so a missing or misregistered AttachmentType fails here.
        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        check(helper, saved.contains(AttachmentHolder.ATTACHMENTS_NBT_KEY),
                "saved player NBT should carry an attachments block");
        CompoundTag attachments = saved.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        check(helper, attachments.contains("nwyfre:player_stages"),
                "attachments should include nwyfre:player_stages, got " + attachments.getAllKeys());

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void regrantingAnOwnedStageChangesNothing(GameTestHelper helper) {
        ServerPlayer player = unplacedPlayer(helper);

        Stages.grant(player, TEST_STAGE);
        check(helper, !Stages.grant(player, TEST_STAGE), "re-granting should report no change");
        check(helper, Stages.get(player).size() == 1, "re-granting must not duplicate the stage");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void revokeRemovesOnlyTheNamedStage(GameTestHelper helper) {
        ServerPlayer player = unplacedPlayer(helper);

        Stages.grant(player, TEST_STAGE);
        Stages.grant(player, OTHER_STAGE);

        check(helper, Stages.revoke(player, TEST_STAGE), "revoke should report a change");
        check(helper, !Stages.has(player, TEST_STAGE), "revoked stage should be gone");
        check(helper, Stages.has(player, OTHER_STAGE), "other stages must be untouched");
        check(helper, !Stages.revoke(player, TEST_STAGE), "revoking twice should report no change");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clearRemovesEveryStage(GameTestHelper helper) {
        ServerPlayer player = unplacedPlayer(helper);

        Stages.grant(player, TEST_STAGE);
        Stages.grant(player, OTHER_STAGE);

        check(helper, Stages.clear(player) == 2, "clear should report both removals");
        check(helper, Stages.get(player).isEmpty(), "no stages should remain");

        helper.succeed();
    }

    /**
     * The contract KubeJS scripts rely on: cancelling the event must leave the player untouched and
     * make the originating call report failure.
     */
    @GameTest(template = "empty")
    public static void cancellingTheEventVetoesTheGrant(GameTestHelper helper) {
        attachVetoListener();
        ServerPlayer player = unplacedPlayer(helper);
        VETO_GRANTS.set(true);
        try {
            check(helper, !Stages.grant(player, TEST_STAGE), "a cancelled grant should report no change");
            check(helper, !Stages.has(player, TEST_STAGE), "a cancelled grant must not alter the player");
            helper.succeed();
        } finally {
            VETO_GRANTS.set(false);
        }
    }

    private static void attachVetoListener() {
        if (vetoListenerAttached) {
            return;
        }
        // Left attached for the server's lifetime but gated on VETO_GRANTS, so it cannot leak into
        // another test even if one fails partway through.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (NwyfreStageChangeEvent event) -> {
            if (VETO_GRANTS.get() && event.isGrant()) {
                event.setCanceled(true);
            }
        });
        vetoListenerAttached = true;
    }

    /**
     * Builds a real {@link ServerPlayer} <em>without</em> putting it on the server's player list.
     *
     * <p>Deliberately not {@code GameTestHelper.makeMockServerPlayerInLevel()}: that calls
     * {@code PlayerList.placeNewPlayer}, which fires the join handlers of every loaded mod. With the
     * integrations present, KubeJS then tries to push {@code kubejs:sync_server_data} down a
     * connection that cannot accept play payloads, and every test fails before reaching its own
     * assertions.
     *
     * <p>An unplaced player is all these tests need: attachments, events and persistence are all
     * entity-level concerns, and none of them require a connection. Nothing is registered anywhere,
     * so there is nothing to clean up afterwards either.
     */
    private static ServerPlayer unplacedPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return new ServerPlayer(
                level.getServer(),
                level,
                new GameProfile(UUID.randomUUID(), "nwyfre-test-player"),
                ClientInformation.createDefault());
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
