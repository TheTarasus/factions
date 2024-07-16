package io.icker.factions.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.*;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;

public class DeclareCommand implements Command {
    private int improve(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return updateRelationship(context, 1);
    }
    private int insult(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return updateRelationship(context, -1);
    }

    private int updateRelationship(CommandContext<ServerCommandSource> context, int points) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "faction");
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        Faction targetFaction = Faction.getByName(name);

        if (targetFaction == null) {
            new Message("Ошибка: данного клана не существует!").fail().send(player, false);
            return 0;
        }

        Faction sourceFaction = User.get(player.getName().getString()).getFaction();

        if (sourceFaction.equals(targetFaction)) {
            new Message("Ошибка: ты пытаешься изменить отношения с собственным кланом!").fail().send(player, false);
            return 0;
        }

        if(targetFaction.isAdmin()){
            new Message("Ошибка: админские кланы не подвергнуты дипломатии!").fail().send(player, false);
            return 0;
        }

        if(Empire.isAnyVassal(sourceFaction.getID()) && !sourceFaction.getCapitalState().getID().equals(targetFaction.getID())){
            new Message("Ошибка: вассалы не могут изменять отношения ни с кем, кроме как с сюзереном!").fail().send(player, false);
            return 0;
        }

        Date nextUpdate = new Date(sourceFaction.relationsLastUpdate + (FactionsMod.CONFIG.HOURS_BEFORE_NEXT_FABRICATE * 1000 * 3600));

        boolean isAfter = nextUpdate.before(new Date());
        if(!isAfter) {
            new Message("Невозможно изменять отношения до этой даты: " + nextUpdate.toString()).fail().send(player, false);
            return 0;
        }

        int warTaxes = FactionsMod.CONFIG.FABRICATE_TAXES;

        sourceFaction.adjustPower(-warTaxes);

        Relationship rel = sourceFaction.getRelationship(targetFaction.getID());
        Relationship rev = targetFaction.getRelationship(sourceFaction.getID());
        rel = new Relationship(sourceFaction.getID(), targetFaction.getID(), rel.points + points);
        sourceFaction.setRelationship(rel);

        sourceFaction.relationsLastUpdate = new Date().getTime();


        String msgStatus = rel.getStatus().name().toLowerCase(Locale.ROOT);

        if (rel.getStatus() == rev.getStatus()) {
            new Message("You are now mutually ").add(msgStatus).add(" with " + targetFaction.getName()).send(sourceFaction);
            new Message("You are now mutually ").add(msgStatus).add(" with " + sourceFaction.getName()).send(targetFaction);
            return 1;
        }

        new Message("Вы объявили " + targetFaction.getName() + " как ").add(msgStatus).add("; Ваши очки дипломатии теперь равны: " + rel.points).send(sourceFaction);

        updateWargoal(sourceFaction, targetFaction, rel, rev, player);

        return 1;
    }


    public static void updateWargoal(Faction sourceFaction, Faction targetFaction, Relationship fromRelation, Relationship toRelation, ServerPlayerEntity player) {
        if (!fromRelation.getStatus().equals(Relationship.Status.ENEMY)) return;

        long dateofwar = new Date(new Date().getTime() + (1000 * 3600 * 24 * 3)).getTime();

        sourceFaction.relationsLastUpdate = dateofwar;
        targetFaction.relationsLastUpdate = dateofwar;
        toRelation = new Relationship(targetFaction.getID(), sourceFaction.getID(), (-FactionsMod.CONFIG.DAYS_TO_FABRICATE) - 1);
        targetFaction.setRelationship(toRelation);
        new Message("§eВы теперь можете объявить войну государству §5" + targetFaction.getName()).send(player, false);
        new Message("§eДоступные цели войны (кликни мышкой): ").send(player, false);

        List<WarGoal.Type> types = WarGoal.allAvailableTypes(sourceFaction, targetFaction);
        Message finalMessage = new Message("");
        for (WarGoal.Type type : types) {
            Message addTo = new Message(type.localizedName + ", ");
            addTo.hover(type.lore).click("/factions declare war \"" + targetFaction.getName() + "\" " + type.name);
            finalMessage.add(addTo);
        }
        finalMessage.send(player, false);
    }

    @Override
    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager
            .literal("declare")
            .requires(Requires.isLeader())
            .then(
                CommandManager.literal("improve")
                .requires(Requires.hasPerms("factions.declare.ally", 0))
                .then(
                    CommandManager.argument("faction", StringArgumentType.string())
                    .suggests(Suggests.allFactions(false))
                    .executes(this::improve)
                )
            )
            .then(
                CommandManager.literal("insult")
                .requires(Requires.hasPerms("factions.declare.enemy", 0))
                .then(
                    CommandManager.argument("faction", StringArgumentType.string())
                    .suggests(Suggests.allFactions(false))
                    .executes(this::insult)
                )
            ).then(
                    CommandManager.literal("war")
                            .requires(Requires.hasPerms("factions.declare.war", 0))
                            .then(CommandManager.argument("faction", StringArgumentType.string())
                                    .suggests(Suggests.allWithNegativeRelations())
                                    .then(CommandManager.argument("wargoal", StringArgumentType.string())
                                            .suggests(Suggests.allWargoals())
                                            .executes(this::declareWar))
                            )
                )
                .then(
                        CommandManager.literal("goals")
                                .requires(Requires.hasPerms("factions.declare.war", 0))
                                .then(CommandManager.argument("faction", StringArgumentType.string())
                                .suggests(Suggests.allWithNegativeRelations())
                                .executes(this::checkWargoals))
                )
            .build();
    }

    private int checkWargoals(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "faction");
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        User user = User.get(player.getName().getString());
        Faction sourceFaction = user.getFaction();
        Faction targetFaction = Faction.getByName(name);
        new Message("§eДоступные цели войны (кликни мышкой): ").send(player, false);

        List<WarGoal.Type> types = WarGoal.allAvailableTypes(sourceFaction, targetFaction);
        Message finalMessage = new Message("");
        for (WarGoal.Type type : types) {
            Message addTo = new Message(type.localizedName + ", ").hover(type.lore).click("/factions declare war \"" + targetFaction.getName() + "\" " + type.name);
            finalMessage.add(addTo);
        }
        finalMessage.send(player, false);
        return 1;
    }

    private int declareWar(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String targetName = StringArgumentType.getString(context, "faction");
        String wargoal = StringArgumentType.getString(context, "wargoal");
        ServerPlayerEntity player = context.getSource().getPlayer();

        User user = User.get(player.getName().getString());
        Faction targetFaction = Faction.getByName(targetName);
        Faction sourceFaction = user.getFaction();
        if(sourceFaction == null){
            new Message("§cОшибка: Чего? Хотя бы город создай, воевать он удумал!").send(player, false);
            return 0;
        }
        if(sourceFaction.getClaims().isEmpty()){
            new Message("§cОшибка: Чего? Хотя бы чанки запривать, воевать он удумал!").send(player, false);
            return 0;
        }
        if(targetFaction == null) {
            new Message("§cОшибка: неверно указано государство противника!").send(player, false);
            return 0;
        }

        if(sourceFaction.getClaims().isEmpty()){
            new Message("§cОшибка: У тебя нет земель! Где-ж ты драться собираешься?").send(player, false);
            return 0;
        }


        if(targetFaction.getClaims().isEmpty()){
            new Message("§cОшибка: У противника нет земель! Ты воевать с пустым местом удумал?").send(player, false);
            return 0;
        }

        if(sourceFaction.getID().equals(targetFaction.getID())){
            new Message("§cОшибка: Самая трудная борьба - это борьба с самим собой...").send(player, false);
            return 0;
        }
        boolean isInvalid = !Arrays.stream(WarGoal.Type.values()).anyMatch(type -> type.name.equals(wargoal));
        if(isInvalid){
            new Message("§cОшибка: нету такой цели войны! Её просто не существует вообще!").send(player, false);
            return 0;
        }

        WarGoal.Type type = Arrays.stream(WarGoal.Type.values()).filter(type1 -> type1.name.equals(wargoal)).findFirst().orElse(null);

        if(type == null){
            new Message("§Ошибка: нету такой цели войны! Её просто не существует вообще!").send(player, false);
            return 0;
        }

        boolean isInActualConflict = targetFaction.getActiveWargoal() != null || sourceFaction.getActiveWargoal() != null;

        if(isInActualConflict){
            new Message("§cОшибка: К сожалению, война на два фронта не предусмотрена... Пойми мои чувства, брат - я заёбался этот мод писать, уже и так много времени на него потратил.").send(player, false);
            return 0;
        }

        boolean isValid = WarGoal.compareAs(sourceFaction, targetFaction, type);

        if(!isValid) {
            new Message("§cОшибка: Для этого противника не подходит данная цель войны.").send(player, false);
            return 0;
        }

        boolean isSelfLiberation = type.equals(WarGoal.Type.LIBERATE);
        boolean isSelfLiberationCheck = sourceFaction.getCapitalState().getID().equals(targetFaction.getID());

        if(isSelfLiberation && isSelfLiberationCheck){
            WarGoal warGoal = new WarGoal(sourceFaction, targetFaction, type);
            WarGoal.add(warGoal);
            sourceFaction.setActiveWargoal(warGoal);
            targetFaction.setActiveWargoal(warGoal);
            Empire.getEmpireByFaction(targetFaction.getID()).setWarGoal(warGoal);
            new Message("§aВойна объявлена успешно!").send(player, false);
            return 1;
        }

        boolean isNegativeRelations = sourceFaction.getRelationship(targetFaction.getID()).getStatus().equals(Relationship.Status.ENEMY);

        if(!isNegativeRelations){
            Relationship.Status relStatus = sourceFaction.getRelationship(targetFaction.getID()).getStatus();
            System.out.println("REL STATUS: " + relStatus);
            System.out.println("EQUAL TO Status.ENEMY?: " + relStatus.equals(Relationship.Status.ENEMY));
            new Message("§cОшибка: нельзя объявить войну другому государству, не ухудшив отношение к нему до предела!").send(player, false);
            return 0;
        }


        sourceFaction.triggerPrisonerReleaseBeforeWar(targetFaction);
        targetFaction.triggerPrisonerReleaseBeforeWar(sourceFaction);

        WarGoal goal = new WarGoal(sourceFaction, targetFaction, type);
        WarGoal.add(goal);

        Empire sourceEmpire = Empire.getEmpireByFaction(sourceFaction.getID());
        Empire targetEmpire = Empire.getEmpireByFaction(targetFaction.getID());

        sourceFaction.setActiveWargoal(goal);
        targetFaction.setActiveWargoal(goal);

        if(sourceEmpire != null) sourceEmpire.setWarGoal(goal);
        if(targetEmpire != null) targetEmpire.setWarGoal(goal);
        new Message("§aВойна объявлена успешно!").send(player, false);
        return 1;
    }

}
