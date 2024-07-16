package io.icker.factions.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.icker.factions.api.persistents.Empire;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

public class KickCommand implements Command {
    private int run(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");

        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (target.getUuid().equals(player.getUuid())) {
            new Message("Cannot kick yourself").format(Formatting.RED).send(player, false);
            return 0;
        }

        User selfUser = User.get(player.getName().getString());
        User targetUser = User.get(target.getName().getString());
        Faction faction = selfUser.getFaction();

        if (targetUser.getFaction().getID() != faction.getID()) {
            new Message("Cannot kick someone that is not in your faction");
            return 0;
        }

        if (selfUser.getRank() == User.Rank.LEADER && (targetUser.getRank() == User.Rank.LEADER || targetUser.getRank() == User.Rank.OWNER)) {
            new Message("Cannot kick members with a higher of equivalent rank").format(Formatting.RED).send(player, false);
            return 0;
        }

        targetUser.leaveFaction();
        context.getSource().getServer().getPlayerManager().sendCommandTree(target);

        new Message("Kicked " + player.getName().getString()).send(player, false);
        new Message("You have been kicked from the faction by " + player.getName().getString()).send(target, false);

        return 1;
    }

    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager
            .literal("kick")
            .requires(Requires.multiple(Requires.isLeader(), Requires.hasPerms("factions.kick", 0)))
            .then(
                CommandManager.argument("player", EntityArgumentType.player()).executes(this::run)
            )
            .then(CommandManager.literal("vassal")
                    .requires(Requires.isEmperor()))
                .then(CommandManager.argument("vassalName", StringArgumentType.string()).suggests(Suggests.allVassals())
                        .executes(this::kickVassal)
                )
            .build();
    }

    private int kickVassal(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String target = StringArgumentType.getString(context, "vassalName");

        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        Faction vassal = Faction.getByName(target);
        User user = User.get(player.getName().getString());
        Faction sourceFaction = user.getFaction();

        if(sourceFaction == null){
            new Message("§cОШИБКА! Твой город не найден!").send(player, false);
            return 0;
        }

        if(vassal == null){
            new Message("§cОШИБКА! Вассал не найден!").send(player, false);
            return 0;
        }

        if (vassal.getID().equals(sourceFaction.getID())) {
            new Message("§cНевозможно кикнуть свою же столицу!").format(Formatting.RED).send(player, false);
            return 0;
        }

        Empire empire = Empire.getEmpireByFaction(sourceFaction.getID());
        if(empire == null){
            new Message("§cОШИБКА! Твоя империя не найдена!!!").send(player, false);
            return 0;
        }

        if(!empire.getVassalsIDList().contains(vassal.getID())){
            new Message("§cИ чего же ты, решил кикнуть вассала, который тебе не принадлежит?").format(Formatting.RED).send(player, false);
            return 0;
        }

        empire.removeVassal(vassal.getID());
        new Message("§6Вассал " + vassal.getName() + " был изгнан из империи - " + empire.name + "!").sendToGlobalChat();
        new Message("§6Ура! Ты добился своего! Твой город теперь обрёл независимость! (Тебя кикнули из империи)").sendToFactionChat(vassal);

        return 1;
    }
}
