package com.nwyfre.nwyfre.registry;

import java.util.function.Supplier;

import com.nwyfre.nwyfre.Nwyfre;
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

    private NwyfreAttachments() {
    }
}
