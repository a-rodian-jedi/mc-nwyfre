package com.nwyfre.nwyfre.registry;

import java.util.function.Supplier;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.ritual.RitualState;
import com.nwyfre.nwyfre.stage.PlayerStages;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Persistent per-entity data.
 *
 * <p>Data Attachments -- not capabilities -- are what persist player data on NeoForge 1.21.1;
 * capabilities there are non-persistent behaviour lookups. See CLAUDE.md, Capabilities.
 */
public final class NwyfreAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Nwyfre.MODID);

    /** A player's unlocked game stages. Persists across death so progression is never lost. */
    public static final Supplier<AttachmentType<PlayerStages>> PLAYER_STAGES =
            ATTACHMENT_TYPES.register("player_stages", () -> AttachmentType
                    .builder(PlayerStages::new)
                    .serialize(PlayerStages.CODEC)
                    .copyOnDeath()
                    .build());

    /**
     * The ritual running on an altar block entity.
     *
     * <p>An attachment rather than hand-rolled block entity NBT: the codec is the only description
     * of the format, so there is no second copy to drift. No {@code copyOnDeath} -- block entities
     * do not die -- and the altar writes it back with {@code setData} after every change, since
     * mutating in place would not mark the block entity dirty.
     *
     * <p>There is no meaningful default: an idle altar has no ritual. Callers must therefore use
     * {@code getExistingData} and {@code removeData}, never {@code getData} (which would invoke
     * this factory and store the result) or {@code setData(null)} (which throws).
     */
    public static final Supplier<AttachmentType<RitualState>> RITUAL_STATE =
            ATTACHMENT_TYPES.register("ritual_state", () -> AttachmentType
                    .<RitualState>builder(holder -> {
                        throw new IllegalStateException(
                                "ritual_state has no default; use getExistingData");
                    })
                    .serialize(RitualState.CODEC, state -> state != null)
                    .build());

    private NwyfreAttachments() {
    }
}
