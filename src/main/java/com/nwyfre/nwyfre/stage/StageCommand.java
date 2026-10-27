package com.nwyfre.nwyfre.stage;

import java.util.Collection;

import com.nwyfre.nwyfre.Nwyfre;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.mojang.brigadier.context.CommandContext;

/**
 * {@code /nwyfre stage ...} -- the operator and pack-author facing way to drive progression.
 *
 * <p>{@code list} is available to everyone for their own stages; granting, revoking and clearing
 * require permission level 2, as they change progression.
 */
@EventBusSubscriber(modid = Nwyfre.MODID)
public final class StageCommand {

    private StageCommand() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nwyfre")
                .then(Commands.literal("stage")
                        .then(Commands.literal("grant")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("stage", ResourceLocationArgument.id())
                                                .executes(StageCommand::grant))))
                        .then(Commands.literal("revoke")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("stage", ResourceLocationArgument.id())
                                                .executes(StageCommand::revoke))))
                        .then(Commands.literal("clear")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(StageCommand::clear)))
                        .then(Commands.literal("list")
                                .executes(context -> listSelf(context))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .requires(source -> source.hasPermission(2))
                                        .executes(StageCommand::listTarget)))));
    }

    private static int grant(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        ResourceLocation stage = ResourceLocationArgument.getId(context, "stage");
        int changed = 0;
        for (ServerPlayer target : targets) {
            if (Stages.grant(target, stage)) {
                changed++;
            }
        }
        final int count = changed;
        context.getSource().sendSuccess(
                () -> Component.literal("Granted stage " + stage + " to " + count + " player(s)"), true);
        return count;
    }

    private static int revoke(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        ResourceLocation stage = ResourceLocationArgument.getId(context, "stage");
        int changed = 0;
        for (ServerPlayer target : targets) {
            if (Stages.revoke(target, stage)) {
                changed++;
            }
        }
        final int count = changed;
        context.getSource().sendSuccess(
                () -> Component.literal("Revoked stage " + stage + " from " + count + " player(s)"), true);
        return count;
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        int removed = 0;
        for (ServerPlayer target : targets) {
            removed += Stages.clear(target);
        }
        final int count = removed;
        context.getSource().sendSuccess(
                () -> Component.literal("Cleared " + count + " stage(s)"), true);
        return count;
    }

    private static int listSelf(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return report(context, context.getSource().getPlayerOrException());
    }

    private static int listTarget(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return report(context, EntityArgument.getPlayer(context, "target"));
    }

    private static int report(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        PlayerStages stages = Stages.get(target);
        String message = stages.isEmpty()
                ? target.getGameProfile().getName() + " has no stages"
                : target.getGameProfile().getName() + " has " + stages.size() + " stage(s): "
                        + String.join(", ", stages.view().stream().map(ResourceLocation::toString).toList());
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return stages.size();
    }
}
