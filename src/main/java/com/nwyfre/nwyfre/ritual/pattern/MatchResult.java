package com.nwyfre.nwyfre.ritual.pattern;

import java.util.Map;
import java.util.Optional;

import com.nwyfre.nwyfre.ritual.Element;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;

/**
 * The outcome of checking one pattern against the world.
 *
 * <p>Carries enough detail to drive the diagnostics as well as the yes/no answer: {@link #missing}
 * is exactly the set of positions to particle-mark, and {@link #offeringSlots} saves the caller
 * re-deriving where the bowls ended up.
 *
 * @param matched whether every demanding cell was satisfied
 * @param missing absolute world positions whose cell was not satisfied, and the cell that failed
 * @param offeringSlots absolute world position of each element's bowl
 */
public record MatchResult(boolean matched, Map<BlockPos, PatternCell> missing,
        Map<ResourceKey<Element>, BlockPos> offeringSlots) {

    public MatchResult(boolean matched, Map<BlockPos, PatternCell> missing,
            Map<ResourceKey<Element>, BlockPos> offeringSlots) {
        this.matched = matched;
        this.missing = Map.copyOf(missing);
        this.offeringSlots = Map.copyOf(offeringSlots);
    }

    public int missingCount() {
        return missing.size();
    }

    public Optional<BlockPos> offeringSlot(ResourceKey<Element> element) {
        return Optional.ofNullable(offeringSlots.get(element));
    }

    /**
     * True when the circle is drawn but no bowls have been placed, which gets its own diagnostic:
     * a player in that state usually does not know bowls are a thing yet.
     */
    public boolean missingOnlyOfferingSlots() {
        return !missing.isEmpty() && missing.values().stream().allMatch(PatternCell::isOfferingSlot);
    }
}
