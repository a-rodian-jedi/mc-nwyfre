package com.nwyfre.nwyfre.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.nwyfre.nwyfre.stage.PlayerStages;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

/**
 * Exercises the real {@link StageSyncPayload#STREAM_CODEC} over a real buffer. The composite codec
 * is the piece most likely to break silently -- a mismatched encode/decode pair would desync every
 * client's stage view rather than throw -- so the round trip is pinned down here.
 */
class StageSyncPayloadTest {

    private static final ResourceLocation AWAKENING = ResourceLocation.fromNamespaceAndPath("nwyfre", "awakening");
    private static final ResourceLocation PACK_STAGE = ResourceLocation.fromNamespaceAndPath("mypack", "bronze_age");

    private static StageSyncPayload roundTrip(StageSyncPayload original) {
        ByteBuf buffer = Unpooled.buffer();
        StageSyncPayload.STREAM_CODEC.encode(buffer, original);
        StageSyncPayload decoded = StageSyncPayload.STREAM_CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes(), "decode should consume exactly what encode wrote");
        return decoded;
    }

    @Test
    void survivesAnEmptyStageSet() {
        StageSyncPayload decoded = roundTrip(new StageSyncPayload(List.of()));
        assertTrue(decoded.stages().isEmpty());
        assertTrue(decoded.toPlayerStages().isEmpty());
    }

    @Test
    void survivesMultipleStagesAcrossNamespaces() {
        StageSyncPayload decoded = roundTrip(new StageSyncPayload(List.of(AWAKENING, PACK_STAGE)));
        assertEquals(List.of(AWAKENING, PACK_STAGE), decoded.stages());
    }

    @Test
    void convertsToAndFromPlayerStages() {
        PlayerStages source = new PlayerStages();
        source.grant(AWAKENING);
        source.grant(PACK_STAGE);

        PlayerStages result = roundTrip(StageSyncPayload.of(source)).toPlayerStages();

        assertEquals(source, result, "a synced client must end up with the same stage set as the server");
    }

    @Test
    void payloadTypeUsesTheModNamespace() {
        assertEquals("nwyfre", StageSyncPayload.TYPE.id().getNamespace());
        assertEquals("stage_sync", StageSyncPayload.TYPE.id().getPath());
    }
}
