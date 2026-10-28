package com.nwyfre.nwyfre.ritual.diagnostic;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Why a ritual did not start, as something a player can act on.
 *
 * <p>Separating the report from the act of sending it is what makes the whole diagnostic ladder a
 * pure function, and therefore unit-testable: {@link RitualDiagnostics} decides, and
 * {@link RitualDiagnosticsSender} is the only part that touches chat or particles.
 *
 * @param rung which rung of the ladder was reached, so callers can branch without parsing text
 * @param lines what to tell the player, most important first
 * @param marks absolute world positions to highlight with particles
 */
public record DiagnosticReport(Rung rung, List<Component> lines, List<BlockPos> marks) {

    /**
     * How close the player is, from furthest to nearest.
     *
     * <p>Ordered deliberately: a player with no circle at all needs different help from one whose
     * circle is perfect but who is holding the wrong thing.
     */
    public enum Rung {
        /** No ritual exists whose circle is even partly recognisable here. */
        NOTHING_CLOSE,
        /** The glyphs are drawn but no bowls have been placed. */
        NO_BOWLS,
        /** A circle is recognisable but blocks are missing from it. */
        PATTERN_INCOMPLETE,
        /** The circle is built, but the item in hand starts nothing. */
        WRONG_ACTIVATION,
        /** Right circle, right item, but a bowl is empty or holds the wrong offering. */
        MISSING_OFFERINGS,
        /** A bowl holds something this ritual did not ask for. */
        UNWANTED_OFFERINGS,
        /** Everything is in place, but a prerequisite does not hold. */
        PREREQUISITES,
        /** Nothing is wrong. Only reached if something else refused the start, such as a veto. */
        READY
    }

    public DiagnosticReport(Rung rung, List<Component> lines, List<BlockPos> marks) {
        this.rung = rung;
        this.lines = List.copyOf(lines);
        this.marks = List.copyOf(marks);
    }

    public static DiagnosticReport of(Rung rung, List<Component> lines) {
        return new DiagnosticReport(rung, lines, List.of());
    }

    public static DiagnosticReport of(Rung rung, List<Component> lines, List<BlockPos> marks) {
        return new DiagnosticReport(rung, lines, marks);
    }

    public boolean hasAnythingToSay() {
        return !lines.isEmpty() || !marks.isEmpty();
    }
}
