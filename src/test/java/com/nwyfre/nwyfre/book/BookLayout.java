package com.nwyfre.nwyfre.book;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A test-scope lint for Patchouli book <em>layout</em>, not book content.
 *
 * <p>Patchouli 1.20 removed the datapack content layout: {@code book.json} must set
 * {@code use_resource_pack} and all categories/entries must live under {@code assets/}. Getting
 * this wrong does not throw at build time -- the book is simply skipped at runtime with one line in
 * the log -- so it is exactly the kind of mistake worth catching in CI.
 *
 * <p>This deliberately checks only structural invariants an author cannot legitimately violate. It
 * says nothing about titles, prose, page counts, or which entries exist, so the real book stays
 * free to change without breaking tests.
 */
final class BookLayout {

    private BookLayout() {
    }

    /**
     * @param dataBookDir  the book directory under a {@code data/} tree (holds {@code book.json})
     * @param assetBookDir the book directory under an {@code assets/} tree (holds the content)
     * @return a list of human-readable problems; empty means the layout is valid
     */
    static List<String> problems(Path dataBookDir, Path assetBookDir) {
        List<String> problems = new ArrayList<>();

        Path bookJson = dataBookDir.resolve("book.json");
        if (!Files.isRegularFile(bookJson)) {
            problems.add("missing book.json at " + bookJson);
            return problems;
        }

        JsonObject book = readObject(bookJson);
        JsonElement useResourcePack = book.get("use_resource_pack");
        if (useResourcePack == null || !useResourcePack.getAsBoolean()) {
            problems.add("book.json must set \"use_resource_pack\": true "
                    + "(Patchouli removed the data-pack content layout in 1.20)");
        }

        for (String contentDir : List.of("categories", "entries")) {
            if (Files.isDirectory(dataBookDir.resolve("en_us").resolve(contentDir))) {
                problems.add("book content found under data/ at en_us/" + contentDir
                        + " -- categories and entries must live under assets/");
            }
        }

        if (!Files.isDirectory(assetBookDir)) {
            problems.add("no book content directory under assets/ at " + assetBookDir);
            return problems;
        }

        List<String> categories = fileNamesIn(assetBookDir.resolve("en_us").resolve("categories"));
        if (categories.isEmpty()) {
            problems.add("book declares no categories under assets/en_us/categories");
        }

        // Every entry must point at a category that exists, or Patchouli drops the entry.
        Path entriesDir = assetBookDir.resolve("en_us").resolve("entries");
        if (Files.isDirectory(entriesDir)) {
            for (Path entry : jsonFilesUnder(entriesDir)) {
                JsonElement category = readObject(entry).get("category");
                if (category == null) {
                    problems.add("entry " + entriesDir.relativize(entry) + " declares no category");
                    continue;
                }
                String path = category.getAsString();
                int colon = path.indexOf(':');
                String name = colon >= 0 ? path.substring(colon + 1) : path;
                if (!categories.contains(name)) {
                    problems.add("entry " + entriesDir.relativize(entry) + " references unknown category '"
                            + path + "'; known categories: " + categories);
                }
            }
        }

        return problems;
    }

    /** Resolves a directory that lives on the test classpath (an exploded resources dir). */
    static Optional<Path> onClasspath(String absoluteResourcePath) {
        URL url = BookLayout.class.getResource(absoluteResourcePath);
        if (url == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(url.toURI()));
        } catch (URISyntaxException e) {
            throw new IllegalStateException("could not resolve " + absoluteResourcePath, e);
        }
    }

    private static JsonObject readObject(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }

    private static List<String> fileNamesIn(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("could not list " + dir, e);
        }
    }

    private static List<Path> jsonFilesUnder(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk " + dir, e);
        }
    }
}
