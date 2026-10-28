package com.nwyfre.nwyfre.ritual.pattern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.ritual.Element;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceKey;

/**
 * The physical circle a ritual must be built in: a layered grid of {@link PatternCell}s, resolved
 * once at load into plain offsets from the altar so that matching is a flat map walk.
 *
 * <p><strong>Patterns never rotate or mirror.</strong> Direction carries meaning here -- north is
 * earth, south is fire -- so a rotating matcher would silently break the element convention. It
 * also keeps the matcher trivial. Authors write the circle facing the right way.
 *
 * <p>Grid convention, chosen so a layer reads like a map with north at the top:
 * <ul>
 *   <li>row index increases toward <em>south</em> (+Z)</li>
 *   <li>column index increases toward <em>east</em> (+X)</li>
 *   <li>the single {@code nwyfre:altar} cell fixes the X/Z origin</li>
 *   <li>Y comes from {@code offsetY + layerIndex}, so the altar cell must land on Y 0</li>
 * </ul>
 * With the default {@code offsetY} of 0 that means the altar is drawn in {@code layers[0]} and
 * everything after it is a layer above. A pattern that wants glyphs <em>below</em> the altar sets
 * {@code offset_y: -1} and draws the altar in {@code layers[1]}.
 *
 * @param offsetY Y of {@code layers[0]} relative to the altar
 * @param layers the raw grid, kept so previews and error messages can show what the author wrote
 * @param mapping character to cell
 * @param cells the resolved form: offset from the altar to the cell demanded there
 * @param offeringSlots offset of each element's bowl, derived from the cells
 * @param size bounding size of the raw grid, for preview layout
 */
