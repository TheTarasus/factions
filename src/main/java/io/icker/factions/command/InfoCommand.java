package io.icker.factions.command;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.*;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.UserCache;
import net.minecraft.util.Util;

public class InfoCommand implements Command {
    private int self(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        User user = User.get(player.getName().getString());
        if (!user.isInFaction()) {
            new Message("Эту команду можно выполнить только находясь в городе!").fail().send(player, false);
            return 0;
        }

        return info(player, user.getFaction());
    }

    private int selfEmpire(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        User user = User.get(player.getName().getString());
        if (!user.isInFaction()) {
            new Message("Эту команду можно выполнить только находясь в составе империи!").fail().send(player, false);
            return 0;
        }
        Faction faction = user.getFaction();
        Empire empire = Empire.getEmpireByFaction(faction.getID());
        if(empire == null){
            new Message("Эту команду можно выполнить только находясь в составе империи!").fail().send(player, false);
            return 0;
        }



        return info(player, user.getFaction());
    }

    private int any(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String factionName = StringArgumentType.getString(context, "faction");

        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        Faction faction = Faction.getByName(factionName);
        if (faction == null) {
            new Message("Этот город не существует!").fail().send(player, false);
            return 0;
        }

        return info(player, faction);
    }

    public static int info(ServerPlayerEntity player, Faction faction) {
        List<User> users = faction.getUsers();

        String userText = Formatting.WHITE.toString() + users.size() + Formatting.GRAY + 
            (FactionsMod.CONFIG.MAX_FACTION_SIZE != -1 ? "/" + FactionsMod.CONFIG.MAX_FACTION_SIZE : (" Количество участников:"));

        String leaderText = Formatting.WHITE +
                String.valueOf(users.stream().filter(u -> u.getRank() == User.Rank.LEADER).count()) + Formatting.GRAY + " Лидеров";

        String commanderText = Formatting.WHITE +
                String.valueOf(users.stream().filter(u -> u.getRank() == User.Rank.COMMANDER).count()) + Formatting.GRAY + " Коммандиров";

        String sheriffText = Formatting.WHITE +
                String.valueOf(users.stream().filter(u -> u.getRank() == User.Rank.SHERIFF).count()) + Formatting.GRAY + " Шерифов";

        UserCache cache = player.getServer().getUserCache();
        String usersList = users.stream()
            .map(user -> cache.findByName(user.getName()).orElse(new GameProfile(Util.NIL_UUID, "{Uncached Player}")).getName())
            .collect(Collectors.joining(", "));
        
        String mutualAllies = faction.getMutualAllies().stream()
            .map(rel -> Faction.get(rel.target))
            .map(fac -> fac.getColor() + fac.getName())
            .collect(Collectors.joining(Formatting.GRAY + ", "));

        User user = User.get(player.getName().getString());
        boolean isAtWar = WarGoal.activeWarWithState(faction) != null;
        boolean isLegit = !isAtWar && User.Rank.OWNER.equals(user.getRank());

        Message enemiesDeclareWar = new Message("Враги этого государства (Можно объявить войну): ");

        String enemiesWith = Formatting.GRAY + faction.getEnemiesWith().stream()
            .map(rel -> Faction.get(rel.target))
            .map(fac -> fac.getColor() + fac.getName())
            .collect(Collectors.joining(Formatting.GRAY + ", "));

        if(isLegit){
            for(Relationship targetRel : faction.getEnemiesWith()){
                Faction target = Faction.get(targetRel.target);
                String facName = target.getColor() + target.getName();
                Message addTo = new Message(facName +", ").hover("§cНажми, чтобы выбрать цель войны").click("/factions declare goals\"" + target.getName() + "\"");
                enemiesDeclareWar = enemiesDeclareWar.add(addTo);
            }
        }

        String enemiesOf = Formatting.GRAY + faction.getEnemiesOf().stream()
            .map(rel -> Faction.get(rel.target))
            .map(fac -> fac.getColor() + fac.getName())
            .collect(Collectors.joining(Formatting.GRAY + ", "));

        int tax = faction.getClaims().size() * FactionsMod.CONFIG.DAILY_TAX_PER_CHUNK;
        int maxPower = FactionsMod.CONFIG.MAX_POWER;

        new Message(Formatting.GRAY + faction.getDescription())
            .prependFaction(faction)
            .send(player, false);
        new Message(userText)
                .add(leaderText)
                .filler("·")
                .add(commanderText)
                .filler("·")
                .add(sheriffText)
                .filler("·")
                .hover(usersList)
            .send(player, false);
        new Message("Казна")
            .filler("·")
            .add(Formatting.GREEN.toString() + faction.getPower() + "₽" + slash() + Formatting.RED.toString() + tax + "₽" + Formatting.GREEN.toString() + slash() + maxPower + "₽")
            .hover("Имеется / Налог / Максимум")
            .send(player, false);
        if (mutualAllies.length() > 0)
            new Message("Союзники: ")
                .add(mutualAllies)
                .send(player, false);
        if (enemiesWith.length() > 0) {
            if (isLegit) enemiesDeclareWar.send(player, false);
            else new Message("Враги этого государства: ")
                    .add(enemiesWith)
                    .send(player, false);
        }
        if (enemiesOf.length() > 0)
            new Message("Является врагом для других государств: ")
                .add(enemiesOf)
                .send(player, false);
        Empire sourceEmpire = Empire.getEmpireByFaction(faction.getID());
        if(sourceEmpire != null){
            String message = sourceEmpire.isMetropoly(sourceEmpire.id) ? "Является вассалом государства " + Faction.get(sourceEmpire.getMetropolyID()).getName() + " и членом империи " + sourceEmpire.name : "Является столицей империи " + sourceEmpire.name;
            new Message(message).format(Formatting.GOLD).send(player, false);
        }

        UUID userFaction = user.isInFaction() ? user.getFaction().getID() : null;
        if (faction.getID().equals(userFaction))
            new Message("Ваше звание: ")
                .add(Formatting.GRAY + user.getRankName())
                .send(player, false);

        return 1;
    }

    private static String slash() {
        return Formatting.GRAY + " / " + Formatting.GREEN;
    }

    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager
                .literal("info")
                .requires(Requires.hasPerms("factions.info", 0))
                .then(CommandManager.literal("faction")
                        .executes(this::self)
                        .then(
                                CommandManager.argument("faction", StringArgumentType.string())
                                        .requires(Requires.hasPerms("factions.info.other", 0))
                                        .suggests(Suggests.allFactions())
                                        .executes(this::any)
                        )
                )
                .then(CommandManager.literal("empire")
                        .executes(this::selfEmpire))
                .build();
    }
}