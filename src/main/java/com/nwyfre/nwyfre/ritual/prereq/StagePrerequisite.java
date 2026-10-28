package com.nwyfre.nwyfre.ritual.prereq;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nwyfre.nwyfre.Config;
import com.nwyfre.nwyfre.stage.PlayerStages;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * Gates a ritual on the player's game stages.
 *
 * <p>This is the term none of the reference mods have, and the reason the whole prerequisite tree
 * exists: progression gating has to be data-driven for a modpack to re-tier Nwyfre.
 *
 * <p>Two things make this term special enough for its own file. It is the only term that needs a
 * player, and it is the only one the JEI integration looks for by type: a recipe is hidden from JEI
 * while its stages are unmet, whereas every other term is merely displayed. A night-only ritual
 * must not vanish from JEI in the daytime.
 *
 * <p>Stage ids stay free-form {@link ResourceLocation}s with no registry behind them, exactly as
 * {@code PlayerStages} intends, so a pack can gate on {@code mypack:bronze_age} with no Java.
 */
public record StagePrerequisite(List<ResourceLocation> stages, StagePrerequisite.Mode mode)
        implements RitualPrerequisite {

    public static final MapCodec<StagePrerequisite> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ResourceLocation.CODEC.listOf().fieldOf("stages").forGetter(StagePrerequisite::stages),
                    Mode.CODEC.optionalFieldOf("mode", Mode.ALL).forGetter(StagePrerequisite::mode))
            .apply(instance, StagePrerequisite::new));

    public StagePrerequisite(List<ResourceLocation> stages, Mode mode) {
        this.stages = List.copyOf(stages);
        this.mode = mode;
    }

    @Override
    public MapCodec<? extends RitualPrerequisite> codec() {
        return CODEC;
    }

    @Override
    public boolean test(PrerequisiteContext context) {
        Optional<PlayerStages> playerStages = context.stages();
        if (playerStages.isEmpty()) {
            // Automation has no stage set to check. Letting it through by default would silently
            // bypass progression, so it is refused unless a pack explicitly opts in.
            return Config.RITUALS.allowUnattendedStagedRituals.get();
        }
        return mode == Mode.ALL
                ? playerStages.get().hasAll(stages)
                : playerStages.get().hasAny(stages);
    }

    /**
     * Deliberately vague.
     *
     * <p>Stage ids are pack-internal bookkeeping and leaking them into chat is noise at best and a
     * spoiler at worst. The diagnostics log the actual ids at debug level instead.
     */
    @Override
    public Component describe() {
        return Component.translatable("nwyfre.ritual.prereq.stage");
    }

    /** Whether every stage is needed or just one of them. */
    public enum Mode implements StringRepresentable {
        ALL("all"),
        ANY("any");

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
