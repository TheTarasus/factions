package io.icker.factions.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.Relationship;
import io.icker.factions.api.persistents.User;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

import java.util.Date;

public class AdminCommand implements Command {
    private int bypass(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();

        User user = User.get(player.getName().getString());
        boolean bypass = !user.bypass;
        user.bypass = bypass;

        new Message("Successfully toggled claim bypass")
                .filler("·")
                .add(
                    new Message(user.bypass ? "ON" : "OFF")
                        .format(user.bypass ? Formatting.GREEN : Formatting.RED)
                )
                .send(player, false);

        return 1;
    }

    private int reload(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        FactionsMod.dynmap.reloadAll();
        new Message("Reloaded dynmap marker").send(context.getSource().getPlayer(), false);
        return 1;
    }

    private int disband(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        Faction target = Faction.getByName(StringArgumentType.getString(context, "faction"));

        new Message("An admin disbanded the faction").send(target);
        target.remove();

        new Message("Faction has been removed").send(player, false);

        PlayerManager manager = source.getServer().getPlayerManager();
        for (ServerPlayerEntity p : manager.getPlayerList()) {
            manager.sendCommandTree(p);
        }
        return 1;
    }

    private int power(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();

        Faction target = Faction.getByName(StringArgumentType.getString(context, "faction"));
        int power = IntegerArgumentType.getInteger(context, "power");

        int adjusted = target.adjustPower(power);
        if (adjusted != 0) {
            if (power > 0) {
                new Message("Admin %s added %d power", player.getName().getString(), adjusted).send(target);
                new Message("Added %d power", adjusted).send(player, false);
            } else {
                new Message("Admin %s removed %d power", player.getName().getString(), adjusted).send(target);
                new Message("Removed %d power", adjusted).send(player, false);
            }
        } else {
            new Message("Could not change power").fail().send(player, false);
        }

        return 1;
    }

    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager
            .literal("admin")
            .then(
                CommandManager.literal("bypass")
                .requires(Requires.hasPerms("factions.admin.bypass", FactionsMod.CONFIG.REQUIRED_BYPASS_LEVEL))
                .executes(this::bypass)
            )
            .then(
                CommandManager.literal("reload")
                .requires(Requires.multiple(Requires.hasPerms("factions.admin.reload", FactionsMod.CONFIG.REQUIRED_BYPASS_LEVEL), source -> FactionsMod.dynmap != null))
                .executes(this::reload)
            )
            .then(
                CommandManager.literal("disband")
                .requires(Requires.hasPerms("factions.admin.disband", FactionsMod.CONFIG.REQUIRED_BYPASS_LEVEL))
                .then(
                    CommandManager.argument("faction", StringArgumentType.string())
                    .suggests(Suggests.allFactions())
                    .executes(this::disband)
                )
            )
            .then(
                CommandManager.literal("power")
                .requires(Requires.hasPerms("factions.admin.power", FactionsMod.CONFIG.REQUIRED_BYPASS_LEVEL))
                .then(
                    CommandManager.argument("power", IntegerArgumentType.integer())
                    .then(
                        CommandManager.argument("faction", StringArgumentType.string())
                        .suggests(Suggests.allFactions())
                        .executes(this::power)
                    )
                )
            ).then(
                    CommandManager.literal("war")
                            .requires(Requires.hasPerms("factions.admin.war", 4))
                            .then(CommandManager.argument("sourceFaction", StringArgumentType.string())
                                .suggests(Suggests.allFactions())
                                .then(CommandManager.argument("targetFaction", StringArgumentType.string())
                                        .suggests(Suggests.allFactions())
                                        .executes(this::war))
                        )
                )
            .build();
    }

    private int war(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {

        Faction source = Faction.getByName(StringArgumentType.getString(context, "sourceFaction"));
        Faction target = Faction.getByName(StringArgumentType.getString(context, "targetFaction"));
        ServerPlayerEntity ctxPlayer = context.getSource().getPlayer();

        if(source == null){
            new Message("§cОшибка: Чего? Хотя бы город создай, воевать он удумал!").send(ctxPlayer, false);
            return 0;
        }
        if(source.getClaims().isEmpty()){
            new Message("§cОшибка: Чего? Хотя бы чанки запривать, воевать он удумал!").send(ctxPlayer, false);
            return 0;
        }
        if(target == null) {
            new Message("§cОшибка: неверно указано государство противника!").send(ctxPlayer, false);
            return 0;
        }

        if(source.getClaims().isEmpty()){
            new Message("§cОшибка: У тебя нет земель! Где-ж ты драться собираешься?").send(ctxPlayer, false);
            return 0;
        }

        if(source.getID().equals(target.getID())) {
            new Message("§cОшибка: Труднее всего бороться с самим собой, ведь силы-то равны").send(ctxPlayer, false);
            return 0;
        }

        if(source.isAdmin() || target.isAdmin()) {
            new Message("§cНевозможно объявить войну на админский клан, или будучи админским кланом!").send(ctxPlayer, false);
            return 0;
        }
        System.out.println("DAYS_TO_FABRICATE = " + FactionsMod.CONFIG.DAYS_TO_FABRICATE);

        Relationship sourceRel = new Relationship(source.getID(), target.getID(), -FactionsMod.CONFIG.DAYS_TO_FABRICATE -1);
        Relationship targetRel = new Relationship(target.getID(), source.getID(), -FactionsMod.CONFIG.DAYS_TO_FABRICATE -1);
        User user = source.getUsers().stream().filter(u -> u.getRank().equals(User.Rank.OWNER)).findFirst().orElse(null);
        if(user == null) {
            new Message("§cОшибка! Глава нападающего клана - не найден! (user == null)").send(ctxPlayer, false);
            return 0;
        }
        ServerPlayerEntity player = context.getSource().getServer().getPlayerManager().getPlayer(user.getName());
        if(player == null){
            new Message("§сОшибка! Глава нападающего клана - оффлайн (user != null && player == null)").send(ctxPlayer, false);
            return 0;
        }
        source.setRelationship(sourceRel);
        target.setRelationship(targetRel);

        DeclareCommand.updateWargoal(source, target, sourceRel, targetRel, player);
        long dateofwar = new Date(new Date().getTime() + (1000 * 3600 * 24 * 3)).getTime();
        source.relationsLastUpdate = dateofwar;
        target.relationsLastUpdate = dateofwar;
        new Message("§8§o§lСкоро грянет буря:").sendToGlobalChat();
        new Message("§eОтношения §r" +source.getColor() + source.getName() + "§e и §r" + target.getColor() + target.getName() + "§e - накалились до предела!").sendToGlobalChat();

        return 1;
    }
}
