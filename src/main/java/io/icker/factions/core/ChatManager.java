package io.icker.factions.core;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.Empire;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.util.Message;
import net.luckperms.api.model.data.DataType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.valkyrienskies.core.impl.shadow.H;

import java.util.UUID;

public class ChatManager {
    public static void handleMessage(ServerPlayerEntity sender, String message) {
        net.luckperms.api.model.user.User lp_user = FactionsMod.LUCK_API.getUserManager().getUser(sender.getName().getString());
        String nickname = sender.getName().getString();
        User member = User.get(nickname);

        if (member.chat == User.ChatMode.GLOBAL) {
            if (member.isInFaction()) {
                ChatManager.inFactionGlobal(sender, member.getFaction(), message, lp_user);
            } else {
                ChatManager.global(sender, message, lp_user);
            }
        } else {
            if (member.isInFaction()) {
                ChatManager.faction(sender, member.getFaction(), message, lp_user);
            } else {
                ChatManager.fail(sender);
            }
        }
    }

    private static void global(ServerPlayerEntity sender, String message, net.luckperms.api.model.user.User user) {
        String prefix = "";
        prefix = user.getCachedData().getMetaData().getPrefix();
        if(prefix != null)
            prefix = prefix.isEmpty() ? "[player]." : "§6["+prefix+"§6]§r.";
        User fUser = User.get(sender.getName().getString());
        Faction faction = fUser.getFaction();
        String royalPrefix = "";
        String royalMeta = "";
        Text text = null;
        if(faction != null){
            User.Rank rank = fUser.rank;
            Empire empire = Empire.getEmpireByFaction(faction.getID());
            if(empire != null && empire.isMetropoly(faction.getID())){
                switch (rank){
                    case OWNER -> {royalPrefix = "§6♔"; royalMeta = "§6Его Величество - §5" + fUser.getName() + "§6, Император Царства §5" + empire.name;}
                    case LEADER -> {royalPrefix = "§6♕"; royalMeta = "§6Регент Царства " + empire.name;}
                    case COMMANDER -> {royalPrefix = "§6♖"; royalMeta = "§6Маршал §5" + fUser.getName() +"§6, служащий Империи §5" + empire.name;}
                    case SHERIFF -> {royalPrefix = "§6♗"; royalMeta = "§6Глава Царской охранки на службе у всея Империи §5" + empire.name;}
                    default -> {}
                }
            } else if (empire != null){
                switch (rank){
                    case OWNER -> {royalPrefix = "§5♔"; royalMeta = "§6Царь княжества §5" + faction.getName() + "§6, подданный города §5" + Faction.get(empire.metropolyID).getName() + "§6, слуга Империи §5" + empire.name;}
                    case LEADER -> {royalPrefix = "§5♕"; royalMeta = "§6Барон княжества §5" + faction.getName() + "§6, служащий Царству " + empire.name;}
                    case COMMANDER -> {royalPrefix = "§5♖"; royalMeta = "§6Генерал княжества §5" + faction.getName() + "§6, на службе у Царства §5" + empire.name;}
                    case SHERIFF -> {royalPrefix = "§5♗"; royalMeta = "§6Офицер местной охранки города §5" + faction.getName() + "§6, на службе у §5" + empire.name;}
                    default -> {}
                }
            } else {
                switch (rank){
                    case OWNER -> {royalPrefix = "§8♚"; royalMeta = "§7Глава вольного города §9" + faction.getName();}
                    case LEADER -> {royalPrefix = "§8♛"; royalMeta = "§7Вице-президент вольного города §9" + faction.getName();}
                    case COMMANDER -> {royalPrefix = "§8♖"; royalMeta = "§7Полковник вольного города §9" + faction.getName();}
                    case SHERIFF -> {royalPrefix = "§8♗"; royalMeta = "§7Офицер милиции вольного города §9" + faction.getName();}
                    default -> {}
                }
            }
        }
        FactionsMod.LOGGER.info("["+ prefix + sender.getName().asString() + " -> All] " + message);
        new Message(prefix).add(new Message("[" + royalPrefix + "]").hover(royalMeta)).add(String.format("[%s] ", sender.getName().getString()) + message).format(Formatting.WHITE).sendToGlobalChat();
    }

    private static void inFactionGlobal(ServerPlayerEntity sender, Faction faction, String message, net.luckperms.api.model.user.User user) {

        String prefix = "";
        if(user != null) prefix = user.getCachedData().getMetaData().getPrefix();

        if(prefix == null) prefix = "";
        prefix = prefix.isEmpty() ? "[player]." : "§6["+prefix+"§6]§r.";

        FactionsMod.LOGGER.info("[" + prefix  + faction.getName() + " " + sender.getName().asString() + " -> All] " + message);
        new Message(prefix + String.format("[%s] ", sender.getName().getString()))
            .add(new Message(faction.getName()).format(Formatting.BOLD, faction.getColor()))
            .filler("»")
            .add(new Message(message).format(Formatting.GRAY))
            .sendToGlobalChat();
    }

    public static void fail(ServerPlayerEntity sender) {
        new Message("You must be in a faction to use faction chat")
                .hover("Click to join global chat")
                .click("/f chat global")
                .fail()
                .send(sender, false);
    }

    private static void faction(ServerPlayerEntity sender, Faction faction, String message, net.luckperms.api.model.user.User user) {
        String prefix = "";
        if(user != null) prefix = user.getCachedData().getMetaData().getPrefix();

        if(prefix == null) prefix = "";
        prefix = prefix.isEmpty() ? "[player]." : "§6["+prefix+"§6]§r.";
        FactionsMod.LOGGER.info("[" + prefix + faction.getName() + " " + sender.getName().asString() + " -> " + faction.getName() + "] " + message);
        new Message(prefix + String.format("[%s] ", sender.getName().getString()))
            .add(new Message("F").format(Formatting.BOLD, faction.getColor()))
            .filler("»")
            .add(new Message(message).format(Formatting.GRAY))
            .sendToFactionChat(faction);
    }
}
