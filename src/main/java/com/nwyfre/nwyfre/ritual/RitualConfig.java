package com.nwyfre.nwyfre.ritual;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The {@code [rituals]} section of {@code nwyfre-common.toml}.
 *
 * <p>Lives next to the ritual code rather than in {@link com.nwyfre.nwyfre.Config} so the ritual
 * system owns its own knobs; {@code Config} just folds this section into the one spec NeoForge
 * loads. Pack authors are expected to retune most of these, which is the point.
 */
public final class RitualConfig {

    public final ModConfigSpec.DoubleValue durationMultiplier;
    public final ModConfigSpec.BooleanValue strictOfferings;
    public final ModConfigSpec.BooleanValue consumeAugments;
    public final ModConfigSpec.IntValue augmentSearchRadius;
    public final ModConfigSpec.IntValue sacrificeRange;
    public final ModConfigSpec.IntValue itemUseRange;
    public final ModConfigSpec.IntValue persistentTickInterval;
    public final ModConfigSpec.BooleanValue allowUnattendedStagedRituals;
    public final ModConfigSpec.IntValue diagnosticsRateLimitTicks;

    public RitualConfig(ModConfigSpec.Builder builder) {
        builder.comment("Ritual system tuning.").push("rituals");

        durationMultiplier = builder
                .comment("Scales every ritual duration. Below 1.0 is faster, above is slower.")
                .defineInRange("durationMultiplier", 1.0, 0.1, 10.0);

        strictOfferings = builder
                .comment("When true, a bowl the pattern defines but the recipe does not ask for must",
                        "be empty. Leaving this on is what keeps two similar rituals unambiguous;",
                        "turning it off lets players leave unrelated items lying in spare bowls.")
                .define("strictOfferings", true);

        consumeAugments = builder
                .comment("When true, augment items are consumed when the ritual starts.",
                        "Default is false: augments sit in a nearby container and act as reusable",
                        "catalysts rather than ingredients.")
                .define("consumeAugments", false);

        augmentSearchRadius = builder
                .comment("How far from the altar to look for a container holding augments.")
                .defineInRange("augmentSearchRadius", 3, 1, 16);

        sacrificeRange = builder
                .comment("How close to the altar a sacrifice must die to count.")
                .defineInRange("sacrificeRange", 8, 1, 64);

        itemUseRange = builder
                .comment("How close to the altar a required item must be used to count.")
                .defineInRange("itemUseRange", 16, 1, 64);

        persistentTickInterval = builder
                .comment("Ticks between beats of a lit persistent ritual. Higher is cheaper.")
                .defineInRange("persistentTickInterval", 20, 1, 1200);

        allowUnattendedStagedRituals = builder
                .comment("When true, a ritual gated on a game stage may be started by automation.",
                        "Default is false: a machine has no player, so there is no stage set to",
                        "check, and allowing it would silently bypass progression.")
                .define("allowUnattendedStagedRituals", false);

        diagnosticsRateLimitTicks = builder
                .comment("Minimum ticks between diagnostic reports from one altar. Throttles the",
                        "all-patterns diff, which is the only expensive part of the ritual system.")
                .defineInRange("diagnosticsRateLimitTicks", 20, 0, 200);

        builder.pop();
    }
}
