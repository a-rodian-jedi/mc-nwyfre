package com.nwyfre.nwyfre.ritual;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * One of the elements an offering can be made to.
 *
 * <p>A datapack registry value, so a pack can invent a fifth element from JSON with no Java change.
 * The element itself carries no position: <em>where</em> its bowl sits is a property of each
 * {@code RitualPattern}, because different circles put their bowls at different distances. The
 * {@link #direction} here is display metadata, used by the diagnostics to say
 * "Earth (north): any crop" and by JEI to lay the slots out on a compass.
 *
 * @param colour particle and UI tint, as packed RGB
 * @param direction the compass direction this element is conventionally placed at
 */
public record Element(int colour, Direction direction) {

    public static final Codec<Element> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("colour").forGetter(Element::colour),
            Direction.CODEC.fieldOf("direction").forGetter(Element::direction))
            .apply(instance, Element::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Element> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, Element::colour,
            ByteBufCodecs.idMapper(Direction::from3DDataValue, Direction::get3DDataValue), Element::direction,
            Element::new);

    /** Translation key for an element id, e.g. {@code element.nwyfre.earth}. */
    public static String translationKey(ResourceLocation id) {
        return "element." + id.getNamespace() + "." + id.getPath();
    }

    public static Component displayName(ResourceKey<Element> key) {
        return Component.translatable(translationKey(key.location()));
    }

    /** "Earth (north)" -- the form the diagnostics and JEI both want. */
    public Component displayNameWithDirection(ResourceKey<Element> key) {
        return Component.translatable("nwyfre.ritual.element_with_direction",
                displayName(key),
                Component.translatable("nwyfre.direction." + direction.getSerializedName()));
    }
}
