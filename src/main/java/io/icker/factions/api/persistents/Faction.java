package io.icker.factions.api.persistents;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.FactionEvents;
import io.icker.factions.api.persistents.wargoals.GenericWarGoal;
import io.icker.factions.core.FactionsManager;
import io.icker.factions.database.Database;
import io.icker.factions.database.Field;
import io.icker.factions.database.Name;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.network.MessageType;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;

@Name("Faction")
public class Faction implements StateTypeable{
    private static final HashMap<UUID, Faction> STORE = Database.load(Faction.class, Faction::getID);

    @Field("ID")
    private UUID id;
    @Field("Name")
    private String name;

    @Field("Description")
    private String description;

    @Field("MOTD")
    private String motd;

    @Field("Color")
    private String color;

    @Field("Open")
    private boolean open;

    @Field("Power")
    private int power;

    @Field("Admin")
    private boolean admin;

    @Field("Home")
    private Home home;

    @Field("Jail")
    public Jail jail;

    @Field("Invites")
    private ArrayList<String> invites = new ArrayList<String>();

    @Field("Relationships")
    private ArrayList<Relationship> relationships = new ArrayList<Relationship>();

    @Field("relationsLastUpdate")
    public long relationsLastUpdate;


    @Field("regionBannerLocation")
    private String regionBannerLocation = FactionsMod.ANCOM_BANNER.toString();

    public String getRegionBannerLocation(){
        return this.regionBannerLocation;
    }

    @Field("empireBannerLocation")
    private String empireBannerLocation = FactionsMod.ANCOM_BANNER.toString();

    @Field("capitulationPoints")
    public int capitulationPoints = 0;

    @Field("empireTax")
    private float empireTax = 0;

    @Field("isCapitulated")
    private boolean isCapitulated = false;

    @Field("dotatedRegionUUID")
    private UUID dotatedRegion = Util.NIL_UUID;

    @Field("dotatedPower")
    private int dotatedPower = -1;
    public boolean getCapitulated(){
        return this.isCapitulated;
    }

    public void setCapitulated(boolean isCapitulated){
        this.isCapitulated = isCapitulated;
        if(isCapitulated) warGoalListener.destroy(this);
    }
    public int capitulationLimit;

    private WarGoal goal;
    public GenericWarGoal warGoalListener;

    public void setEmpireBannerLocation(File file){
        this.regionBannerLocation = file.toString();
        FactionEvents.BANNER_UPDATE.invoker().onBannerUpdate(this);
    }


    public void setRegionBannerLocation(File file){
        this.regionBannerLocation = file.toString();
        FactionEvents.BANNER_UPDATE.invoker().onBannerUpdate(this);
    }
    public String getEmpireBannerLocation(){
        return this.empireBannerLocation;
    }

    public Faction(UUID id, String name, String description, String motd, Formatting color, boolean open, int power, boolean admin, long relationsLastUpdate, Jail jail, String empireBannerLocation, String regionBannerLocation, int capitulationPoints, boolean isCapitulated, UUID dotatedRegion, int dotatedPower) {
        this.id = id;
        this.name = name;
        this.motd = motd;
        this.description = description;
        this.color = color.getName();
        this.open = open;
        this.admin = admin;
        this.power = admin ? Integer.MAX_VALUE : power;
        Safe safe = new Safe(name);
        Safe.add(safe);
        this.relationsLastUpdate = relationsLastUpdate;
        this.jail = jail;
        this.capitulationPoints = capitulationPoints;

        this.empireBannerLocation = empireBannerLocation == null ? FactionsMod.ANCOM_BANNER.toString() : empireBannerLocation;
        this.regionBannerLocation = regionBannerLocation == null ? FactionsMod.ANCOM_BANNER.toString() : regionBannerLocation;


        this.updateCapitulationLimit();

        this.isCapitulated = this.capitulationPoints > capitulationLimit;
        this.goal = WarGoal.activeWarWithState(this.getCapitalState());
        this.dotatedRegion = dotatedRegion;
        this.dotatedPower = dotatedPower;
        this.removeNulledRelationships();
    }

