package com.nwyfre.nwyfre.network;

import java.util.List;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.stage.PlayerStages;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client mirror of a player's full stage set.
 *
 * <p>Sent as a whole set rather than a delta: the set is tiny, and an absolute value cannot drift
 * out of sync if a packet is missed around login or a dimension change. The client needs this so
 * stage-gated JEI visibility and Patchouli entries can resolve without a server round trip.
 */
public record StageSyncPayload(List<ResourceLocation> stages) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StageSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Nwyfre.MODID, "stage_sync"));

    public static final StreamCodec<ByteBuf, StageSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()),
            StageSyncPayload::stages,
            StageSyncPayload::new);

    public static StageSyncPayload of(PlayerStages stages) {
        return new StageSyncPayload(List.copyOf(stages.view()));
    }

    public PlayerStages toPlayerStages() {
        return PlayerStages.of(stages);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
