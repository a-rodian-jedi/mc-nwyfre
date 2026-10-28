package com.nwyfre.nwyfre.block.entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.nwyfre.nwyfre.Config;
import com.nwyfre.nwyfre.Nwyfre;
import com.nwyfre.nwyfre.block.RitualAltarBlock;
import com.nwyfre.nwyfre.registry.NwyfreAttachments;
import com.nwyfre.nwyfre.registry.NwyfreBlockEntities;
import com.nwyfre.nwyfre.registry.NwyfreDataComponents;
import com.nwyfre.nwyfre.registry.NwyfreParticles;
import com.nwyfre.nwyfre.ritual.Element;
import com.nwyfre.nwyfre.ritual.ImbuedRitual;
import com.nwyfre.nwyfre.ritual.NwyfreRitualEvent;
import com.nwyfre.nwyfre.ritual.RitualAugment;
import com.nwyfre.nwyfre.ritual.RitualContext;
import com.nwyfre.nwyfre.ritual.RitualEffect;
import com.nwyfre.nwyfre.ritual.RitualRecipe;
import com.nwyfre.nwyfre.ritual.RitualSite;
import com.nwyfre.nwyfre.ritual.RitualState;
import com.nwyfre.nwyfre.ritual.diagnostic.RitualDiagnosticsSender;
import com.nwyfre.nwyfre.ritual.prereq.PrerequisiteContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The centre of a ritual, and the thing that drives its whole lifecycle.
 *
 * <p>Holds exactly one stack: the activation item of a running ritual, or the imbued candle of a
 * persistent one. Running state lives in the {@code RITUAL_STATE} Data Attachment rather than in
 * hand-rolled NBT.
 *
 * <p>The sacrifice and item-use listeners are registered on the game bus only for the lifetime of a
 * running ritual, and are unregistered in {@link #stop} <em>and</em> {@link #onChunkUnloaded}.
 * Missing the chunk-unload path is the easy leak here: an altar whose chunk unloads mid-ritual would
 * otherwise leave a listener attached to a block entity nobody can reach.
 */
public class RitualAltarBlockEntity extends SingleSlotBlockEntity {

    private final Consumer<PlayerInteractEvent.RightClickItem> itemUseListener = this::onPlayerUsedItem;
    private final Consumer<LivingDeathEvent> deathListener = this::onLivingDeath;
    private boolean listenersRegistered;

    /** Resolved lazily from the state, since a player may not be loaded when the chunk is. */
    @Nullable
    private ServerPlayer caster;

    /** Per-altar diagnostic cooldown. Deliberately not persisted: it is a courtesy, not state. */
    private long lastDiagnosticTick;

    public RitualAltarBlockEntity(BlockPos pos, BlockState state) {
        super(NwyfreBlockEntities.RITUAL_ALTAR.get(), pos, state);
    }

    /**
     * Starts a ritual when an activation item is inserted, including by a hopper.
     *
     * <p>Putting the trigger here rather than only in the block interaction is what makes rituals
     * automatable, the same trick Occultism uses. A stack that starts nothing is rejected outright,
     * so automation cannot silently fill the altar with junk.
     */
    @Override
    protected ItemStackHandler createHandler() {
        return new ItemStackHandler(1) {
            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                if (level == null || level.isClientSide() || isRunning() || !getHeld().isEmpty()) {
                    return stack;
                }
                if (RitualSite.findRitual((ServerLevel) level, worldPosition, stack).isEmpty()) {
                    return stack;
                }
                ItemStack leftover = super.insertItem(slot, stack, simulate);
                if (!simulate && leftover.getCount() != stack.getCount()) {
                    tryStart(null);
                }
                return leftover;
            }

            @Override
            protected void onContentsChanged(int slot) {
                contentsChanged();
            }
        };
    }

    // ---- state ------------------------------------------------------------------------------

    /**
     * The running ritual, or null when the altar is idle.
     *
     * <p>Uses {@code getExistingData} rather than {@code getData}: an idle altar genuinely has no
     * state, and {@code getData} would invoke the attachment default and store it.
     */
    @Nullable
    public RitualState getRitualState() {
        return getExistingData(NwyfreAttachments.RITUAL_STATE.get()).orElse(null);
    }

    /**
     * Writes the state back so NeoForge marks this block entity dirty.
     *
     * <p>{@link RitualState} is immutable precisely so this cannot be forgotten: there is no way to
     * change it except by producing a new one and passing it through here.
     *
     * <p>Clearing has to go through {@code removeData} -- {@code setData} rejects null outright.
     */
    private void setRitualState(@Nullable RitualState state) {
        if (state == null) {
            removeData(NwyfreAttachments.RITUAL_STATE.get());
        } else {
            setData(NwyfreAttachments.RITUAL_STATE.get(), state);
        }
        setChanged();
    }

    public boolean isRunning() {
        return getRitualState() != null;
    }

    private Optional<RecipeHolder<RitualRecipe>> recipeOf(RitualState state) {
        return RitualSite.allRituals((ServerLevel) level).stream()
                .filter(holder -> holder.id().equals(state.recipeId()))
                .findFirst();
    }

    // ---- starting ---------------------------------------------------------------------------

    /**
     * Attempts to start a ritual from whatever is in the altar.
     *
     * @return whether a ritual actually started
     */
    public boolean tryStart(@Nullable ServerPlayer player) {
        if (level == null || level.isClientSide() || isRunning()) {
            return false;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        ItemStack activationItem = getHeld();
        if (activationItem.isEmpty()) {
            return false;
        }

        Optional<RitualSite.Candidate> found =
                RitualSite.findRitual(serverLevel, worldPosition, activationItem);
        if (found.isEmpty()) {
            return false;
        }

        RitualSite.Candidate candidate = found.get();
        RitualRecipe recipe = candidate.recipe().value();

        // Prerequisites are evaluated here, at start -- not at datapack load. That is what lets a
        // ritual gate on the time, the weather or where it is being performed.
        PrerequisiteContext context = PrerequisiteContext.of(serverLevel, worldPosition, player);
        if (recipe.prerequisites().isPresent() && !recipe.prerequisites().get().test(context)) {
            return false;
        }

        Optional<RitualAugment> augment = RitualAugment.select(recipe.augments(),
                RitualSite.readAugmentItems(serverLevel, worldPosition));
        int potency = recipe.potencyWith(augment);

        if (NeoForge.EVENT_BUS.post(new NwyfreRitualEvent.Starting(
                serverLevel, worldPosition, candidate.recipe().id(), player, potency)).isCanceled()) {
            // A cancelled start must be indistinguishable from one that never matched: nothing
            // consumed, nothing begun.
            return false;
        }

        augment.ifPresent(matched -> RitualSite.consumeAugment(serverLevel, worldPosition, matched));

        setRitualState(RitualState.starting(
                candidate.recipe().id(),
                Optional.ofNullable(player).map(Player::getUUID),
                potency,
                augment.flatMap(RitualAugment::effect).isPresent()));
        this.caster = player;

        registerListeners();
        setLit(true);
        serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6F, 1.4F);
        contentsChanged();
        return true;
    }

    // ---- ticking ----------------------------------------------------------------------------

    public static void tick(Level level, BlockPos pos, BlockState state, RitualAltarBlockEntity altar) {
        if (level.isClientSide()) {
            return;
        }
        altar.serverTick((ServerLevel) level);
    }

    private void serverTick(ServerLevel serverLevel) {
        RitualState state = getRitualState();
        if (state == null) {
            tickPersistent(serverLevel);
            return;
        }

        RecipeHolder<RitualRecipe> holder = recipeOf(state).orElse(null);
        if (holder == null) {
            // The recipe was removed by a datapack reload while running. Give the items back
            // rather than stranding them.
            Nwyfre.LOGGER.debug("Ritual {} vanished mid-run at {}; interrupting", state.recipeId(), worldPosition);
            stop(serverLevel, state, null, NwyfreRitualEvent.Stopped.Reason.INTERRUPTED);
            return;
        }

        RitualRecipe recipe = holder.value();
        restoreCaster(serverLevel, state);

        Optional<RitualSite.Candidate> resolved = RitualSite.evaluate(serverLevel, worldPosition, holder);
        if (resolved.isEmpty() || !resolved.get().match().matched()) {
            stop(serverLevel, state, recipe, NwyfreRitualEvent.Stopped.Reason.INTERRUPTED);
            return;
        }
        RitualSite.Candidate candidate = resolved.get();

        if (recipe.sustainPrerequisites() && recipe.prerequisites().isPresent()
                && !recipe.prerequisites().get().test(
                        PrerequisiteContext.of(serverLevel, worldPosition, caster))) {
            stop(serverLevel, state, recipe, NwyfreRitualEvent.Stopped.Reason.INTERRUPTED);
            return;
        }

        if (!state.demandsSatisfied(recipe.demands())) {
            // Visibly waiting on the player rather than silently stalled.
            if (serverLevel.getGameTime() % 10L == 0L) {
                spawnParticles(serverLevel, NwyfreParticles.RITUAL_WAITING.get(), 2);
            }
            return;
        }

        RitualState advanced = state.advanced();
        advanced = consumeDueOfferings(serverLevel, recipe, candidate, advanced);
        if (advanced == null) {
            return;
        }

        recipe.effectWith(Optional.empty()).tickRunning(
                contextFor(serverLevel, advanced, candidate.bowlContents()));

        if (advanced.ticksRun() >= recipe.timing().scaledDuration()) {
            complete(serverLevel, advanced, holder, candidate);
        } else {
            setRitualState(advanced);
        }
    }

    /**
     * Takes however many offerings are due by now.
     *
     * <p>Spread across the duration rather than taken up front, so an interrupted ritual costs the
     * player only what it had actually used. An offering that has gone missing interrupts, since
     * the recipe can no longer be honoured.
     *
     * @return the updated state, or {@code null} if the ritual was interrupted
     */
    @Nullable
    private RitualState consumeDueOfferings(ServerLevel serverLevel, RitualRecipe recipe,
            RitualSite.Candidate candidate, RitualState state) {
        List<ResourceKey<Element>> order = recipe.offeringElements().stream()
                .sorted(java.util.Comparator.comparing(key -> key.location().toString()))
                .toList();

        int due = Math.min(order.size(), (int) (state.ticksRun() / recipe.ticksPerOffering()));
        RitualState updated = state;

        while (updated.consumedCount() < due) {
            ResourceKey<Element> element = order.get(updated.consumedCount());
            BlockPos bowlPos = candidate.match().offeringSlot(element).orElse(null);
            ItemStack taken = bowlPos == null
                    ? ItemStack.EMPTY
                    : RitualSite.takeOffering(serverLevel, bowlPos);

            if (taken.isEmpty()) {
                stop(serverLevel, updated, recipe, NwyfreRitualEvent.Stopped.Reason.INTERRUPTED);
                return null;
            }

            updated = updated.withConsumed(taken);
            serverLevel.sendParticles(NwyfreParticles.OFFERING_CONSUMED.get(),
                    bowlPos.getX() + 0.5, bowlPos.getY() + 0.6, bowlPos.getZ() + 0.5,
                    6, 0.1, 0.1, 0.1, 0.01);
            serverLevel.playSound(null, bowlPos, SoundEvents.SOUL_ESCAPE.value(),
                    SoundSource.BLOCKS, 0.5F, 1.3F);
        }
        return updated;
    }

    // ---- finishing --------------------------------------------------------------------------

    private void complete(ServerLevel serverLevel, RitualState state,
            RecipeHolder<RitualRecipe> holder, RitualSite.Candidate candidate) {
        RitualRecipe recipe = holder.value();

        // Re-check before paying out. A ritual valid at the start may not be valid now, and a
        // silent half-apply is worse than an interrupt.
        if (!candidate.match().matched()) {
            stop(serverLevel, state, recipe, NwyfreRitualEvent.Stopped.Reason.INTERRUPTED);
            return;
        }

        RitualEffect effect = resolveEffect(recipe, state);
        RitualContext context = contextFor(serverLevel, state, offeringsConsumed(recipe, state));

        if (recipe.timing().persistent()) {
            imbue(serverLevel, state, recipe);
        } else {
            effect.apply(context);
            setHeld(ItemStack.EMPTY);
            setRitualState(null);
            unregisterListeners();
            setLit(false);
        }

        serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT,
                SoundSource.BLOCKS, 0.7F, 1.2F);
        NeoForge.EVENT_BUS.post(new NwyfreRitualEvent.Completed(
                serverLevel, worldPosition, state.recipeId(), caster, recipe.timing().persistent()));
        contentsChanged();
    }

    /**
     * Binds the finished ritual into the candle that started it.
     *
     * <p>The candle stays in the altar and the altar stays lit, so the circle visibly keeps working
     * rather than producing an object the player has to understand. Because the binding is a data
     * component, the candle is still an item: it can be taken out, carried, and relit on another
     * altar with its remaining burn time intact.
     */
    private void imbue(ServerLevel serverLevel, RitualState state, RitualRecipe recipe) {
        ItemStack candle = getHeld().copy();
        candle.set(NwyfreDataComponents.IMBUED_RITUAL.get(),
                new ImbuedRitual(state.recipeId(), recipe.timing().burnTicks(), state.potency()));
        setHeld(candle);

        setRitualState(null);
        unregisterListeners();
        setLit(true);
    }

    /**
     * Ends a ritual without completing it, returning everything it had taken.
     *
     * <p>Refunding matters: offerings are consumed progressively, so an interrupt halfway through
     * would otherwise quietly eat half the cost of the rite.
     */
    private void stop(ServerLevel serverLevel, RitualState state, @Nullable RitualRecipe recipe,
            NwyfreRitualEvent.Stopped.Reason reason) {
        for (ItemStack consumed : state.consumedOfferings()) {
            Containers.dropItemStack(serverLevel,
                    worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), consumed);
        }
        Containers.dropItemStack(serverLevel,
                worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), getHeld());
        setHeld(ItemStack.EMPTY);

        setRitualState(null);
        unregisterListeners();
        setLit(false);
        serverLevel.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.0F);
        NeoForge.EVENT_BUS.post(new NwyfreRitualEvent.Stopped(
                serverLevel, worldPosition, state.recipeId(), caster, reason));
        contentsChanged();
    }

    /** Called when a player deliberately calls a ritual off. */
    public boolean cancel(@Nullable ServerPlayer player) {
        RitualState state = getRitualState();
        if (state == null || level == null || level.isClientSide()) {
            return false;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        stop(serverLevel, state, recipeOf(state).map(RecipeHolder::value).orElse(null),
                NwyfreRitualEvent.Stopped.Reason.CANCELLED);
        return true;
    }

    // ---- persistent hosting (step 6) --------------------------------------------------------

    /**
     * Runs the lit candle of a persistent ritual.
     *
     * <p>Burn time only advances while lit, so extinguishing a candle genuinely preserves it rather
     * than merely pausing the effect -- which is what the spec asks for, and what makes a candle
     * worth saving for when it is needed.
     */
    private void tickPersistent(ServerLevel serverLevel) {
        if (!isLit()) {
            return;
        }
        ImbuedRitual imbued = heldImbuedRitual();
        if (imbued == null) {
            // Lit with nothing bound in it: an interrupted completion, or somebody took the candle.
            setLit(false);
            return;
        }

        int interval = Config.RITUALS.persistentTickInterval.get();
        if (serverLevel.getGameTime() % interval != 0L) {
            return;
        }

        RecipeHolder<RitualRecipe> holder = RitualSite.allRituals(serverLevel).stream()
                .filter(candidate -> candidate.id().equals(imbued.recipeId()))
                .findFirst()
                .orElse(null);
        if (holder == null) {
            Nwyfre.LOGGER.debug("Imbued candle at {} names missing ritual {}", worldPosition, imbued.recipeId());
            return;
        }

        holder.value().effect().tickPersistent(RitualContext.of(
                serverLevel, worldPosition, caster, imbued.potency(), Map.of(),
                holder.value().timing().burnTicks() - imbued.burnTicksRemaining()));

        ImbuedRitual burned = imbued.burned(interval);
        if (burned.spent()) {
            // Wasted away, as the spec puts it: the candle is gone, not merely inert.
            setHeld(ItemStack.EMPTY);
            setLit(false);
            serverLevel.playSound(null, worldPosition, SoundEvents.CANDLE_EXTINGUISH,
                    SoundSource.BLOCKS, 0.7F, 0.8F);
            NeoForge.EVENT_BUS.post(new NwyfreRitualEvent.Stopped(serverLevel, worldPosition,
                    imbued.recipeId(), caster, NwyfreRitualEvent.Stopped.Reason.EXPIRED));
        } else {
            ItemStack candle = getHeld().copy();
            candle.set(NwyfreDataComponents.IMBUED_RITUAL.get(), burned);
            setHeld(candle);
        }
        contentsChanged();
    }

    /** The ritual bound into whatever the altar is holding, or null if it holds no imbued candle. */
    @Nullable
    public ImbuedRitual heldImbuedRitual() {
        return getHeld().get(NwyfreDataComponents.IMBUED_RITUAL.get());
    }

    public boolean isLit() {
        BlockState state = getBlockState();
        return state.hasProperty(RitualAltarBlock.LIT) && state.getValue(RitualAltarBlock.LIT);
    }

    /**
     * Lights or snuffs an imbued candle.
     *
     * @return whether there was an imbued candle to toggle
     */
    public boolean toggleLit(ServerLevel serverLevel) {
        if (heldImbuedRitual() == null) {
            return false;
        }
        boolean nowLit = !isLit();
        setLit(nowLit);
        serverLevel.playSound(null, worldPosition,
                nowLit ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.CANDLE_EXTINGUISH,
                SoundSource.BLOCKS, 0.7F, 1.0F);
        contentsChanged();
        return true;
    }

    // ---- demands ----------------------------------------------------------------------------

    private void onPlayerUsedItem(PlayerInteractEvent.RightClickItem event) {
        RitualState state = getRitualState();
        if (state == null || level == null || event.getLevel() != level || state.itemUseDone()) {
            return;
        }
        int range = Config.RITUALS.itemUseRange.get();
        if (worldPosition.distSqr(event.getPos()) > (double) range * range) {
            return;
        }
        recipeOf(state).ifPresent(holder -> {
            if (holder.value().demands().isValidItemUse(event.getItemStack())) {
                setRitualState(state.withItemUseDone());
            }
        });
    }

    private void onLivingDeath(LivingDeathEvent event) {
        RitualState state = getRitualState();
        LivingEntity dying = event.getEntity();
        if (state == null || level == null || dying.level() != level || state.sacrificeDone()) {
            return;
        }
        // Only a player-caused death counts, so a ritual cannot be fed by ambient mob deaths.
        if (!(event.getSource().getEntity() instanceof Player)) {
            return;
        }
        int range = Config.RITUALS.sacrificeRange.get();
        if (worldPosition.distSqr(dying.blockPosition()) > (double) range * range) {
            return;
        }
        recipeOf(state).ifPresent(holder -> {
            if (holder.value().demands().isValidSacrifice(dying)) {
                setRitualState(state.withSacrificeDone());
            }
        });
    }

    private void registerListeners() {
        if (listenersRegistered) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(itemUseListener);
        NeoForge.EVENT_BUS.addListener(deathListener);
        listenersRegistered = true;
    }

    private void unregisterListeners() {
        if (!listenersRegistered) {
            return;
        }
        NeoForge.EVENT_BUS.unregister(itemUseListener);
        NeoForge.EVENT_BUS.unregister(deathListener);
        listenersRegistered = false;
    }

    /**
     * Drops the listeners when the chunk goes away.
     *
     * <p>Without this an altar whose chunk unloads mid-ritual leaks a listener holding a block
     * entity nobody can reach again.
     */
    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        unregisterListeners();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        unregisterListeners();
    }

    // ---- helpers ----------------------------------------------------------------------------

    private RitualEffect resolveEffect(RitualRecipe recipe, RitualState state) {
        if (!state.augmentEffectReplaced()) {
            return recipe.effect();
        }
        // The augment itself is not stored, only that it replaced the effect, so the swap is
        // re-derived from the items that are present. Re-selecting is cheap and keeps the saved
        // state small; potency is snapshotted because it is a number the player could game.
        return RitualAugment.select(recipe.augments(),
                        RitualSite.readAugmentItems((ServerLevel) level, worldPosition))
                .flatMap(RitualAugment::effect)
                .orElse(recipe.effect());
    }

    private RitualContext contextFor(ServerLevel serverLevel, RitualState state,
            Map<ResourceKey<Element>, ItemStack> offerings) {
        return RitualContext.of(serverLevel, worldPosition, caster, state.potency(),
                offerings, state.ticksRun());
    }

    /** Pairs each consumed offering back up with the element it was given to. */
    private Map<ResourceKey<Element>, ItemStack> offeringsConsumed(RitualRecipe recipe, RitualState state) {
        List<ResourceKey<Element>> order = recipe.offeringElements().stream()
                .sorted(java.util.Comparator.comparing(key -> key.location().toString()))
                .toList();
        Map<ResourceKey<Element>, ItemStack> offerings = new java.util.HashMap<>();
        for (int i = 0; i < order.size() && i < state.consumedOfferings().size(); i++) {
            offerings.put(order.get(i), state.consumedOfferings().get(i));
        }
        return offerings;
    }

    /**
     * Re-finds the caster after a reload.
     *
     * <p>Retried occasionally rather than every tick: the player may simply not be online, and a
     * ritual without a caster still runs -- it just cannot send messages.
     */
    private void restoreCaster(ServerLevel serverLevel, RitualState state) {
        if (caster != null || state.casterId().isEmpty() || serverLevel.getGameTime() % 100L != 0L) {
            return;
        }
        UUID id = state.casterId().get();
        caster = serverLevel.getServer().getPlayerList().getPlayer(id);
    }

    private void setLit(boolean lit) {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(RitualAltarBlock.LIT) && state.getValue(RitualAltarBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(RitualAltarBlock.LIT, lit), 3);
        }
    }

    private void spawnParticles(ServerLevel serverLevel, SimpleParticleType type, int count) {
        serverLevel.sendParticles(type,
                worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5,
                count, 0.3, 0.2, 0.3, 0.01);
    }

    /**
     * Tells the player why nothing happened.
     *
     * <p>Rate limiting state lives here rather than in the sender so each altar has its own
     * cooldown: two players poking two different circles should not throttle each other.
     */
    public void explainFailure(@Nullable ServerPlayer player, ItemStack activationItem) {
        if (level == null || level.isClientSide()) {
            return;
        }
        lastDiagnosticTick = RitualDiagnosticsSender.explain(
                (ServerLevel) level, worldPosition, player, activationItem, lastDiagnosticTick);
    }

    /** The id of the running ritual, for Jade and the diagnostics. */
    public Optional<ResourceLocation> runningRitualId() {
        RitualState state = getRitualState();
        return state == null ? Optional.empty() : Optional.of(state.recipeId());
    }
}
