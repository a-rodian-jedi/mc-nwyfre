package com.nwyfre.nwyfre.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

/**
 * Stage progression locks are called out in CLAUDE.md as a high-risk critical path, so the
 * grant/revoke/query contract and the persistence round-trip are pinned down here. These exercise
 * {@link PlayerStages} directly -- no logic is re-implemented in the test.
 */
class PlayerStagesTest {

    private static final ResourceLocation AWAKENING = ResourceLocation.fromNamespaceAndPath("nwyfre", "awakening");
    private static final ResourceLocation RITES = ResourceLocation.fromNamespaceAndPath("nwyfre", "rites");
    // A pack-authored stage: proves stage ids are not restricted to Nwyfre's own namespace.
    private static final ResourceLocation PACK_STAGE = ResourceLocation.fromNamespaceAndPath("mypack", "bronze_age");

    @Test
    void newPlayerHasNoStages() {
        PlayerStages stages = new PlayerStages();
        assertTrue(stages.isEmpty());
        assertEquals(0, stages.size());
        assertFalse(stages.has(AWAKENING));
    }

    @Test
    void grantReportsWhetherItChangedAnything() {
        PlayerStages stages = new PlayerStages();
        assertTrue(stages.grant(AWAKENING), "first grant should report a change");
        assertFalse(stages.grant(AWAKENING), "re-granting should report no change, so callers can skip sync");
        assertEquals(1, stages.size());
        assertTrue(stages.has(AWAKENING));
    }

    @Test
    void revokeReportsWhetherItChangedAnything() {
        PlayerStages stages = new PlayerStages();
        stages.grant(AWAKENING);
        assertTrue(stages.revoke(AWAKENING));
        assertFalse(stages.revoke(AWAKENING), "revoking an absent stage should report no change");
        assertTrue(stages.isEmpty());
    }

    @Test
    void stagesFromAnyNamespaceAreAccepted() {
        PlayerStages stages = new PlayerStages();
        assertTrue(stages.grant(PACK_STAGE));
        assertTrue(stages.has(PACK_STAGE));
    }

    @Test
    void hasAllRequiresEveryStage() {
        PlayerStages stages = new PlayerStages();
        stages.grant(AWAKENING);
        assertTrue(stages.hasAll(List.of(AWAKENING)));
        assertFalse(stages.hasAll(List.of(AWAKENING, RITES)));
        assertTrue(stages.hasAll(List.of()), "an empty requirement is trivially satisfied");
    }

    @Test
    void hasAnyNeedsOnlyOneStage() {
        PlayerStages stages = new PlayerStages();
        stages.grant(RITES);
        assertTrue(stages.hasAny(List.of(AWAKENING, RITES)));
        assertFalse(stages.hasAny(List.of(AWAKENING)));
        assertFalse(stages.hasAny(List.of()), "an empty candidate set cannot be satisfied");
    }

    @Test
    void clearReportsWhetherItChangedAnything() {
        PlayerStages stages = new PlayerStages();
        assertFalse(stages.clear(), "clearing an empty set should report no change");
        stages.grant(AWAKENING);
        assertTrue(stages.clear());
        assertTrue(stages.isEmpty());
    }

    @Test
    void viewIsUnmodifiableAndOrdered() {
        PlayerStages stages = new PlayerStages();
        stages.grant(RITES);
        stages.grant(AWAKENING);
        assertEquals(List.of(RITES, AWAKENING), List.copyOf(stages.view()), "insertion order should be preserved");
        assertThrows(UnsupportedOperationException.class, () -> stages.view().add(PACK_STAGE));
    }

    @Test
    void copyIsIndependentOfTheOriginal() {
        PlayerStages original = new PlayerStages();
        original.grant(AWAKENING);
        PlayerStages copy = original.copy();
        copy.grant(RITES);
        assertFalse(original.has(RITES), "mutating a copy must not affect the original");
        assertTrue(copy.has(AWAKENING));
    }

    @Test
    void survivesACodecRoundTrip() {
        PlayerStages original = new PlayerStages();
        original.grant(AWAKENING);
        original.grant(PACK_STAGE);

        var encoded = PlayerStages.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(message -> new AssertionError("encode failed: " + message));
        PlayerStages decoded = PlayerStages.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError("decode failed: " + message));

        assertEquals(original, decoded, "a player's stages must survive being saved and reloaded");
        assertEquals(Set.of(AWAKENING, PACK_STAGE), decoded.view());
    }
}
