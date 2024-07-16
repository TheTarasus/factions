package io.icker.factions.api.persistents;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.FactionEvents;
import io.icker.factions.database.Database;
import io.icker.factions.database.Field;
import io.icker.factions.database.Name;
import io.icker.factions.util.Message;
import net.minecraft.util.Formatting;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Name("Empire")
public class Empire implements StateTypeable {

    private static final HashMap<UUID, Empire> STORE = Database.load(Empire.class, Empire::getID);

    @Field("ID")
    public UUID id;

    @Field("Name")
    public String name;

    @Field("metropolyID")
    private UUID metropolyID;

    @Field("empireTax")
    public float empireTax;

    @Field("vassalsIDList")
    private ArrayList<UUID> vassalsIDList;
    @Field("metropolyBannerLocation")
    private String metropolyBannerLocation;

    @Field("vassalBannerLocation")
    private String vassalBannerLocation;

    @Field("Invites")
    public ArrayList<UUID> invites = new ArrayList<>();

    public WarGoal goal;

    @Override
    public String getName(){
        return this.name;
    }

    public Empire(UUID id, UUID metropolyID, ArrayList<UUID> vassalsIDList, String name, String metropolyBannerLocation, String vassalBannerLocation, Formatting color){
        this.id = id;
        this.setMetropolyID(metropolyID);
        this.setVassalsIDList(vassalsIDList == null ? new ArrayList<>() : vassalsIDList);
        this.name = name;
        this.color = color.getName();
        this.metropolyBannerLocation = metropolyBannerLocation == null ? FactionsMod.ANCAP_METROPOLY_BANNER.toString() : metropolyBannerLocation;
        this.vassalBannerLocation = vassalBannerLocation == null ? FactionsMod.ANCAP_VASSAL_BANNER.toString() : metropolyBannerLocation;
        this.goal = WarGoal.activeWarWithState(this.getCapitalState());
        updateAllEmpire();
    }

    public Empire() {}

    public void addVassal(UUID id){
        this.vassalsIDList.add(id);
        vassalsIDList.remove(this.metropolyID);
    }
    public void removeVassal(UUID id){
        this.vassalsIDList.remove(id);
        vassalsIDList.remove(this.metropolyID);
    }

    private void setMetropolyBannerLocation(File file){
        this.metropolyBannerLocation = file.toString();
    }

    private void setVassalBannerLocation(File file){
        this.vassalBannerLocation = file.toString();
    }

    public void setEmpireFlags(File metropoly, File vassal){
        setMetropolyBannerLocation(metropoly);
        setVassalBannerLocation(vassal);
        FactionEvents.EMPIRE_BANNER_UPDATE.invoker().onEmpireBannerUpdate(this);
        updateAllEmpire();
    }
    public boolean isInvited(UUID townID) {
        return invites.stream().anyMatch(invite -> invite.equals(townID));
    }
    public void updateAllEmpire(){
        for(UUID vassalID : this.getVassalsIDList()) {
            Faction faction = Faction.get(vassalID);
            if(faction == null) {this.removeVassal(vassalID); continue;}
            FactionEvents.MODIFY.invoker().onModify(faction);
            faction.setActiveWargoal(this.goal);
            faction.setEmpireBannerLocation(new File(getVassalFlagPath()));
        }

        Faction faction = Faction.get(this.getMetropolyID());
        faction.setActiveWargoal(this.goal);
        faction.setEmpireBannerLocation(new File(getMetropolyFlagPath()));
        FactionEvents.MODIFY.invoker().onModify(Faction.get(this.getMetropolyID()));
    }

    public String getMetropolyFlagPath(){
        return this.metropolyBannerLocation;
    }

    public String getVassalFlagPath(){
        return this.vassalBannerLocation;
    }


    @Field("color")
    private String color;

    public Formatting getColor(){
        return Formatting.byName(color);
    }
    public void setColor(Formatting color) {
        this.color = color.getName();
        updateAllEmpire();
    }
    @Override
    public UUID getID(){
        return id;
    }

    public static boolean isVassalOfFaction(UUID suzerain, UUID vassal){
        return STORE.values().stream().anyMatch(empire -> empire.getMetropolyID().equals(suzerain) && empire.vassalsIDList.contains(vassal));
    }

