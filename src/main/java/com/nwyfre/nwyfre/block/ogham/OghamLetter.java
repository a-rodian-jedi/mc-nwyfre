package com.nwyfre.nwyfre.block.ogham;

import java.util.Locale;

/**
 * The Ogham alphabet, as drawn in {@code references/ogham.png}.
 *
 * <p>Twenty letters in four <em>aicmí</em> of five, distinguished by which side of the stem their
 * strokes fall on and how many there are, plus the five supplementary <em>forfeda</em>, which are
 * bespoke shapes rather than counted strokes.
 *
 * <p>This enum is the source of truth for the set. The block registration, the datagen and
 * {@code tools/generate_ogham_textures.py} all follow it, and {@code OghamLetterTest} pins the
 * shape of the alphabet so a miscounted stroke or a missing letter fails the build rather than
 * shipping as a glyph nobody can read.
 *
 * <p>Letters are named for the tree each one traditionally stands for -- which is also where the
 * tree list in {@code docs/ideas.md} comes from.
 */
public enum OghamLetter {

    // Aicme Beithe -- strokes below the stem.
    BEITH(Aicme.BEITHE, 1, "B"),
    LUIS(Aicme.BEITHE, 2, "L"),
    FEARN(Aicme.BEITHE, 3, "F"),
    SAIL(Aicme.BEITHE, 4, "S"),
    NION(Aicme.BEITHE, 5, "N"),

    // Aicme hUatha -- strokes above the stem.
    UATH(Aicme.HUATHA, 1, "H"),
    DAIR(Aicme.HUATHA, 2, "D"),
    TINNE(Aicme.HUATHA, 3, "T"),
    COLL(Aicme.HUATHA, 4, "C"),
    CEIRT(Aicme.HUATHA, 5, "Q"),

    // Aicme Muine -- strokes crossing the stem at an angle.
    MUIN(Aicme.MUINE, 1, "M"),
    GORT(Aicme.MUINE, 2, "G"),
    NGEADAL(Aicme.MUINE, 3, "NG"),
    STRAIF(Aicme.MUINE, 4, "Z"),
    RUIS(Aicme.MUINE, 5, "R"),

    // Aicme Ailme -- strokes crossing the stem square on.
    AILM(Aicme.AILME, 1, "A"),
    ONN(Aicme.AILME, 2, "O"),
    UR(Aicme.AILME, 3, "U"),
    EDAD(Aicme.AILME, 4, "E"),
    IDAD(Aicme.AILME, 5, "I"),

    // Forfeda -- shapes, not stroke counts. Transliterations follow the reference chart, which
    // differs from some scholarly tables on which of EABHADH / EAMHANCHOLL is "AE" and which "EA".
    EABHADH(Aicme.FORFEDA, 1, "AE"),
    IFIN(Aicme.FORFEDA, 2, "IO"),
    UILLEANN(Aicme.FORFEDA, 3, "UI"),
    OR(Aicme.FORFEDA, 4, "OI"),
    EAMHANCHOLL(Aicme.FORFEDA, 5, "EA");

    /** The five families. The first four are counted strokes; the forfeda are drawn shapes. */
    public enum Aicme {
        BEITHE, HUATHA, MUINE, AILME, FORFEDA
    }

    private final Aicme aicme;
    private final int rank;
    private final String transliteration;

    OghamLetter(Aicme aicme, int rank, String transliteration) {
        this.aicme = aicme;
        this.rank = rank;
        this.transliteration = transliteration;
    }

    public Aicme aicme() {
        return aicme;
    }

    /**
     * Its place within its aicme, which for the four counted aicmí is also its stroke count.
     * The forfeda carry a rank purely to order them.
     */
    public int rank() {
        return rank;
    }

    /** The Latin letter or digraph this stands for, e.g. {@code "NG"}. */
    public String transliteration() {
        return transliteration;
    }

    /** Registry path and texture name, e.g. {@code ogham_beith}. */
    public String blockName() {
        return "ogham_" + name().toLowerCase(Locale.ROOT);
    }
}
