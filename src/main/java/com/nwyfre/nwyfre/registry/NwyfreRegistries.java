package com.nwyfre.nwyfre.registry;

import java.util.Optional;

import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.ritual.Element;
import com.nwyfre.nwyfre.ritual.pattern.RitualPattern;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * Datapack registries: elements and ritual patterns.
 *
 * <p>These are datapack registries rather than code registries on purpose. A pack author can drop
 * a new circle into {@code data/<ns>/ritual_pattern/}, or invent a fifth element in
 * {@code data/<ns>/element/}, with no Java at all -- which is the whole point of Nwyfre.
 *
 * <p>Both are registered <em>with</em> a network codec, so they sync to clients. That is what lets
 * JEI lay out offering slots by direction and Patchouli draw a pattern preview without the client
 * having to ask the server for the shape.
 */
@EventBusSubscriber(modid = Nwyfre.MODID)
public final class NwyfreRegistries {

    public static final ResourceKey<Registry<Element>> ELEMENT = RitualPattern.ELEMENT_REGISTRY;
    public static final ResourceKey<Registry<RitualPattern>> RITUAL_PATTERN = RitualPattern.REGISTRY;

    private NwyfreRegistries() {
    }

    @SubscribeEvent
    static void onNewDataPackRegistry(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(ELEMENT, Element.CODEC, Element.CODEC);
        event.dataPackRegistry(RITUAL_PATTERN, RitualPattern.CODEC, RitualPattern.CODEC);
    }

    public static Registry<Element> elements(RegistryAccess access) {
        return access.registryOrThrow(ELEMENT);
    }

    public static Registry<RitualPattern> patterns(RegistryAccess access) {
        return access.registryOrThrow(RITUAL_PATTERN);
    }

    public static Optional<RitualPattern> pattern(RegistryAccess access, ResourceKey<RitualPattern> key) {
        return patterns(access).getOptional(key);
    }
}
