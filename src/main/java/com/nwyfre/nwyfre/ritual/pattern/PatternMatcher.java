package com.nwyfre.nwyfre.ritual.pattern;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.nwyfre.nwyfre.ritual.Element;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Checks a {@link RitualPattern} against the blocks around an altar.
 *
 * <p>Takes a {@code Function<BlockPos, BlockState>} rather than a level. That is deliberate: it
 * makes the entire matcher testable headlessly against a plain {@code Map}, which is why almost
 * all of the pattern coverage lives in JUnit rather than in a GameTest. {@link #of(BlockGetter)}
 * adapts a real level for production use.
 */
public final class PatternMatcher {

    private PatternMatcher() {
    }

    /** Adapts a live level to the lookup the matcher wants. */
    public static Function<BlockPos, BlockState> of(BlockGetter level) {
        return level::getBlockState;
    }

    /**
     * Matches {@code pattern} with its altar cell at {@code altarPos}.
     *
     * <p>Wildcard cells are skipped, so they never show up as missing. So is the altar anchor:
     * it exists only to fix the origin, and {@code altarPos} came from an altar the caller already
     * found, so re-testing it could only ever be redundant. Everything else is tested and, on
     * failure, recorded with its absolute world position for the diagnostics.
     */
    public static MatchResult match(RitualPattern pattern, BlockPos altarPos,
            Function<BlockPos, BlockState> blocks) {
        Map<BlockPos, PatternCell> missing = new HashMap<>();

        for (Map.Entry<BlockPos, PatternCell> entry : pattern.cells().entrySet()) {
            PatternCell cell = entry.getValue();
            if (cell.isWildcard() || cell.isAnchor()) {
                continue;
            }
            BlockPos worldPos = altarPos.offset(entry.getKey());
            if (!cell.test(blocks.apply(worldPos))) {
                missing.put(worldPos, cell);
            }
        }

        Map<ResourceKey<Element>, BlockPos> slots = new HashMap<>();
        pattern.offeringSlots().forEach((element, offset) -> slots.put(element, altarPos.offset(offset)));

        return new MatchResult(missing.isEmpty(), missing, slots);
    }

    /**
     * How close the world is to this pattern, as satisfied demanding cells.
     *
     * <p>Used only to pick which pattern to explain when nothing matched: the closest one is almost
     * always the circle the player was actually trying to build.
     */
    public static int score(RitualPattern pattern, BlockPos altarPos, Function<BlockPos, BlockState> blocks) {
        return pattern.demandingCellCount() - match(pattern, altarPos, blocks).missingCount();
    }
}
