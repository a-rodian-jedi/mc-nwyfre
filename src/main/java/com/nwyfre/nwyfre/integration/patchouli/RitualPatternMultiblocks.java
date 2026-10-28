package com.nwyfre.nwyfre.integration.patchouli;

import java.util.HashMap;
import java.util.Map;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.registry.NwyfreBlocks;
import com.nwyfre.nwyfre.registry.NwyfreRegistries;
import com.nwyfre.nwyfre.ritual.pattern.PatternCell;
import com.nwyfre.nwyfre.ritual.pattern.RitualPattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import vazkii.patchouli.api.IMultiblock;
import vazkii.patchouli.api.IStateMatcher;
import vazkii.patchouli.api.PatchouliAPI;

/**
 * Turns every loaded {@link RitualPattern} into a Patchouli multiblock, so a book page can show the
 * circle as a holographic preview the player can walk around.
 *
 * <p>Generated rather than hand-written. A pattern already describes exactly the shape a player has
 * to build; maintaining a second copy of it as a Patchouli multiblock would guarantee the two
 * eventually disagree, and the book is precisely where a disagreement does the most damage.
 *
 * <p><strong>Timing.</strong> Patterns are a datapack registry, so they do not exist until a world
 * loads. Registration therefore happens on datapack load/sync rather than at mod setup, and the
 * same id is re-registered on every reload so an edited pattern shows its new shape.
 */
public final class RitualPatternMultiblocks {

    private RitualPatternMultiblocks() {
    }

    /**
     * Registers a multiblock for each pattern, replacing any previous version.
     *
     * @param registryAccess registries from the loading server or the synced client
     * @return how many patterns were registered
     */
    public static int registerAll(net.minecraft.core.RegistryAccess registryAccess) {
        Registry<RitualPattern> patterns = NwyfreRegistries.patterns(registryAccess);
        int registered = 0;

        for (Map.Entry<ResourceKey<RitualPattern>, RitualPattern> entry : patterns.entrySet()) {
            try {
                PatchouliAPI.get().registerMultiblock(
                        multiblockId(entry.getKey().location()),
                        toMultiblock(entry.getValue()));
                registered++;
            } catch (RuntimeException exception) {
                // A pattern Patchouli cannot represent must not stop a world loading. The circle
                // still works in-game; only its preview is missing.
                Nwyfre.LOGGER.warn("Could not build a Patchouli preview for pattern {}",
                        entry.getKey().location(), exception);
            }
        }
        return registered;
    }

    /**
     * The multiblock id for a pattern.
     *
     * <p>Namespaced apart from the pattern id so a generated preview can never collide with a
     * hand-written multiblock a pack happens to have named the same thing.
     */
    public static ResourceLocation multiblockId(ResourceLocation patternId) {
        return Nwyfre.id("pattern/" + patternId.getNamespace() + "/" + patternId.getPath());
    }

    /**
     * Converts a pattern into a sparse multiblock.
     *
     * <p>Sparse rather than the string-grid form, because the pattern is already resolved into
     * offsets and a sparse map takes those directly -- no re-deriving a grid that could be
     * transposed by mistake.
     */
    private static IMultiblock toMultiblock(RitualPattern pattern) {
        Map<BlockPos, IStateMatcher> matchers = new HashMap<>();

        pattern.cells().forEach((offset, cell) -> {
            IStateMatcher matcher = matcherFor(cell);
            if (matcher != null) {
                matchers.put(offset, matcher);
            }
        });

        // The altar anchors the circle. Patchouli needs it present as a real matcher even though
        // the game's own matcher skips it, or the preview would show a hole in the middle.
        matchers.put(BlockPos.ZERO,
                PatchouliAPI.get().looseBlockMatcher(NwyfreBlocks.RITUAL_ALTAR.get()));

        IMultiblock multiblock = PatchouliAPI.get().makeSparseMultiblock(matchers);
        // Circles never rotate, and the preview must not imply otherwise: a player shown a mirrored
        // circle would build it facing the wrong way and get bowls at the wrong elements.
        return multiblock.setSymmetrical(false);
    }

    /**
     * The Patchouli matcher for a cell.
     *
     * @return null for cells that place no demand, which are left out of the preview entirely
     */
    private static IStateMatcher matcherFor(PatternCell cell) {
        PatchouliAPI.IPatchouliAPI api = PatchouliAPI.get();
        return switch (cell.predicate()) {
            // Wildcards are omitted rather than matched: showing "anything goes here" as a cell
            // makes a 7x7 circle look far more demanding than it is.
            case PatternCell.Predicate.Any ignored -> null;
            case PatternCell.Predicate.IsAir ignored -> api.airMatcher();
            case PatternCell.Predicate.IsBlock isBlock -> api.looseBlockMatcher(isBlock.block());
            case PatternCell.Predicate.IsTag isTag -> api.tagMatcher(isTag.tag());
            // The anchor is added by the caller, which knows where the origin is.
            case PatternCell.Predicate.IsAltar ignored -> null;
        };
    }
}
