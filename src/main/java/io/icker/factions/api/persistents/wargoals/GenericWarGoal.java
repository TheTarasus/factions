package io.icker.factions.api.persistents.wargoals;

import io.icker.factions.api.persistents.*;
import io.icker.factions.database.Field;

import java.util.List;

public abstract class GenericWarGoal {
    public abstract boolean isPrimitive();
    public abstract boolean isLongWar();
    public abstract boolean willDestroy(boolean aggressiveWin);
    public abstract boolean willVassalize(boolean aggressiveWin);
    @Field("isVassal")
    public boolean isVassalAggressor;
    GenericWarGoal(boolean isVassalAggressor){
        this.isVassalAggressor = isVassalAggressor;
    }
    GenericWarGoal(){;}
    public User vassalize(StateTypeable vassalizer, StateTypeable victim){
        Empire sourceEmpire = Empire.getEmpireByFaction(vassalizer.getID());
        sourceEmpire = sourceEmpire == null ? Empire.getEmpire(vassalizer.getID()) : sourceEmpire;

        Empire targetEmpire = Empire.getEmpireByFaction(victim.getID());
        targetEmpire = targetEmpire == null ? Empire.getEmpire(victim.getID()) : targetEmpire;

        List<User> badUsers = victim.getCapitalState().getUsers().stream().filter(user -> user.rank == User.Rank.OWNER || user.rank == User.Rank.LEADER).toList();

        if(targetEmpire.metropolyID == victim.getID()){
            targetEmpire.getCapitalState().setCapitulated(true);
            targetEmpire.remove();
            return null;
        }

        sourceEmpire.getVassalsIDList().add(victim.getID());
        targetEmpire.getVassalsIDList().remove(victim.getID());

        badUsers.forEach(User::leaveFaction);
        List<User> users = vassalizer.getCapitalState().getUsers();
        User goodUser = users.stream().filter(user -> user.rank == User.Rank.COMMANDER).findFirst().orElse(null);
        goodUser = goodUser == null ? users.stream().filter(user -> user.rank == User.Rank.SHERIFF).findFirst().orElse(null) : goodUser;
        goodUser = goodUser == null ? users.stream().filter(user -> user.rank == User.Rank.MEMBER).findFirst().orElse(null) : goodUser;
        if(goodUser != null) {
            goodUser.leaveFaction();
            goodUser.joinFaction(vassalizer.getID(), User.Rank.OWNER);
        }
        return goodUser;
    }

    public void subjugate(StateTypeable subjugator, StateTypeable victim){
        Empire targetEmpire = Empire.getEmpireByFaction(victim.getID());
        if(targetEmpire != null) targetEmpire.getVassalsIDList().remove(victim.getID());
        Empire.getEmpireByFaction(subjugator.getID()).getVassalsIDList().add(victim.getID());
    }

    public void reparations(StateTypeable victor, StateTypeable victim, float percents){
        Faction targetFaction = Faction.get(victim.getID());
        int power = targetFaction.getPower();
        targetFaction.adjustPower((int)-((float)power*percents));
        victor.getCapitalState().adjustPower((int)((float)power*percents));
    }
    public abstract boolean initConflict(WarGoal goal);

    public abstract void stopTheWar(WarGoal goal);
    public abstract void destroy(Faction faction);
    public static GenericWarGoal getWargoal(WarGoal.Type type) {
        switch (type) {
            case LIBERATE -> {
                return new LiberateWarGoal();
            }
            case ANNEX -> {
                return new AnnexWarGoal();
            }
            case ANNIHILATE -> {
                return new AnnihilateWarGoal();
            }
            case SUBJUGATE -> {
                return new SubjugateWarGoal();
            }
            case PUPPETING -> {
                return new PuppetingWarGoal();
            }
            case BALKANIZE -> {
                return new BalkanizeWarGoal();
            }
            case CONQUEST -> {
                return new ConquestWarGoal();
            }
        }
        return null;
    }
}
