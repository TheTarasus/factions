package io.icker.factions.core;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.Empire;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.util.Message;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

public class ChatManager {
    public static void handleMessage(ServerPlayerEntity sender, String message) {
        net.luckperms.api.model.user.User lp_user = FactionsMod.LUCK_API.getUserManager().getUser(sender.getName().getString());
        String nickname = sender.getName().getString();
        User member = User.get(nickname);

        if (member.chat == User.ChatMode.GLOBAL) {
            if (member.isInFaction()) {
                ChatManager.inFactionGlobal(sender, member.getFaction(), message, lp_user);
                return;
            } else {
                ChatManager.global(sender, message, lp_user);
                return;
            }
        } else {
            if (member.isInFaction()) {
                ChatManager.faction(sender, member.getFaction(), message, lp_user);
                return;
            } else {
                ChatManager.fail(sender);
                return;
            }
        }
    }

    private static void global(ServerPlayerEntity sender, String message, net.luckperms.api.model.user.User user) {
        String prefix = "";
        prefix = user.getCachedData().getMetaData().getPrefix();
            prefix = prefix == null || prefix.isEmpty() || prefix.isBlank() ? "[player]." : "§6["+prefix+"§6]§r.";
        FactionsMod.LOGGER.info("["+ prefix + sender.getName().asString() + " -> All] " + message);
        new Message(prefix + sender.getName().getString())
                .filler("»")
                .add(new Message(message).format(Formatting.GRAY))
                .sendToGlobalChat();
    }

    private static void inFactionGlobal(ServerPlayerEntity sender, Faction faction, String message, net.luckperms.api.model.user.User user) {

        String prefix = "";
        if(user != null) prefix = user.getCachedData().getMetaData().getPrefix();

        if(prefix == null) prefix = "";
        prefix = prefix.isEmpty() ? "[player]." : "§6["+prefix+"§6]§r.";


        User fUser = User.get(sender.getName().getString());
        String royalPrefix = "";
        String royalMeta = "";
        User.Rank rank = fUser.getRank();
        Empire empire = Empire.getEmpireByFaction(faction.getID());
        if(faction.isAdmin()){
            royalPrefix = "§c⚒";
            royalMeta = "§4Олигарх, имеющий резиденцию в " + faction.getColor() + faction.getName() + "\n\n §4§n(Этот город имеет админскую неприкосновенность)";
        }
        else if(empire != null && empire.isMetropoly(faction.getID())){
            switch (rank){
                case OWNER -> {royalPrefix = "§6♔"; royalMeta = "§6§lЕго Величество - §5§l" + fUser.getName() + "§6§l, Император Царства §5§l" + empire.name;}
                case LEADER -> {royalPrefix = "§6♕"; royalMeta = "§6§lРегент Царства §5§l" + empire.name;}
                case COMMANDER -> {royalPrefix = "§6♖"; royalMeta = "§6§lМаршал §5§l" + fUser.getName() +"§6§l, служащий Империи §5§l" + empire.name;}
                case SHERIFF -> {royalPrefix = "§6♗"; royalMeta = "§6§lОфицер царской охранки на службе у Империи §5§l" + empire.name;}
                case MEMBER -> {royalPrefix = "§6♟"; royalMeta = "§6§lГорожанин столичный, приличный! \n- Да только к остальному Царству §5§l" + empire.name + "§6§l безразличный...";}
                default -> {}
            }
        } else if (empire != null){
            switch (rank){
                case OWNER -> {royalPrefix = "§5♔"; royalMeta = "§6Глава княжества §5" + faction.getName() + "§6, подданный города §5" + Faction.get(empire.getMetropolyID()).getName() + "§6, слуга Империи §5" + empire.name;}
                case LEADER -> {royalPrefix = "§5♕"; royalMeta = "§6Барон княжества §5" + faction.getName() + "§6, служащий Царству §5" + empire.name;}
                case COMMANDER -> {royalPrefix = "§5♖"; royalMeta = "§6Генерал княжества §5" + faction.getName() + "§6, на службе у Царства §5" + empire.name;}
                case SHERIFF -> {royalPrefix = "§5♗"; royalMeta = "§6Сотрудник местной охранки города §5" + faction.getName() + "§6, на службе у §5" + empire.name;}
                case MEMBER -> {royalPrefix = "§5♟"; royalMeta = "§6Крестьянин простой, да крепостной - \nна Царство §5" + empire.name + "§6 бурлачит , труженник малой...";}
                default -> {}
            }
        } else {
            switch (rank){
                case OWNER -> {royalPrefix = "§8♚"; royalMeta = "§7Глава вольного города §9" + faction.getName();}
                case LEADER -> {royalPrefix = "§8♛"; royalMeta = "§7Вице-президент вольного города §9" + faction.getName();}
                case COMMANDER -> {royalPrefix = "§8♖"; royalMeta = "§7Полковник вольного города §9" + faction.getName();}
                case SHERIFF -> {royalPrefix = "§8♗"; royalMeta = "§7Офицер милиции вольного города §9" + faction.getName();}
                case MEMBER -> {royalPrefix = "§8♟"; royalMeta = "§7А это, подивись-ка - свободный, вольный, праздный гражданин!\n Но только смысла от того, ведь в государстве §9" + faction.getName() + "§7 нету даже лишней парочки штанин!";}
                default -> {}
            }
        }

        FactionsMod.LOGGER.info("[" + prefix  + faction.getName() + " " + sender.getName().asString() + " -> All] " + message);
        new Message(prefix + String.format("[%s] ", sender.getName().getString()))
            .add(new Message("§8§l{"+royalPrefix+"§8§l}§r").hover(royalMeta))
            .add(new Message(faction.getName()).format(Formatting.BOLD, faction.getColor()))
            .filler("»")
            .add(new Message(message).format(Formatting.WHITE))
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
            .add(new Message(message).format(Formatting.BLUE))
            .sendToFactionChat(faction);
    }
}
