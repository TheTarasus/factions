package io.icker.factions.core;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.util.Message;
import net.luckperms.api.model.data.DataType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

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
        if(prefix == null) prefix = "";
        prefix = user.getCachedData().getMetaData().getPrefix();
        if(prefix != null)
            prefix = prefix.isEmpty() ? "[player]." : "§6["+prefix+"§6]§r.";
        FactionsMod.LOGGER.info("["+ prefix + sender.getName().asString() + " -> All] " + message);
        new Message(prefix + String.format("[%s] ", sender.getName().getString()) + message).format(Formatting.WHITE).sendToGlobalChat();
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
