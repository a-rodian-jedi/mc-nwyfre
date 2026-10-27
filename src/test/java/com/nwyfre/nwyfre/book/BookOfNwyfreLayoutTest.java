package com.nwyfre.nwyfre.book;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Lints the real Book of Nwyfre's layout.
 *
 * <p>Checks structure only -- that {@code book.json} sets {@code use_resource_pack}, that no
 * content was left under {@code data/}, and that every entry's category resolves. Adding, removing
 * or rewriting entries and prose will not fail this test; only a layout mistake that would make
 * Patchouli silently skip the book will.
 *
 * <p>The lint's own behaviour is covered by {@link BookLayoutTest} against fixed stub books, so this
 * test never needs to encode anything about the book's content.
 */
class BookOfNwyfreLayoutTest {

    private static final String BOOK = "patchouli_books/book_of_nwyfre";

    @Test
    void realBookUsesTheLayoutPatchouliRequires() {
        Path dataBookDir = BookLayout.onClasspath("/data/nwyfre/" + BOOK)
                .orElseThrow(() -> new AssertionError(
                        "book.json is not on the classpath at data/nwyfre/" + BOOK));
        Path assetBookDir = BookLayout.onClasspath("/assets/nwyfre/" + BOOK)
                .orElseThrow(() -> new AssertionError(
                        "book content is not on the classpath at assets/nwyfre/" + BOOK
                                + " -- Patchouli 1.20+ requires it under assets/"));

        assertEquals(List.of(), BookLayout.problems(dataBookDir, assetBookDir),
                "the Book of Nwyfre layout should satisfy Patchouli's requirements");
    }
}
