package com.stalkermod.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.stalkermod.StalkerMod;
import com.stalkermod.horror.HorrorEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** /stalker event <footsteps|whisper|branch|appear|jumpscare|haunt|knock|peek>  (op level 2) - for testing. */
@Mod.EventBusSubscriber(modid = StalkerMod.MOD_ID)
public class StalkerCommands {
    private static final String[] TYPES = {"footsteps", "whisper", "branch", "appear", "jumpscare", "haunt", "knock", "peek"};

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("stalker").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("event")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TYPES, builder))
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String type = StringArgumentType.getString(ctx, "type");
                                            boolean ok = HorrorEvents.trigger(player.serverLevel(), player, type);
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    ok ? "Triggered: " + type : "Failed: unknown event, or no door/window/tree spot found nearby: " + type), false);
                                            return ok ? 1 : 0;
                                        }))));
    }
}
