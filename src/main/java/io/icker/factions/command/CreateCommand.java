package io.icker.factions.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.FactionEvents;
import io.icker.factions.api.persistents.*;
import io.icker.factions.util.Command;
import io.icker.factions.util.Message;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

import java.util.UUID;

public class CreateCommand implements Command {
    private int run(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");

        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (!name.matches("^[a-zA-Z0-9А-я_Ёё]{2,32}$")){
            new Message("Название города может содержать от 2 до 32 символов: Латиница(A-z), Русская кирриллица(А-я), арабские цифры(0-9)").fail().send(player, false);
            return 0;
        }

        if (Faction.getByName(name) != null) {
            new Message("Невозможно создать город: Название занято!").fail().send(player, false);
            return 0;
        }
        Faction faction = new Faction(name, "(Нет описания)", "(Нет статуса)", Formatting.WHITE, false, FactionsMod.CONFIG.BASE_POWER + FactionsMod.CONFIG.MEMBER_POWER, false, 0, new Jail(), FactionsMod.ANCOM_BANNER.toString(), FactionsMod.ANCOM_BANNER.toString(), 0, false);
        Faction.add(faction);
        User.get(player.getName().getString()).joinFaction(faction.getID(), User.Rank.OWNER);

        source.getServer().getPlayerManager().sendCommandTree(player);
        new Message("Город успешно создан!").send(player, false);
        return 1;
    }



    public LiteralCommandNode<ServerCommandSource> getNode() {
        return CommandManager
            .literal("create").requires(Requires.hasPerms("factions.create", 0))
            .then(
                    CommandManager.literal("town").then(
                            CommandManager.argument("name", StringArgumentType.greedyString()).requires(Requires.isFactionless()).executes(this::run)
                    )
            )
                .then(CommandManager.literal("empire").then(
                        CommandManager.argument("name", StringArgumentType.greedyString()).requires(Requires.multiple(Requires.isEmpireless(), Requires.isLeader()))
                                .executes(this::createEmpire)
                )
            ).build();
    }

    private int createEmpire(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");

        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (!name.matches("^[a-zA-Z0-9А-я_Ёё]{2,32}$")){
            new Message("Название города может содержать от 2 до 32 символов: Латиница(A-z), Русская кирриллица(А-я), арабские цифры(0-9)").fail().send(player, false);
            return 0;
        }

        if (Empire.getEmpireByName(name) != null) {
            new Message("Невозможно создать империю: Название занято!").fail().send(player, false);
            return 0;
        }
        User user = User.get(player.getName().getString());
        Faction faction = user.getFaction();
        int power = faction.getPower();
        if(power < FactionsMod.CONFIG.POWER_TO_CREATE_EMPIRE) {
            new Message("Невозможно создать империю: Необходимо "+ FactionsMod.CONFIG.POWER_TO_CREATE_EMPIRE +" ₽!").fail().send(player, false);
            return 0;
        }
        Empire empire = new Empire(UUID.randomUUID(), faction.getID(), null, name, FactionsMod.ANCAP_METROPOLY_BANNER.toString(), FactionsMod.ANCAP_VASSAL_BANNER.toString(), faction.getColor());
        Empire.add(empire);
        EmperorLocalization emperorLocalization = new EmperorLocalization(empire.getID(), null, null);
        EmperorLocalization.add(emperorLocalization);
        FactionEvents.UPDATE_ALL_EMPIRE.invoker().onUpdate(empire);

        new Message("§4§lCourt§aOf§6§lRasus §r§6встречает расцвет новой империи: на карте появилось государство \"§5"+ name +"\"").sendToGlobalChat();
        return 1;
    }
}