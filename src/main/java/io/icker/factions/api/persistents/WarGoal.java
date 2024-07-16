package io.icker.factions.api.persistents;

import io.icker.factions.api.persistents.wargoals.*;
import io.icker.factions.database.Database;
import io.icker.factions.database.Field;
import io.icker.factions.database.Name;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.concurrent.Executor;

@Name("WarGoal")
public class WarGoal {


    private static final HashMap<UUID, WarGoal> STORE = Database.load(WarGoal.class, WarGoal::getID);

    public Collection<WarGoal> all(){
        return STORE.values();
    }

    public WarGoal(){;} // WarGoal.init<>()

    public enum Type {

        //Common war goals:

        //Liberates some country, or itself:
        LIBERATE("liberate", "§9[ОСВОБОЖДЕНИЕ]", "Освобождает любого вассала\n(в том числе, самого себя)\nот империи.",false, true, false, false, true, true, Float.POSITIVE_INFINITY, 0.15f, Float.POSITIVE_INFINITY, false),

        //Just a dull annex. Doesn't work against empires:
        ANNEX("annex", "§a[АННЕКСИЯ]", "Аннексирует вольное государство.", true, false, true, true, false, false, 0.5f, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, false),

        //Annihilation of target land, erasing the state from Earth. Only works against free states:
        ANNIHILATE("annihilate", "§4[УНИЧТОЖЕНИЕ]", "Стирает с лица Земли вольное государство.", true, false, true, true, false, false, 1.0f, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, false),


        //War goals that can declare only empires:

        //Vassalizes the state without changing the leader:
        SUBJUGATE("subjugate", "§d[ПОДЧИНЕНИЕ]", "Подчиняет свободное государство,\n оставляя его правителя нетронутым", false, false, true, true, false, false, 0.3f, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, false),

        //Vassalizes the state and changes the government:
        PUPPETING("puppeting", "§5[ВАССАЛИЗАЦИЯ]", "Подчиняет свободное государство\nи устанавливает наместника",false, false, true, true, false, false, 0.4f, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, true),

        //Splits and balkanizes the target empire:
        BALKANIZE("balkanize", "§4[РАСЧЛЕНЕНИЕ]", "Расчленяет и разваливает империю", true, false, true, false, false, true, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, 1.5f, false),

        //It's like "SUBJUGATE", but against empires. Puppets every surrendered state:
        CONQUEST("conquest", "§5§n[ЗАВОЕВАНИЕ]", "Вассализирует все поверженные города\nвражеской империи, пока столицу этой империи\nне захватят, и она не распадётся или сдастся \n(Это будет долгая война)", false, false, true, false, false, true, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, 0.8f, true);


        public final String name, localizedName, lore;
        public final boolean asFreeState, asVassal, asEmpire, againstFreeState, againstVassal, againstEmpire, willVassalize;
        public final float againstFreeStateMultiplier, againstVassalMultiplier, againstEmpireMultiplier;

        Type(String name, String localizedName, String lore, boolean asFreeState, boolean asVassal, boolean asEmpire, boolean againstFreeState, boolean againstVassal, boolean againstEmpire, float againstFreeStateMultiplier, float againstVassalMultiplier, float againstEmpireMultiplier, boolean willVassalize) {
            this.name = name;
            this.localizedName = localizedName;
            this.lore = lore;
            this.asFreeState = asFreeState;
            this.asVassal = asVassal;
            this.asEmpire = asEmpire;
            this.againstFreeState = againstFreeState;
            this.againstVassal = againstVassal;
            this.againstEmpire = againstEmpire;
            this.willVassalize = willVassalize;
            this.againstFreeStateMultiplier = againstFreeStateMultiplier;
            this.againstVassalMultiplier = againstVassalMultiplier;
            this.againstEmpireMultiplier = againstEmpireMultiplier;
        }
    }