    private void updateCapitulationLimit() {
        System.out.println("Capitulation Limit for town " + this.name + " is: " + this.capitulationLimit);
        this.capitulationLimit = this.getUsers().size() * 5;
        this.capitulationLimit *= this.getStateType() == WarGoal.StateType.EMPIRE ? 1 + (this.findAllUsersOfEmpire().size() / 25) : 1;
        this.capitulationLimit = this.isAdmin() ? Integer.MAX_VALUE : this.capitulationLimit;
    }

    public void payWarTaxes(){
        WarGoal goal = this.getActiveWargoal();
        if(goal == null) return;

        float cost = goal.isAgressor(this) ? goal.cost : goal.reverseCost;
        if(Float.isInfinite(cost) || Float.isNaN(cost)) cost = 0.5f;
        switch (this.getStateType()){
            case EMPIRE -> cost *= 5;
            case VASSAL -> cost *= 2;
            case FREE_STATE -> cost *=3;
        }
        cost += 1;

        this.adjustPower((int)-cost);
    }

    public void adjustCapitulationPoints(int points){
        this.updateCapitulationLimit();
        if(this.getActiveWargoal() == null){
            this.isCapitulated = false;
            this.capitulationPoints = 0;
            return;
        }
        this.capitulationPoints += points;
        if(this.capitulationPoints > this.capitulationLimit) {
            this.isCapitulated = true;
            this.getActiveWargoal().processingWarGoal.destroy(this);
        }
    }

    public Faction() { ; }

    public String getKey() {
        return id.toString();
    }

    public static Faction get(UUID id) {
        return STORE.get(id);
    }

    public static Faction getByName(String name) {
        return STORE.values()
            .stream()
            .filter(f -> f.name.equals(name))
            .findFirst()
            .orElse(null);
    }

    public static void add(Faction faction) {
        STORE.put(faction.id, faction);
    }

    public static Collection<Faction> all() {
        return STORE.values();
    }

    public static List<Faction> allBut(UUID id) {
        return STORE.values()
            .stream()
            .filter(f -> f.id != id)
            .toList();
    }

    public UUID getID() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    public Formatting getColor() {
        return Formatting.byName(color);
    }

    public String getDescription() {
        return description;
    }

    public String getMOTD() {
        return motd;
    }

    public int getPower() {
        return power;
    }

    public SimpleInventory getSafe() {
        return Safe.getSafe(name).inventory;
    }

    public void setSafe(SimpleInventory safe) {
        Safe.getSafe(name).inventory = safe;
    }

    public boolean isOpen() {
        return open;
    }

    public void setName(String name) {
        Safe.getSafe(name).factionName = name;
        this.name = name;
        FactionEvents.MODIFY.invoker().onModify(this);
    }

    public boolean isAdmin(){
        return admin;
    }

    public void setDescription(String description) {
        this.description = description;
        FactionEvents.MODIFY.invoker().onModify(this);
    }

    public void setMOTD(String motd) {
        this.motd = motd;
        FactionEvents.MODIFY.invoker().onModify(this);
    }

    public void setColor(Formatting color) {
        this.color = color.getName();
        FactionEvents.MODIFY.invoker().onModify(this);
    }

    public void setOpen(boolean open) {
        this.open = open;
        FactionEvents.MODIFY.invoker().onModify(this);
    }

    public void setAdmin(boolean admin){
        this.admin = admin;
        FactionEvents.MODIFY.invoker().onModify(this);
    }

    public int adjustPower(int adjustment) {
        int maxPower = FactionsMod.CONFIG.MAX_POWER;
        int newPower = Math.min(power + adjustment, maxPower);
        int oldPower = this.power;

        if (newPower == oldPower) return 0;
        if(admin) return 0;
        power = newPower;
        FactionEvents.POWER_CHANGE.invoker().onPowerChange(this, oldPower);
        if(power < 0) {
            this.remove();
        }
        return newPower - oldPower;
    }

    public void taxForLand(){
        if(!this.getStateType().equals(WarGoal.StateType.VASSAL)) {modifiedTaxForLand(1);return;}
        if(this.getCapitalState().getDotations(this) < 0) {modifiedTaxForLand(FactionsMod.CONFIG.VASSAL_TAX_PER_CHUNK_MULTIPLIER);return;}
        modifiedTaxForLand(0.1f);
    }

    public void modifiedTaxForLand(float modifier){

    }

