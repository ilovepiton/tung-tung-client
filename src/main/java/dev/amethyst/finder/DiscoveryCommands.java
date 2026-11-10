package dev.amethyst.finder;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.amethyst.finder.ui.DiscoveryScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;

public final class DiscoveryCommands {
    private DiscoveryCommands() {}
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            dispatcher.register(ClientCommands.literal("findplayer")
                .executes(context -> {context.getSource().getClient().gui.setScreen(new DiscoveryScreen(true));return 1;})
                .then(ClientCommands.literal("clear").executes(context -> {
                    try {ChunkFinderClient.PLAYERS.clear();context.getSource().sendFeedback(Component.literal("Find Player: saved marker cleared."));return 1;}
                    catch(Exception error){context.getSource().sendError(Component.literal(error.getMessage()));return 0;}
                }))
                .then(ClientCommands.argument("player", StringArgumentType.word())
                    .suggests((context,builder)->{for(var row:ChunkFinderClient.PLAYERS.rows(context.getSource().getClient()))
                        if(row.observable()&&row.name().toLowerCase(java.util.Locale.ROOT).startsWith(builder.getRemainingLowerCase()))builder.suggest(row.name());return builder.buildFuture();})
                    .executes(context->{try {
                        var pin=ChunkFinderClient.PLAYERS.pin(context.getSource().getClient(),StringArgumentType.getString(context,"player"));
                        context.getSource().sendFeedback(Component.literal("Find Player: "+pin.name()+" · "+pin.coordinates()+" · saved position"));return 1;
                    }catch(Exception error){context.getSource().sendError(Component.literal(error.getMessage()));return 0;}})));
        });
    }
}