    public static List<Type> allAvailableTypes(StateTypeable sourceType, StateTypeable targetType){
        boolean asFreeState = false, asVassal = false, asEmpire = false, againstFreeState = false, againstVassal = false, againstEmpire = false;
        switch (sourceType.getStateType()){
            case FREE_STATE -> asFreeState = true;
            case VASSAL -> asVassal = true;
            case EMPIRE -> asEmpire = true;
        }
        System.out.println("The source State("+ sourceType.getCapitalState().getName() +") is a: " + sourceType.getStateType());
        switch (targetType.getStateType()){
            case FREE_STATE -> againstFreeState = true;
            case VASSAL -> againstVassal = true;
            case EMPIRE -> againstEmpire = true;
        }
        System.out.println("The target State("+ targetType.getCapitalState().getName() +") is a: " + targetType.getStateType());

        List<Type> types = Arrays.asList(Type.values());
        List<Type> readyTypes = new ArrayList<>();
        for(Type type : types){
            boolean match = ((type.asFreeState && asFreeState) || (type.asVassal && asVassal) || (type.asEmpire && asEmpire)) &&
            ((type.againstFreeState && againstFreeState) || (type.againstVassal && againstVassal) || (type.againstEmpire && againstEmpire));
            if(match) readyTypes.add(type);
        }
        return readyTypes;
    }

    public static boolean compareAs(StateTypeable sourceType, StateTypeable targetType, Type type){
        switch (sourceType.getStateType()){
            case FREE_STATE -> {return type.asFreeState && compareAgainst(sourceType, targetType, type);}
            case VASSAL -> {return type.asVassal && compareAgainst(sourceType, targetType, type);}
            case EMPIRE -> {return type.asEmpire && compareAgainst(sourceType, targetType, type);}
            default -> {return false;}
        }
    }

    private static boolean compareAgainst(StateTypeable sourceType, StateTypeable targetType, Type type){
        switch (targetType.getStateType()){
            case FREE_STATE -> {return type.againstFreeState && !Faction.get(targetType.getID()).isAdmin();}
            case VASSAL -> {return type.againstVassal;}
            case EMPIRE -> {return type.againstEmpire;}
            default -> {return false;}
        }
    }

    public static float calculateCost(StateTypeable sourceType, StateTypeable targetType, Type type){
        switch (targetType.getStateType()){
            case FREE_STATE -> {return Faction.get(targetType.getID()).isAdmin() ? Float.POSITIVE_INFINITY : type.againstFreeStateMultiplier;}
            case VASSAL -> {return type.againstVassalMultiplier;}
            case EMPIRE -> {return type.againstEmpireMultiplier;}
            default -> {return 0f;}
        }
    }

    public static float calculateCostReverse(StateTypeable sourceType, StateTypeable targetType, Type type){
        float calculateCost = calculateCost(sourceType, targetType, type);
        if(Float.isInfinite(calculateCost) || Float.isNaN(calculateCost)) return 0;
        return 1 / (1 + calculateCost);
    }

    public enum StateType {
        FREE_STATE, VASSAL, EMPIRE;
    }

    public static class StateWithType {

        public UUID id;
        public StateType type;

        public StateWithType(UUID id, StateType type){
            this.id = id; this.type = type;
        }

        public StateWithType(StateTypeable state){
            this.id = state.getID(); this.type = state.getStateType();
        }

    }

    @Field("Agressor")
    public UUID agressor;

    @Field("Victim")
    public UUID victim;

    @Field("GoalType")
    public Type goalType;

    @Field("ID")
    public UUID id;

    @Field("timeOfWarEnd")
    public long timeOfWarEnd;
    @Field("timeOfWarStarted")
    public long timeOfWarStarted;

    public GenericWarGoal processingWarGoal;

    public float cost,reverseCost;
    public boolean isValid;
    @Field("warStarted")
    public boolean warStarted;

    public WarGoal(StateTypeable agressor, StateTypeable victim, Type goalType, UUID id, long timeOfWarEnd, long timeOfWarStarted, boolean warStarted){
        this(agressor.getID(), victim.getID(), goalType, id, timeOfWarEnd, timeOfWarStarted, warStarted);
    }

