package com.nwyfre.nwyfre.ritual.diagnostic;

import java.util.List;
import java.util.function.Function;

import com.nwyfre.nwyfre.Config;
import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.registry.NwyfreParticles;
import com.nwyfre.nwyfre.registry.NwyfreRegistries;
import com.nwyfre.nwyfre.ritual.Element;
import com.nwyfre.nwyfre.ritual.RitualRecipe;
import com.nwyfre.nwyfre.ritual.RitualSite;
import com.nwyfre.nwyfre.ritual.prereq.PrerequisiteContext;
import com.nwyfre.nwyfre.ritual.prereq.StagePrerequisite;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The side-effecting half of the diagnostics: gathers the facts, asks
 * {@link RitualDiagnostics} what to say, then says it.
 *
 * <p>Everything expensive is here and gated. Comparing the world against every registered pattern
 * is the only costly operation in the ritual system, so it runs only when a player actually asked
 * -- never on automated insertion -- and no more often than
 * {@code diagnosticsRateLimitTicks} per altar.
 */
public final class RitualDiagnosticsSender {

    private RitualDiagnosticsSender() {
    }

    /**
     * Explains to {@code player} why nothing happened.
     *
     * @param lastSentTick when this altar last reported, for rate limiting
     * @return the tick at which the report was sent, or {@code lastSentTick} if it was suppressed
     */
    public static long explain(ServerLevel level, BlockPos altarPos, @Nullable ServerPlayer player,
            ItemStack activationItem, long lastSentTick) {
        // No player means automation. There is nobody to tell, and the all-patterns diff is the
        // one thing in this system worth avoiding on a hopper tick.
        if (player == null) {
            return lastSentTick;
        }

        long now = level.getGameTime();
        int cooldown = Config.RITUALS.diagnosticsRateLimitTicks.get();
        if (lastSentTick > 0 && now - lastSentTick < cooldown) {
            return lastSentTick;
        }

        List<RitualSite.Candidate> candidates = RitualSite.allRituals(level).stream()
                .map(holder -> RitualSite.evaluate(level, altarPos, holder))
                .flatMap(java.util.Optional::stream)
                .toList();

        PrerequisiteContext context = PrerequisiteContext.of(level, altarPos, player);
        DiagnosticReport report = RitualDiagnostics.diagnose(
                candidates, activationItem, context, elementNamer(level));

        if (!report.hasAnythingToSay()) {
            return now;
        }

        report.lines().forEach(line ->
                player.sendSystemMessage(line.copy().withStyle(ChatFormatting.GRAY)));
        mark(level, report.marks());
        logStageDetail(report, candidates, player);

        return now;
    }

    /** Renders an element as "Earth (north)", falling back to its id if it somehow went missing. */
    private static Function<ResourceKey<Element>, Component> elementNamer(ServerLevel level) {
        Registry<Element> elements = NwyfreRegistries.elements(level.registryAccess());
        return key -> elements.getOptional(key)
                .map(element -> element.displayNameWithDirection(key))
                .orElseGet(() -> Component.literal(key.location().toString()));
    }

    /**
     * Highlights the positions the player needs to look at.
     *
     * <p>This is the part that turns "something is wrong with your circle" into "that block,
     * there". Capped so a wildly wrong circle cannot spam thousands of particles.
     */
    private static void mark(ServerLevel level, List<BlockPos> marks) {
        marks.stream().limit(64).forEach(pos -> level.sendParticles(
                NwyfreParticles.RITUAL_MARKER.get(),
                pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                4, 0.15, 0.15, 0.15, 0.0));
    }

    /**
     * Logs the stage ids behind a stage refusal at debug level.
     *
     * <p>The player is told only that the rite is beyond them: stage ids are pack-internal
     * bookkeeping and a spoiler. A pack author debugging their own progression still needs them,
     * so they go to the log instead of to chat.
     */
    private static void logStageDetail(DiagnosticReport report,
            List<RitualSite.Candidate> candidates, ServerPlayer player) {
        if (report.rung() != DiagnosticReport.Rung.PREREQUISITES || !Nwyfre.LOGGER.isDebugEnabled()) {
            return;
        }
        candidates.stream()
                .map(candidate -> candidate.recipe().value())
                .map(RitualRecipe::prerequisites)
                .flatMap(java.util.Optional::stream)
                .forEach(tree -> collectStages(tree).forEach(stage -> Nwyfre.LOGGER.debug(
                        "Ritual stage gate unmet for {}: {}", player.getGameProfile().getName(), stage)));
    }

    private static List<String> collectStages(
            com.nwyfre.nwyfre.ritual.prereq.RitualPrerequisite term) {
        if (term instanceof StagePrerequisite stage) {
            return stage.stages().stream().map(Object::toString).toList();
        }
        if (term instanceof com.nwyfre.nwyfre.ritual.prereq.RitualPrerequisite.AllOf allOf) {
            return allOf.terms().stream().flatMap(child -> collectStages(child).stream()).toList();
        }
        if (term instanceof com.nwyfre.nwyfre.ritual.prereq.RitualPrerequisite.AnyOf anyOf) {
            return anyOf.terms().stream().flatMap(child -> collectStages(child).stream()).toList();
        }
        if (term instanceof com.nwyfre.nwyfre.ritual.prereq.RitualPrerequisite.Not not) {
            return collectStages(not.term());
        }
        return List.of();
    }
}
