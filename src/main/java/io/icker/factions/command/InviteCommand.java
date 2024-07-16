package io.icker.factions.command;

import java.util.List;
import java.util.UUID;

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

public class InviteCommand implements Command {
    private int list(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();

        List<String> invites = User.get(source.getPlayer().getName().getString()).getFaction().getInvites();
        int count = invites.size();

        new Message("У тебя ")
                .add(new Message(String.valueOf(count)).format(Formatting.YELLOW))
                .add(" исходящих приглашений в город")
                .send(source.getPlayer(), false);

        if (count == 0) return 1;

        StringBuilder players = new StringBuilder();
        int length = invites.size();
        for(int i = 0; i < length; i++){
            players.append(invites.get(i));
            if(i == length - 1) players.append(",");
        }

        new Message(players.toString()).format(Formatting.ITALIC).send(source.getPlayer(), false);
        return 1;
    }

    private int add(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");

        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        Faction faction = User.get(source.getPlayer().getName().getString()).getFaction();
        if (faction.isInvited(player.getName().getString())) {
            new Message(target.getName().getString() + " уже приглашён к тебе в город!").format(Formatting.RED).send(player, false);
            return 0;
        }

        User targetUser = User.get(target.getName().getString());
        UUID targetFaction = targetUser.isInFaction() ? targetUser.getFaction().getID() : null;
        if (faction.getID().equals(targetFaction)) {
            new Message(target.getName().getString() + " уже в твоём городе!").format(Formatting.RED).send(player, false);
            return 0;
        }

        faction.addInvite(target.getName().getString());

        new Message(target.getName().getString() + " приглашён в город.")
                .send(faction);
        new Message("Вы были приглашены в этот город").format(Formatting.YELLOW)
                .hover("Нажми, чтобы присоединиться").click("/factions join " + faction.getName())
                .prependFaction(faction)
                .send(target, false);
        return 1;
    }

