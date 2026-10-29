package com.nwyfre.nwyfre.block.ogham;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Structural lints on the alphabet and on how a glyph decides which way its stem runs.
 *
 * <p>Nothing here asserts on how a glyph <em>looks</em> -- the textures are the content author's
 * to change. What it pins down is the shape of the alphabet, which is not a matter of taste: Ogham
 * has five aicmí of five, and a letter with the wrong stroke count or a duplicated rank is a glyph
 * players will read as a different letter.
 */
class OghamLetterTest {

    @BeforeAll
    static void bootstrap() {
        // Touching OghamGlyphBlock loads Block, whose static setup needs the vanilla bootstrap.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theAlphabetHasFiveAicmiOfFive() {
        Map<OghamLetter.Aicme, List<OghamLetter>> byAicme = Arrays.stream(OghamLetter.values())
                .collect(Collectors.groupingBy(OghamLetter::aicme));

        assertEquals(OghamLetter.Aicme.values().length, byAicme.size(), "every aicme should be present");
        byAicme.forEach((aicme, letters) ->
                assertEquals(5, letters.size(), aicme + " should hold five letters"));
        assertEquals(25, OghamLetter.values().length);
    }

    @Test
    void everyAicmeRunsOneThroughFive() {
        // For the four counted aicmí the rank is literally the number of strokes drawn, so a
        // duplicate or a gap means two letters that look alike.
        Arrays.stream(OghamLetter.values())
                .collect(Collectors.groupingBy(OghamLetter::aicme))
                .forEach((aicme, letters) -> {
                    Set<Integer> ranks = letters.stream().map(OghamLetter::rank)
                            .collect(Collectors.toSet());
                    assertEquals(Set.of(1, 2, 3, 4, 5), ranks, aicme + " should run 1..5");
                });
    }

    @Test
    void everyLetterIsDistinctlyNamedAndTransliterated() {
        Set<String> blockNames = Arrays.stream(OghamLetter.values())
                .map(OghamLetter::blockName).collect(Collectors.toSet());
        Set<String> transliterations = Arrays.stream(OghamLetter.values())
                .map(OghamLetter::transliteration).collect(Collectors.toSet());

        assertEquals(OghamLetter.values().length, blockNames.size(), "block names must be unique");
        assertEquals(OghamLetter.values().length, transliterations.size(),
                "two letters sharing a transliteration cannot both be written");
    }

    @Test
    void blockNamesArePrefixedAndLowercase() {
        for (OghamLetter letter : OghamLetter.values()) {
            String name = letter.blockName();
            assertTrue(name.startsWith("ogham_"), name + " should be prefixed");
            assertEquals(name.toLowerCase(java.util.Locale.ROOT), name, name + " should be lowercase");
        }
    }

    /**
     * Every glyph needs its sprite, and the generator script keeps its own copy of the alphabet.
     * This is what catches the two drifting apart -- a missing texture renders as the
     * missing-texture checkerboard with no warning at all.
     */
    @Test
    void everyLetterHasATexture() {
        for (OghamLetter letter : OghamLetter.values()) {
            String path = "/assets/nwyfre/textures/block/" + letter.blockName() + ".png";
            assertNotNull(OghamLetterTest.class.getResource(path), "missing texture: " + path);
        }
        assertNotNull(OghamLetterTest.class.getResource(
                "/assets/nwyfre/textures/block/ogham_stem.png"), "missing the bare stem texture");
    }

    // ---- stem axis ---------------------------------------------------------------------------

    @Test
    void aGlyphJoinsTheLineItIsPlacedAgainst() {
        assertEquals(Direction.Axis.X,
                OghamGlyphBlock.chooseAxis(true, false, Direction.Axis.Z));
        assertEquals(Direction.Axis.Z,
                OghamGlyphBlock.chooseAxis(false, true, Direction.Axis.X));
    }

    @Test
    void withNothingToJoinTheWritersFacingDecides() {
        assertEquals(Direction.Axis.Z,
                OghamGlyphBlock.chooseAxis(false, false, Direction.Axis.Z));
        assertEquals(Direction.Axis.X,
                OghamGlyphBlock.chooseAxis(false, false, Direction.Axis.X));
    }

    @Test
    void atACrossingTheWritersFacingBreaksTheTie() {
        // Both axes already carry a line, so there is no "the" line to continue. Falling back
        // keeps placement predictable instead of depending on which neighbour is checked first.
        assertEquals(Direction.Axis.X,
                OghamGlyphBlock.chooseAxis(true, true, Direction.Axis.X));
        assertEquals(Direction.Axis.Z,
                OghamGlyphBlock.chooseAxis(true, true, Direction.Axis.Z));
    }
}
