package com.nwyfre.nwyfre.book;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests the {@link BookLayout} lint itself, against fixed stub books in {@code src/test/resources}.
 *
 * <p>These fixtures are test data and nothing else: they are never shipped, never loaded by the
 * game, and never edited as content. That separation is the point -- the real Book of Nwyfre stays
 * fully the author's to rewrite, while the lint's own behaviour is pinned against books that do not
 * move. {@code legacy} reproduces the exact layout mistake that silently broke the real book.
 */
class BookLayoutTest {

    private static Path fixture(String name) {
        return BookLayout.onClasspath("/fixtures/book/" + name)
                .orElseThrow(() -> new AssertionError("fixture not on test classpath: " + name));
    }

    private static List<String> problemsFor(String fixtureName) {
        Path root = fixture(fixtureName);
        return BookLayout.problems(
                root.resolve("data/patchouli_books/stub_book"),
                root.resolve("assets/patchouli_books/stub_book"));
    }

    @Test
    void acceptsACorrectlySplitBook() {
        assertEquals(List.of(), problemsFor("valid"), "the valid stub book should lint clean");
    }

    @Test
    void rejectsABookWithoutUseResourcePack() {
        assertTrue(problemsFor("legacy").stream().anyMatch(p -> p.contains("use_resource_pack")),
                "should flag the missing use_resource_pack flag; got " + problemsFor("legacy"));
    }

    @Test
    void rejectsContentLeftUnderTheDataTree() {
        assertTrue(problemsFor("legacy").stream().anyMatch(p -> p.contains("under data/")),
                "should flag entries left under data/; got " + problemsFor("legacy"));
    }

    @Test
    void rejectsAnEntryPointingAtAMissingCategory() {
        Path root = fixture("legacy");
        // Point the lint at the legacy book's data tree for both roots, so its stray entries are
        // seen as content and their dangling category reference is checked.
        List<String> problems = BookLayout.problems(
                root.resolve("data/patchouli_books/stub_book"),
                root.resolve("data/patchouli_books/stub_book"));
        assertTrue(problems.stream().anyMatch(p -> p.contains("unknown category")),
                "should flag the dangling category reference; got " + problems);
    }

    @Test
    void reportsAMissingBookJson() {
        Path root = fixture("valid");
        List<String> problems = BookLayout.problems(
                root.resolve("data/patchouli_books/does_not_exist"),
                root.resolve("assets/patchouli_books/stub_book"));
        assertTrue(problems.stream().anyMatch(p -> p.contains("missing book.json")),
                "should flag a missing book.json; got " + problems);
    }
}