    public WarGoal(UUID agressor, UUID victim, Type goalType, UUID id, long timeOfWarEnd, long timeOfWarStarted, boolean warStarted){
        this.agressor = agressor;
        this.victim = victim;
        this.goalType = goalType;
        this.id = id == null ? UUID.randomUUID() : id;
        this.isValid = compareAs(findStateTypeable(this.agressor), findStateTypeable(this.victim), goalType);
        this.timeOfWarEnd = timeOfWarEnd;
        this.timeOfWarStarted = timeOfWarStarted;
        if(new Date().after(new Date(timeOfWarEnd))) {this.remove(); return;}
        if(!isValid) {this.remove(); return;}
        this.cost = calculateCost(findStateTypeable(this.agressor), findStateTypeable(this.victim), goalType);
        this.reverseCost = calculateCostReverse(findStateTypeable(this.agressor), findStateTypeable(this.victim), goalType);
        if(this.cost == Float.POSITIVE_INFINITY || this.reverseCost == Float.POSITIVE_INFINITY) {this.remove(); return;}
        if(hasSimilarWars(agressor, victim)) {this.remove(); return;}
        this.processingWarGoal = GenericWarGoal.getWargoal(goalType);
        if(this.processingWarGoal == null) {System.out.println("[Factions]: WTF? GenericWarGoal is null!"); return;}
        this.warStarted = warStarted;
        if(!warStarted) processingWarGoal.initConflict(this);
    }

    public WarGoal(StateTypeable agressor, StateTypeable victim, Type goalType){
        this(agressor, victim, goalType, UUID.randomUUID(), goalType.againstEmpire ? new Date().getTime() + 31L*24L*3600L*1000L : new Date().getTime() + 7L*24L*3600L*1000L, new Date().getTime(), false);
    }

    public boolean hasSimilarWars(StateWithType first, StateWithType second){
        return STORE.values().stream().anyMatch(wg -> (wg.agressor.equals(first) && wg.victim.equals(second)) || (wg.victim.equals(first) && wg.agressor.equals(second)));
    }

    public boolean hasSimilarWars(UUID first, UUID second){


        return this.hasSimilarWars(new StateWithType(findStateTypeable(first)), new StateWithType(findStateTypeable(second)));
    }

    public StateTypeable findReverse(StateTypeable state){
        return this.isAgressor(state) ? this.findStateTypeable(this.victim) : this.findStateTypeable(this.agressor);
    }


    public StateTypeable findStateTypeable(StateWithType type){
        Faction faction = Faction.get(type.id);
        if(faction != null) return faction;
        return Empire.getEmpire(type.id);
    }

    public StateTypeable findStateTypeable(UUID id){
        Faction faction = Faction.get(id);
        if(faction != null) return faction;
        Empire empire = Empire.getEmpireByFaction(id);
        if(empire != null) return empire;
        return Empire.getEmpire(id);
    }

    public String reverseName(Faction faction){
        StateTypeable typeable = this.findReverse(faction);
        return typeable.getName();
    }

    public UUID getID(){
        return this.id;
    }

    public static WarGoal findByAnyStates(StateTypeable first, StateTypeable second){
        return STORE.values().stream().filter(wg -> (wg.agressor.equals(first.getID()) && wg.victim.equals(second.getID())) || (wg.victim.equals(second.getID())&& wg.agressor.equals(first.getID()))).findFirst().orElse(null);
    }

    public static WarGoal activeWarWithState(StateTypeable state){
        StateWithType fType = new StateWithType(state);
        return STORE.values().stream().filter(wg -> wg.agressor.equals(fType)||wg.victim.equals(fType)).findFirst().orElse(null);
    }

    public boolean isAgressor(StateTypeable state){
        return this.agressor.equals(state.getCapitalState().getID());
    }

    public boolean isVictim(StateTypeable state){
        return this.victim.equals(state.getCapitalState().getID());
    }


    public void remove(){
        StateTypeable agressor = this.findStateTypeable(this.agressor);
        StateTypeable victim = this.findStateTypeable(this.victim);
        if(agressor.getStateType() == StateType.EMPIRE) Empire.getEmpireByFaction(agressor.getID()).setWarGoal(null);
        if(victim.getStateType() == StateType.EMPIRE) Empire.getEmpireByFaction(victim.getID()).setWarGoal(null);
        agressor.getCapitalState().setActiveWargoal(null);
        victim.getCapitalState().setActiveWargoal(null);

        STORE.remove(this.id);
    }





    public static void save() {
        Database.save(WarGoal.class, STORE.values().stream().toList());
    }


    public static void add(WarGoal goal){
        STORE.put(goal.id, goal);
    }
}