    public int getDotations(Faction faction){
        if(!this.getStateType().equals(WarGoal.StateType.EMPIRE)) return -1;
        return this.dotatedRegion.equals(faction.getID()) ? this.dotatedPower : -1;
    }

    public void resetDotations(){
        this.dotatedRegion = Util.NIL_UUID;
        this.dotatedPower = -1;
    }

    public void taxToEmpire(){
        boolean isNotTaxor = this.getStateType() != WarGoal.StateType.VASSAL;
        if(isNotTaxor) return;
        int dotations = this.getCapitalState().getDotations(this);
        if(dotations >= 0) {
            this.getCapitalState().adjustPower(-dotations);
            this.adjustPower(dotations);
            return;
        }
        float percent = this.getCapitalState().getEmpireTax();
        int adjusted = (int) (this.power * percent);
        int incame = this.adjustPower(-adjusted);
        this.getCapitalState().adjustPower(-incame);
    }

    public List<User> getUsers() {
        return User.getByFaction(id);
    }

    public List<Claim> getClaims() {
        return Claim.getByFaction(id);
    }

    public void removeAllClaims() {
        Claim.getByFaction(id)
            .forEach(Claim::remove);
        FactionEvents.REMOVE_ALL_CLAIMS.invoker().onRemoveAllClaims(this);
    }

    public void addClaim(int x, int z, String level) {
        Claim.add(new Claim(x, z, level, id, false, null));
    }

    public void addOutpost(int x, int z, String level, Claim.Outpost outpost) {
        Claim.add(new Claim(x, z, level, id, false, outpost));
    }

    public boolean isInvited(String playerName) {
        return getInvites().stream().anyMatch(invite -> invite.equals(playerName));
    }

    public Home getHome() {
        return home;
    }

    public Claim.Outpost getHome(int index) {
        Claim origin = this.getClaims().stream().filter(Claim::isOutpost)
                .filter(claim ->
                        claim.outpost.index == index).findFirst().get();
        Claim.Outpost pos = null;
        pos = origin.outpost;
        pos = pos == null ? new Claim.Outpost((int) home.x >> 4, (int) home.z >> 4, new BlockPos(home.x, home.y, home.z), 0, home.level) : pos;
        return pos;
    }

    public int homesLength(){
        return (int) this.getClaims().stream().filter(Claim::isOutpost).count()+1;
    }

    public void removeNulledRelationships(){
        relationships = new ArrayList<>(relationships
            .stream().filter(
                relationship -> {
                if(relationship == null) return false;
                return Faction.get(relationship.source) != null && relationship.target != null;
                }
            ).toList());
    }

    public void setHome(Home home) {
        this.home = home;
        FactionEvents.SET_HOME.invoker().onSetHome(this, home);
    }

    public Relationship getRelationship(UUID target) {
        if(Empire.isAnyVassal(target) && !(Empire.isVassalOfFaction(this.id, target) || Empire.isVassalOfFaction(target, this.id))) return new Relationship(this.id, target, 0);
        return relationships.stream().filter(rel -> rel.target.equals(target)).findFirst().orElse(new Relationship(this.id, target, 0));
    }

    public Relationship getReverse(Relationship rel) {
        return Faction.get(rel.target).getRelationship(id);
    }

    public boolean isMutualAllies(UUID target) {
        Relationship rel = getRelationship(target);
        return rel.getStatus() == Relationship.Status.ALLY && getReverse(rel).getStatus() == Relationship.Status.ALLY;
    }

    public List<Relationship> getMutualAllies() {
        return relationships.stream().filter(rel -> isMutualAllies(rel.target)).toList();
    }

    public List<Relationship> getEnemiesWith() {
        return relationships.stream().filter(rel -> rel.getStatus() == Relationship.Status.ENEMY).toList();
    }

    public List<Relationship> getEnemiesOf() {
        return relationships.stream().filter(rel -> getReverse(rel).getStatus() == Relationship.Status.ENEMY).toList();
    }

    public void removeRelationship(UUID target) {
        relationships = new ArrayList<>(relationships.stream().filter(rel -> !rel.target.equals(target)).toList());
    }

    public void setRelationship(Relationship relationship) {
        System.out.println("Relationship points are: " + relationship.points);
        if(relationship.target.equals(this.getID())){
            removeRelationship(relationship.target);
            System.out.println("Relationship target is equal to source!");
            return;
        }
        if (relationship.getStatus() != Relationship.Status.NEUTRAL) {
            relationships.add(relationship);
            System.out.println("Relationship status is: " + relationship.getStatus());
            return;
        }

        System.out.println("Relationship is neutral!");
    }