    public boolean isFactionIn(UUID id){
        return getMetropolyID().equals(id) || getVassalsIDList().contains(id);
    }

    public boolean isVassal(UUID id){
        vassalsIDList.remove(this.metropolyID);
        return getVassalsIDList().contains(id);
    }

    public boolean isMetropoly(UUID id){
        vassalsIDList.remove(this.metropolyID);
        return getMetropolyID().equals(id);
    }

    public static Empire getEmpire(UUID id){
        return STORE.get(id);
    }
    public static Empire getEmpireByFaction(UUID id) { return STORE.values().stream().filter(empire -> empire.isFactionIn(id)).findFirst().orElse(null);}

    public static Empire getEmpireByName(String name){
        return STORE.values().stream().filter(empire -> empire.name.equals(name)).findFirst().orElse(null);
    }

    public static void add(Empire empire){
        STORE.put(empire.id, empire);
    }

    public static boolean isAnyVassal(UUID id){
        return STORE.values().stream().anyMatch(empire -> empire.isVassal(id));
    }

    public static boolean isAnyMetropoly(UUID id){
        return STORE.values().stream().anyMatch(empire -> empire.isMetropoly(id));
    }


    public void remove(){
        List<UUID> members = getVassalsIDList();
        WarGoal goal = this.getActiveWargoal();
        if(goal != null) {
            this.goal.remove();
            return;
        };
        this.setWarGoal(null);
        members.add(getMetropolyID());
        for (UUID id : members){
            Faction faction = Faction.get(id);
            if(faction == null) continue;
            faction.resetDotations();
            FactionEvents.MODIFY.invoker().onModify(faction);
        }
        EmperorLocalization.get(this.getID()).remove();
        new Message("§4Империя §6" + this.name + "§4 распалась.").sendToGlobalChat();
        STORE.remove(this.id);
    }



    public static void save() {
        Database.save(Empire.class, STORE.values().stream().toList());
    }

    public void releaseAllPrisonersBeforeWar(StateTypeable targetState){
        List<UUID> ids = new ArrayList<>();
        ids.addAll(this.getVassalsIDList());
        ids.add(this.getMetropolyID());
        for(UUID id : ids){
            Faction faction = Faction.get(id);
            if(faction == null) {this.removeVassal(id); continue;}
            if(faction.jail == null) continue;
            faction.jail.releaseAllPrisonersBeforeWar(targetState);
        }
        Empire targetEmpire = Empire.getEmpireByFaction(targetState.getID());
        String targetName = targetEmpire == null ? targetState.getCapitalState().getName() : targetEmpire.name;
        }

    @Override
    public WarGoal.StateType getStateType() {
        return WarGoal.StateType.EMPIRE;
    }

    @Override
    public Faction getCapitalState() {
        return Faction.get(this.getMetropolyID());
    }

    @Override
    public List<User> findAllUsersOfEmpire() {
        List<User> users = new ArrayList<>();
        List<UUID> members = new ArrayList<>();
        members.addAll(this.getVassalsIDList());
        members.add(this.getMetropolyID());
        for (UUID id : members){
            Faction faction = Faction.get(id);
            if(faction == null) continue;
            users.addAll(faction.getUsers());
        }
        return users;
    }

    @Override
    public WarGoal getActiveWargoal() {
        return goal;
    }


    public void setWarGoal(@Nullable WarGoal goal){
        this.goal = goal;
        updateAllEmpire();
    }

    public ArrayList<UUID> getVassalsIDList() {
        vassalsIDList.remove(this.metropolyID);
        return vassalsIDList;
    }

    public void setVassalsIDList(ArrayList<UUID> vassalsIDList) {
        vassalsIDList.remove(this.metropolyID);
        this.vassalsIDList = vassalsIDList;
        FactionEvents.UPDATE_ALL_EMPIRE.invoker().onUpdate(this);
    }

    public UUID getMetropolyID() {
        return metropolyID;
    }

    public void setMetropolyID(UUID metropolyID) {
        this.metropolyID = metropolyID;
    }
}