public record RitualPattern(int offsetY, List<List<String>> layers, Map<Character, PatternCell> mapping,
        Map<BlockPos, PatternCell> cells, Map<ResourceKey<Element>, BlockPos> offeringSlots, Vec3i size) {

    public static final ResourceKey<Registry<RitualPattern>> REGISTRY =
            ResourceKey.createRegistryKey(Nwyfre.id("ritual_pattern"));

    public static final ResourceKey<Registry<Element>> ELEMENT_REGISTRY =
            ResourceKey.createRegistryKey(Nwyfre.id("element"));

    /**
     * Kept separate from {@link #CODEC} because chaining {@code validate} directly onto
     * {@code create} defeats type inference on the builder group.
     */
    private static final Codec<RitualPattern> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("offset_y", 0).forGetter(RitualPattern::offsetY),
                    Codec.STRING.listOf().listOf().fieldOf("layers").forGetter(RitualPattern::layers),
                    Codec.unboundedMap(characterCodec(), PatternCell.CODEC).fieldOf("mapping")
                            .forGetter(RitualPattern::mapping))
            .apply(instance, RitualPattern::of));

    public static final Codec<RitualPattern> CODEC = RAW_CODEC.validate(RitualPattern::validate);

    /** JSON object keys are strings, but a mapping key is one character. */
    private static Codec<Character> characterCodec() {
        return Codec.STRING.comapFlatMap(
                string -> string.length() == 1
                        ? DataResult.success(string.charAt(0))
                        : DataResult.error(() -> "Mapping keys must be exactly one character, got: " + string),
                String::valueOf);
    }

    /** Builds a pattern from the authored form, resolving the grid into offsets. */
    public static RitualPattern of(int offsetY, List<List<String>> layers,
            Map<Character, PatternCell> mapping) {
        List<List<String>> safeLayers = List.copyOf(layers);
        Map<Character, PatternCell> safeMapping = Map.copyOf(mapping);
        Resolved resolved = resolve(offsetY, safeLayers, safeMapping);
        return new RitualPattern(offsetY, safeLayers, safeMapping,
                resolved.cells(), resolved.offeringSlots(), resolved.size());
    }

    private record Resolved(Map<BlockPos, PatternCell> cells,
            Map<ResourceKey<Element>, BlockPos> offeringSlots, Vec3i size) {
    }

    /**
     * Walks the grid and rewrites every cell as an offset from the altar.
     *
     * <p>Returns empty maps on a malformed grid rather than throwing; {@link #validate} is what
     * turns malformed input into a codec error carrying a readable message.
     */
    private static Resolved resolve(int offsetY, List<List<String>> layers,
            Map<Character, PatternCell> mapping) {
        int anchorColumn = -1;
        int anchorRow = -1;

        for (int layerIndex = 0; layerIndex < layers.size() && anchorRow < 0; layerIndex++) {
            List<String> rows = layers.get(layerIndex);
            for (int rowIndex = 0; rowIndex < rows.size() && anchorRow < 0; rowIndex++) {
                String row = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < row.length(); columnIndex++) {
                    PatternCell cell = mapping.get(row.charAt(columnIndex));
                    if (cell != null && cell.predicate() instanceof PatternCell.Predicate.IsAltar) {
                        anchorColumn = columnIndex;
                        anchorRow = rowIndex;
                        break;
                    }
                }
            }
        }

        int width = layers.stream().flatMap(List::stream).mapToInt(String::length).max().orElse(0);
        int depth = layers.stream().mapToInt(List::size).max().orElse(0);
        Vec3i size = new Vec3i(width, layers.size(), depth);

        if (anchorRow < 0) {
            return new Resolved(Map.of(), Map.of(), size);
        }

        Map<BlockPos, PatternCell> cells = new HashMap<>();
        Map<ResourceKey<Element>, BlockPos> slots = new HashMap<>();

        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            List<String> rows = layers.get(layerIndex);
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                String row = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < row.length(); columnIndex++) {
                    PatternCell cell = mapping.get(row.charAt(columnIndex));
                    if (cell == null) {
                        continue;
                    }
                    BlockPos offset = new BlockPos(
                            columnIndex - anchorColumn,
                            offsetY + layerIndex,
                            rowIndex - anchorRow);
                    cells.put(offset, cell);
                    final BlockPos slot = offset;
                    cell.offering().ifPresent(element -> slots.put(element, slot));
                }
            }
        }

        return new Resolved(Map.copyOf(cells), Map.copyOf(slots), size);
    }

    private static DataResult<RitualPattern> validate(RitualPattern pattern) {
        List<String> problems = new ArrayList<>();

        long anchors = pattern.layers.stream()
                .flatMap(List::stream)
                .flatMapToInt(String::chars)
                .mapToObj(codePoint -> pattern.mapping.get((char) codePoint))
                .filter(cell -> cell != null && cell.predicate() instanceof PatternCell.Predicate.IsAltar)
                .count();
        if (anchors != 1) {
            problems.add("needs exactly one nwyfre:altar cell, found " + anchors);
        }

        for (List<String> layer : pattern.layers) {
            Set<Integer> widths = new HashSet<>();
            layer.forEach(row -> widths.add(row.length()));
            if (widths.size() > 1) {
                problems.add("every row in a layer must be the same length, found lengths " + widths);
                break;
            }
        }

        if (anchors == 1 && !pattern.cells.containsKey(BlockPos.ZERO)) {
            problems.add("the nwyfre:altar cell must land on Y 0; check offset_y against the layer "
                    + "the altar is drawn in");
        }

        Map<ResourceKey<Element>, Integer> elementCounts = new HashMap<>();
        for (PatternCell cell : pattern.mapping.values()) {
            cell.offering().ifPresent(element -> elementCounts.merge(element, 1, Integer::sum));
        }
        elementCounts.forEach((element, count) -> {
            if (count > 1) {
                problems.add("two cells both claim the offering slot for " + element.location());
            }
        });

        return problems.isEmpty()
                ? DataResult.success(pattern)
                : DataResult.error(() -> "Invalid ritual pattern: " + String.join("; ", problems));
    }

    /** The elements this circle has bowls for, whether or not a given recipe uses them all. */
    public Set<ResourceKey<Element>> elements() {
        return offeringSlots.keySet();
    }

    public Optional<BlockPos> offeringSlotOffset(ResourceKey<Element> element) {
        return Optional.ofNullable(offeringSlots.get(element));
    }

    /**
     * Number of cells the matcher will actually check, used to rank near-misses.
     *
     * <p>Must stay in step with what {@code PatternMatcher} skips, or a score can go negative.
     */
    public int demandingCellCount() {
        return (int) cells.values().stream()
                .filter(cell -> !cell.isWildcard() && !cell.isAnchor())
                .count();
    }
}