    public void remove() {
        if(this.getStateType() == WarGoal.StateType.EMPIRE) {
            Empire empire = Empire.getEmpireByFaction(this.id);
            if(empire != null) empire.remove();
        }
        if(this.getStateType() == WarGoal.StateType.VASSAL){
            Empire empire = Empire.getEmpireByFaction(this.getID());
            if(empire != null) empire.removeVassal(this.getID());
        }
        FactionsManager.playerManager.getServer().getPlayerManager().broadcast(
                new LiteralText("§eГород §9" + this.name + " §eрасформирован!"), MessageType.CHAT, Util.NIL_UUID
        );
        for (User user : getUsers()) {
            user.leaveFaction();
        }
        removeAllClaims();
        Safe.getSafe(name).remove();

        Empire empire = Empire.getEmpire(id);
        if(empire != null) {
            if(empire.getMetropolyID() == this.id) {
                empire.remove();
            }
            empire.removeVassal(this.id);
        }
        STORE.remove(id);
        FactionEvents.DISBAND.invoker().onDisband(this);

    }

    public static void save() {
        Database.save(Faction.class, STORE.values().stream().toList());
    }

    @Override
    public WarGoal.StateType getStateType() {
        Empire empire = Empire.getEmpireByFaction(this.id);
        if(empire == null) return WarGoal.StateType.FREE_STATE;
        return empire.isMetropoly(this.id) ? WarGoal.StateType.EMPIRE : WarGoal.StateType.VASSAL;
    }

    @Override
    public Faction getCapitalState() {
        Faction result;
        WarGoal.StateType type = this.getStateType();
        switch (type) {
            case VASSAL:
                Empire empire = Empire.getEmpireByFaction(this.getID());
                return Faction.get(empire.getMetropolyID());
            default:
                return this;
        }
    }

    public void triggerPrisonerReleaseBeforeWar(StateTypeable faction){
        WarGoal.StateType type = this.getStateType();
        if(this.jail == null) return;
        switch (type){
            case VASSAL, EMPIRE -> {
                Empire empire = Empire.getEmpireByFaction(this.getID());
                empire.releaseAllPrisonersBeforeWar(faction);
            }
            default -> this.jail.releaseAllPrisonersBeforeWar(faction);
        }
    }

    @Override
    public List<User> findAllUsersOfEmpire() {
        WarGoal.StateType type = this.getStateType();
        switch (type){
            case VASSAL, EMPIRE -> {
                Empire empire = Empire.getEmpireByFaction(this.getID());
                List<UUID> members = empire.getVassalsIDList();
                members.add(empire.getMetropolyID());
                List<User> users = new ArrayList<>();
                for(UUID id : members){
                    Faction faction = Faction.get(id);
                    if(faction == null) continue;
                    users.addAll(faction.getUsers());
                }
                return users;
            }
            default -> {return this.getUsers();}
        }
    }

    @Override
    public WarGoal getActiveWargoal() {
        return this.goal;
    }

    public void setActiveWargoal(@Nullable WarGoal goal){
        if(goal == null){
            this.warGoalListener = null;
            return;
        }
        this.goal = goal;
        this.warGoalListener = GenericWarGoal.getWargoal(goal.goalType);
    }

    public float getEmpireTax() {
        if(this.getStateType() != WarGoal.StateType.FREE_STATE) return 0;
        return empireTax;
    }

    public void setEmpireTax(float empireTax) {
        if(this.getStateType() != WarGoal.StateType.FREE_STATE) {this.empireTax = 0; return;}
        this.empireTax = Math.max(FactionsMod.CONFIG.MIN_VASSAL_TAX, Math.min(FactionsMod.CONFIG.MAX_VASSAL_TAX, empireTax));
    }

    public ArrayList<String> getInvites() {
        return invites;
    }

    public void addInvite(String invite){
        if(!this.invites.contains(invite)) this.invites.add(invite);
    }

    public void removeInvite(String invite){
        this.invites.remove(invite);
    }

    public void setInvites(ArrayList<String> invites) {
        this.invites = invites;
    }
}