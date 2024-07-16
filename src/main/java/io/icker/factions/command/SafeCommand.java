package io.icker.factions.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.PlayerEvents;
import io.icker.factions.config.Config;
import io.icker.factions.util.Command;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public class SafeCommand implements Command {

    private int run(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        PlayerEvents.OPEN_SAFE.invoker().onOpenSafe(context.getSource().getPlayer());
        return 1;
    }

    @Override
    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager
            .literal("safe")
            .requires(
                Requires.multiple(
                    Requires.hasPerms("faction.safe", 1),
                    Requires.isCommander(),
                    s -> FactionsMod.CONFIG.FACTION_SAFE == Config.SafeOptions.COMMAND || FactionsMod.CONFIG.FACTION_SAFE == Config.SafeOptions.ENABLED
                )
            )
            .executes(this::run)
            .build();
    }
}
