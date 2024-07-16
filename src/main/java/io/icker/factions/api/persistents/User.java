package io.icker.factions.api.persistents;

import io.icker.factions.api.events.FactionEvents;
import io.icker.factions.database.Database;
import io.icker.factions.database.Field;
import io.icker.factions.database.Name;
import net.minecraft.network.MessageType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Name("User")
public class User {
    private static final HashMap<String, User> STORE = Database.load(User.class, User::getName);

    public Rank getRank() {
        if(rank == null) return Rank.TOWNLESS;
        return rank;
    }

    public void setRank(Rank rank) {
        this.rank = rank;
    }

    public enum ChatMode {
        FOCUS,
        FACTION,
        GLOBAL
    }

    public enum Rank {
        OWNER,
        LEADER,
        COMMANDER,
        SHERIFF,
        MEMBER,
        TOWNLESS
    }

    public enum SoundMode {
        NONE,
        WARNINGS,
        FACTION,
        ALL
    }

    @Field("Name")
    private String name;
    @Field("Prisoner")
    private Prisoner prisoner;

    @Field("FactionID")
    private UUID factionID;

    @Field("Rank")
    private Rank rank;

    @Field("Radar")
    public boolean radar = true;

    @Field("Chat")
    public ChatMode chat = ChatMode.GLOBAL;

    @Field("Sounds")
    public SoundMode sounds = SoundMode.ALL;

    public boolean autoclaim = false;
    public boolean bypass = false;

    public User(String name) {
        this.name = name;
    }

    public User() { ; }

    public String getKey() {
        return name;
    }

    public static User get(String name) {
        if (!STORE.containsKey(name)) {
            User.add(new User(name));
        }
        return STORE.get(name);
    }

    public static List<User> getByFaction(UUID factionID) {
        return STORE.values()
            .stream()
            .filter(m -> m.isInFaction() && m.factionID.equals(factionID))
            .toList();
    }

    public static void add(User user) {
        STORE.put(user.name, user);
    }

    public String getName() {
        return name;
    }

    public boolean isInFaction() {
        return factionID != null;
    }

    private String getEnumName(Enum<?> value) {
        return Arrays
            .stream(value.name().split("_"))
            .map(word -> word.isEmpty() ? word :
                Character.toTitleCase(word.charAt(0)) +
                word.substring(1).toLowerCase())
            .collect(Collectors.joining(" "));
    }

    public String getRankName() {
        Faction faction = this.getFaction();
        if(faction == null)
            return getLocalizedRankName();
        Empire empire = Empire.getEmpireByFaction(faction.getID());
        if(empire == null) return getLocalizedRankName();
        switch (getRank()) {
            case OWNER:
                return "Его Величество - " + this.getName() + ", Император " + empire.name;
            case LEADER:
                return "Царский Регент";
            case COMMANDER:
                return "Фельдмаршал империи";
            case SHERIFF:
                return "Руководитель Царской охранки";
            default:
                return "Обычный столичный житель";
        }
    }

    public String getLocalizedRankName(){
        switch (getRank()){
            case OWNER -> {
                return "Владелец города";
            }
            case LEADER -> {
                return "Мэр города";
            }
            case COMMANDER -> {
                return "Городской полководец";
            }
            case SHERIFF -> {
                return "Шериф";
            }
            default -> {
                return  "Простолюдин";
            }
        }
    }

    public String getChatName() {
        return getEnumName(chat);
    }

    public String getSoundName() {
        return getEnumName(sounds);
    }

    public Faction getFaction() {
        return Faction.get(factionID);
    }

    public void joinFaction(UUID factionID, Rank rank) {
        this.factionID = factionID;
        this.setRank(rank);
        FactionEvents.MEMBER_JOIN.invoker().onMemberJoin(Faction.get(factionID), this);
    }

    public void leaveFaction() {
        UUID oldFactionID = factionID;
        factionID = null;
        setRank(null);
        FactionEvents.MEMBER_LEAVE.invoker().onMemberLeave(Faction.get(oldFactionID), this);
    }

    public static void save() {
        Database.save(User.class, STORE.values().stream().toList());
    }

    public Prisoner getPrisoner(MinecraftServer server){
        if(this.prisoner == null) return null;
        if(!this.prisoner.checkIfInJail(server)) {
            server.getPlayerManager().broadcast(new LiteralText("§e[JAILBREAK!]§c " + this.name + "§e has escaped from §c" + this.prisoner.jailFaction + " prison!"), MessageType.CHAT, Util.NIL_UUID);
            Faction jailFaction = Faction.get(this.prisoner.jailFaction);
            jailFaction.jail.getPrisoners().removeIf(p -> p.prisoner.equals(this.name));
            this.prisoner = null;
            return null;
        }
        return this.prisoner;
    }

    public boolean setImprisoned(ServerWorld world, Faction jailFaction, int hours){
        if(this.prisoner != null) return false;
        Jail jail = jailFaction.jail;
        if(jail == null) return false;
        BlockPos prisonRespawn = new BlockPos(jail.x, jail.y, jail.z);
        Prisoner detained = new Prisoner(name, jailFaction.getID(), new Date().getTime() + (1000L * 3600L * hours), prisonRespawn.getX(), prisonRespawn.getY(), prisonRespawn.getZ(), world.getRegistryKey().getValue().toString());
        if(!detained.isValid(world.getServer())) return false;
        jailFaction.jail.getPrisoners().add(detained);
        this.prisoner = detained;
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(this.name);
        if(player == null) return false;
        player.teleport(world, jail.x, jail.y, jail.z, jail.yaw, jail.pitch);
        player.setSpawnPoint(world.getRegistryKey(), prisonRespawn, jail.yaw, true, false);

        return true;
    }

    public void resetImprisoned(ServerWorld world){
        this.prisoner = null;
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(this.prisoner.prisoner);
        if(player == null) return;
        player.setSpawnPoint(world.getRegistryKey(), world.getSpawnPos(), 0, false, false);
    }

    public void resetImprisoned(){
        this.prisoner = null;
    }
}