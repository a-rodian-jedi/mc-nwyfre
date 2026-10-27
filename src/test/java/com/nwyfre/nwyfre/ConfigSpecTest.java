package com.nwyfre.nwyfre;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Smoke test for the headless ModDevGradle/JUnit harness.
 *
 * <p>Its job is to prove the harness itself works: that tests run off-client with the real
 * NeoForge classpath available. Touching {@link Config#SPEC} forces class initialisation of
 * a genuine {@code ModConfigSpec}, which only resolves if NeoForge is really on the test
 * classpath -- so a green run here means the harness is wired correctly.
 *
 * <p>Per CLAUDE.md the substantive coverage belongs on the high-risk systems -- mana math,
 * damage/attribute scaling, stage progression locks, recipe validation -- and those tests
 * should be added alongside each of those systems rather than stubbed out ahead of them.
 */
class ConfigSpecTest {

    @Test
    void modIdMatchesTheValueGradleBuildsResourcesWith() {
        // mod_id in gradle.properties is expanded into neoforge.mods.toml; the @Mod annotation
        // must agree with it or FML will not find the mod at all.
        assertEquals("nwyfre", Nwyfre.MODID);
    }

    @Test
    void configSpecBuildsAgainstTheRealNeoForgeClasspath() {
        assertNotNull(Config.SPEC, "ModConfigSpec should have been built by the Config class initialiser");
    }

    @Test
    void configDefaultsMatchTheShippedValues() {
        assertEquals(42, Config.MAGIC_NUMBER.getDefault().intValue());
        assertTrue(Config.LOG_DIRT_BLOCK.getDefault());
        assertEquals("The magic number is... ", Config.MAGIC_NUMBER_INTRODUCTION.getDefault());
        assertEquals(1, Config.ITEM_STRINGS.getDefault().size());
    }
}
