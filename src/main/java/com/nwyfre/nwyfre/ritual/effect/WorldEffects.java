package com.nwyfre.nwyfre.ritual.effect;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nwyfre.nwyfre.ritual.RitualContext;
import com.nwyfre.nwyfre.ritual.RitualEffect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * The effects that act on the world rather than on items: time, weather, and area effects.
 *
 * <p>Between them these cover the three worked examples in {@code specs/rituals.md}, so the data
 * format is exercised by real content from the start rather than only by fixtures.
 */
public final class WorldEffects {

    private WorldEffects() {
    }

    /**
     * Skips to a time of day, the way sleeping does.
     *
     * <p>Advances rather than rewinds: setting the time backwards would undo a day of hunger,
     * crop growth and mob despawn timers, which players read as the ritual going wrong. Asking for
     * sunrise at noon therefore reaches sunrise tomorrow.
     */
    public record SetTime(Target target, long ticks) implements RitualEffect {

        public static final MapCodec<SetTime> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Target.CODEC.optionalFieldOf("time", Target.CUSTOM).forGetter(SetTime::target),
                        Codec.LONG.optionalFieldOf("ticks", 0L).forGetter(SetTime::ticks))
                .apply(instance, SetTime::new));

        public enum Target implements StringRepresentable {
            SUNRISE("sunrise", 0L),
            NOON("noon", 6000L),
            SUNSET("sunset", 12000L),
            MIDNIGHT("midnight", 18000L),
            CUSTOM("custom", 0L);

            public static final Codec<Target> CODEC = StringRepresentable.fromEnum(Target::values);

            private final String name;
            final long ticks;

            Target(String name, long ticks) {
                this.name = name;
                this.ticks = ticks;
            }

            @Override
            public String getSerializedName() {
                return name;
            }
        }

        private long targetTicks() {
            return target == Target.CUSTOM ? Math.floorMod(ticks, 24000L) : target.ticks;
        }

        @Override
        public MapCodec<? extends RitualEffect> codec() {
            return CODEC;
        }

        @Override
        public void apply(RitualContext context) {
            ServerLevel level = context.level();
            long current = level.getDayTime();
            long wanted = targetTicks();
            long untilWanted = Math.floorMod(wanted - Math.floorMod(current, 24000L), 24000L);
            // A full day rather than zero when already exactly there, so the rite always does
            // something visible instead of appearing to fail.
            level.setDayTime(current + (untilWanted == 0 ? 24000L : untilWanted));
        }

        /** Repeatedly skipping time would make a lit candle freeze the clock at one moment. */
        @Override
        public boolean supportsPersistent() {
            return false;
        }

        @Override
        public Component describe() {
            return target == Target.CUSTOM
                    ? Component.translatable("nwyfre.ritual.effect.set_time.custom", ticks)
                    : Component.translatable("nwyfre.ritual.effect.set_time." + target.getSerializedName());
        }
    }

    /**
     * Sets the weather.
     *
     * <p>Clearing also cancels an in-progress thunderstorm, which vanilla tracks separately from
     * rain; setting only the rain flag would leave the sky dark and the player confused.
     */
    public record SetWeather(Kind kind, int duration) implements RitualEffect {

        public static final MapCodec<SetWeather> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Kind.CODEC.fieldOf("weather").forGetter(SetWeather::kind),
                        Codec.intRange(1, 1_000_000).optionalFieldOf("duration", 12000)
                                .forGetter(SetWeather::duration))
                .apply(instance, SetWeather::new));

        public enum Kind implements StringRepresentable {
            CLEAR("clear"),
            RAIN("rain"),
            THUNDER("thunder");

            public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

            private final String name;

            Kind(String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return name;
            }
        }

        @Override
        public MapCodec<? extends RitualEffect> codec() {
            return CODEC;
        }

        @Override
        public void apply(RitualContext context) {
            switch (kind) {
                case CLEAR -> context.level().setWeatherParameters(duration, 0, false, false);
                case RAIN -> context.level().setWeatherParameters(0, duration, true, false);
                case THUNDER -> context.level().setWeatherParameters(0, duration, true, true);
            }
        }

        @Override
        public Component describe() {
            return Component.translatable("nwyfre.ritual.effect.set_weather." + kind.getSerializedName());
        }
    }

    /**
     * Applies a mob effect to living entities in a cube around the altar.
     *
     * <p>This is the persistent example from the spec: a lit candle giving regeneration over a
     * 16x16x16 volume is {@code radius: 8}. {@link RitualContext#potency()} is added to the radius,
     * so an augment can widen it without the recipe re-declaring the effect.
     */
    public record AreaEffect(Holder<MobEffect> effect, int amplifier, int radius, int interval,
            boolean affectPlayers, boolean affectOthers) implements RitualEffect {

        public static final MapCodec<AreaEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        MobEffect.CODEC.fieldOf("effect").forGetter(AreaEffect::effect),
                        Codec.intRange(0, 255).optionalFieldOf("amplifier", 0).forGetter(AreaEffect::amplifier),
                        Codec.intRange(1, 64).optionalFieldOf("radius", 8).forGetter(AreaEffect::radius),
                        Codec.intRange(1, 1200).optionalFieldOf("interval", 60).forGetter(AreaEffect::interval),
                        Codec.BOOL.optionalFieldOf("affect_players", true).forGetter(AreaEffect::affectPlayers),
                        Codec.BOOL.optionalFieldOf("affect_others", false).forGetter(AreaEffect::affectOthers))
                .apply(instance, AreaEffect::new));

        @Override
        public MapCodec<? extends RitualEffect> codec() {
            return CODEC;
        }

        private int effectiveRadius(RitualContext context) {
            return Math.min(64, radius + Math.max(0, context.potency()));
        }

        @Override
        public void apply(RitualContext context) {
            int reach = effectiveRadius(context);
            BlockPos altar = context.altarPos();
            AABB area = new AABB(altar).inflate(reach);

            // The effect must outlast the gap to the next beat, or a candle would strobe.
            int effectDuration = interval * 2;

            for (LivingEntity entity : context.level().getEntitiesOfClass(LivingEntity.class, area)) {
                boolean isPlayer = entity instanceof net.minecraft.world.entity.player.Player;
                if (isPlayer ? !affectPlayers : !affectOthers) {
                    continue;
                }
                entity.addEffect(new MobEffectInstance(effect, effectDuration, amplifier, true, false, true));
            }
        }

        @Override
        public Component describe() {
            return Component.translatable("nwyfre.ritual.effect.area_effect",
                    Component.translatable(effect.value().getDescriptionId()), radius * 2);
        }
    }

    /**
     * Runs a server command.
     *
     * <p>The universal escape hatch, the same role Occultism gives {@code execute_command}: it is
     * what lets a pack build a ritual Nwyfre has no effect for, using only datapack files. Runs at
     * permission level 2 from the altar position, with no output echoed to chat.
     */
    public record RunCommand(List<String> commands) implements RitualEffect {

        public static final MapCodec<RunCommand> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.withAlternative(Codec.STRING.listOf(), Codec.STRING, List::of)
                                .fieldOf("commands").forGetter(RunCommand::commands))
                .apply(instance, RunCommand::new));

        public RunCommand(List<String> commands) {
            this.commands = List.copyOf(commands);
        }

        @Override
        public MapCodec<? extends RitualEffect> codec() {
            return CODEC;
        }

        @Override
        public void apply(RitualContext context) {
            ServerLevel level = context.level();
            net.minecraft.server.MinecraftServer server = level.getServer();
            net.minecraft.commands.CommandSourceStack source = server.createCommandSourceStack()
                    .withLevel(level)
                    .withPosition(context.centre())
                    .withPermission(2)
                    .withSuppressedOutput();

            Optional<net.minecraft.server.level.ServerPlayer> caster = context.caster();
            net.minecraft.commands.CommandSourceStack finalSource = caster
                    .map(player -> source.withEntity(player))
                    .orElse(source);

            commands.forEach(command -> server.getCommands().performPrefixedCommand(finalSource, command));
        }

        @Override
        public Component describe() {
            return Component.translatable("nwyfre.ritual.effect.run_command");
        }
    }
}