    private int remove(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");

        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        Faction faction = User.get(player.getName().getString()).getFaction();
        faction.removeInvite(target.getName().getString());

        new Message("Приглашение у игрока " +target.getName().getString() + " потеряло свою актуальность").send(player, false);
        return 1;
    }

    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager
            .literal("invite")
            .requires(Requires.isCommander())
            .then(
                CommandManager
                .literal("list")
                .requires(Requires.hasPerms("factions.invite.list", 0))
                .executes(this::list)
            )
            .then(
                CommandManager
                .literal("add")
                .requires(Requires.hasPerms("factions.invite.add", 0))
                .then(
                    CommandManager.argument("player", EntityArgumentType.player())
                    .executes(this::add)
                )
            )
            .then(
                CommandManager
                .literal("remove")
                .requires(Requires.hasPerms("factions.invite.remove", 0))
                .then(
                    CommandManager.argument("player", EntityArgumentType.player())
                    .executes(this::remove)
                )
            )
                .then(
                        CommandManager.literal("to-empire").requires(Requires.isEmperor())
                                .then(CommandManager.literal("add")
                                    .then(
                                            CommandManager.argument("town", StringArgumentType.greedyString())
                                                    .executes(this::addToEmpire)
                                    )
                                )
                                .then(CommandManager.literal("remove").then(
                                        CommandManager.argument("town", StringArgumentType.greedyString())
                                                .executes(this::removeFromEmpire)
                                    )
                                )
                                .then(CommandManager.literal("list").requires(Requires.isEmperor())
                                        .executes(
                                                this::empireList
                                        )
                                )
                )
            .build();
    }

    private int empireList(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();

        User user = User.get(source.getPlayer().getName().getString());
        List<UUID> invites = Empire.getEmpireByFaction(user.getFaction().getID()).invites;
        int count = invites.size();

        new Message("У твоей империи ")
                .add(new Message(String.valueOf(count)).format(Formatting.YELLOW))
                .add(" исходящих предложений стать твоим вассалом.")
                .add("\n\nА именно:")
                .send(source.getPlayer(), false);

        if (count == 0) return 1;

        StringBuilder players = new StringBuilder();
        int length = invites.size();
        for(int i = 0; i < length; i++){
            players.append(Faction.get(invites.get(i)).getID());
            if(i == length - 1) players.append(",");
        }

        new Message(players.toString()).format(Formatting.ITALIC).send(source.getPlayer(), false);
        return 1;
    }

    private int removeFromEmpire(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String targetName = StringArgumentType.getString(context, "town");
        ServerPlayerEntity source = context.getSource().getPlayer();

        Faction targetFaction = Faction.getByName(targetName);

        if(targetFaction == null){
            new Message("Ошибка: города с таким названием не существует!").format(Formatting.RED).fail().send(source, false);
            return 0;
        }
        User user = User.get(source.getName().getString());
        Faction sourceFaction = user.getFaction();
        Empire sourceEmpire = Empire.getEmpireByFaction(sourceFaction.getID());

        sourceEmpire.invites.remove(targetFaction.getID());

        new Message("Предложение стать вассалом у города " +targetFaction.getName() + " отозвано").send(source, false);
        return 1;
    }

    private int addToEmpire(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {

        String townName = StringArgumentType.getString(context, "town");

        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        Faction sourceFaction = User.get(player.getName().getString()).getFaction();
        if(sourceFaction == null){
            new Message("Ошибка: чего??? Твоего города не существует!").format(Formatting.RED).send(player, false);
            return 0;
        }
        Empire sourceEmpire = Empire.getEmpireByFaction(sourceFaction.getID());
        if(sourceEmpire == null){
            new Message("Ошибка: у тебя нету империи!").format(Formatting.RED).send(player, false);
            return 0;
        }
        if(!sourceEmpire.isMetropoly(sourceFaction.getID())){
            new Message("Ошибка: ты вздумал приглашать города от имени вассала? Что за наглость?").format(Formatting.RED).send(player, false);
            return 0;
        }

        Faction targetFaction = Faction.getByName(townName);
        if(targetFaction == null){
            new Message("Ошибка: города с таким названием не существует!").format(Formatting.RED).send(player, false);
            return 0;
        }
        Empire targetEmpire = Empire.getEmpireByFaction(targetFaction.getID());
        if(targetEmpire != null){
            new Message("Ошибка: невозможно отобрать вассала у другой империи!").format(Formatting.RED).send(player, false);
            return 0;
        }



        if (sourceEmpire.isInvited(targetFaction.getID())) {
            new Message("Ошибка: Ты уже предложил присоединиться " + targetFaction.getName() + " к твоей империи!").format(Formatting.RED).send(player, false);
            return 0;
        }

        if (sourceEmpire.getVassalsIDList().contains(targetFaction.getID())) {
            new Message(targetFaction.getName() + " уже твой вассал!").format(Formatting.RED).send(player, false);
            return 0;
        }
        User targetOwnerUser = targetFaction.getUsers().stream().filter(u -> u.getRank() == User.Rank.OWNER).findFirst().orElse(null);

        if(targetOwnerUser == null){
            new Message("Ошибка: В городе "+ targetFaction.getName() +" не найден ни один король (Что??? Репортни это админам!)").format(Formatting.RED).send(player, false);
            return 0;
        }
        ServerPlayerEntity targetOwner = source.getServer().getPlayerManager().getPlayer(targetOwnerUser.getName());

        if(targetOwner == null){
            new Message("Ошибка: ServerPlayerManager не нашёл этого игрока!!! (Что??? Срочно репортни это админам!)").format(Formatting.RED).send(player, false);
            return 0;
        }
        sourceEmpire.invites.add(targetFaction.getID());


        new Message("Городу " +targetFaction.getName() + " была предложена добровольная вассализация").sendToEmpireChat(sourceEmpire);
        new Message("Империя " + sourceEmpire.name + " предлагает тебе добровольно стать её вассалом").format(Formatting.YELLOW)
                .hover("Нажми, чтобы присоединиться").click("/factions join-empire " + sourceEmpire.name)
                .send(targetOwner, false);
        return 1;

    }
}