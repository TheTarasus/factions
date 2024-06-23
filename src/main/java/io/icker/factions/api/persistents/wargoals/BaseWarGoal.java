package io.icker.factions.api.persistents.wargoals;

import io.icker.factions.api.persistents.Empire;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.StateTypeable;
import io.icker.factions.api.persistents.WarGoal;

public class BaseWarGoal extends GenericWarGoal{
    BaseWarGoal(boolean isVassalAggressor) {
        super(isVassalAggressor);
    }
    BaseWarGoal(){;}

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isLongWar() {
        return false;
    }

    @Override
    public boolean willDestroy(boolean aggressiveWin) {
        return false;
    }

    @Override
    public boolean willVassalize(boolean aggressiveWin) {
        return false;
    }

    @Override
    public boolean initConflict(WarGoal goal) {
        StateTypeable aggressor = goal.findStateTypeable(goal.agressor);
        StateTypeable victim = goal.findStateTypeable(goal.victim);


        Faction sourceFaction = aggressor.getCapitalState();
        Faction targetFaction = victim.getCapitalState();


        sourceFaction.setActiveWargoal(goal);
        targetFaction.setActiveWargoal(goal);


        Empire sourceEmpire = Empire.getEmpireByFaction(sourceFaction.getID());
        Empire targetEmpire = Empire.getEmpireByFaction(targetFaction.getID());


        if(sourceEmpire != null) sourceEmpire.setWarGoal(goal);
        if(targetEmpire != null) targetEmpire.setWarGoal(goal);

        return true;
    }

    @Override
    public void stopTheWar(WarGoal goal) {
        Faction sourceFaction = Faction.get(goal.agressor.id);
        Empire sourceEmpire = sourceFaction == null ? Empire.getEmpire(goal.agressor.id) : Empire.getEmpireByFaction(sourceFaction.getID());


        if(sourceEmpire != null)    sourceEmpire.setWarGoal(null);
        if(sourceFaction != null)   sourceFaction.setActiveWargoal(null);


        Faction targetFaction = Faction.get(goal.victim.id);
        Empire targetEmpire = targetFaction == null ? Empire.getEmpire(goal.victim.id) : Empire.getEmpireByFaction(targetFaction.getID());


        if(targetEmpire != null)    targetEmpire.setWarGoal(null);
        if(targetFaction != null)   targetFaction.setActiveWargoal(null);

        goal.remove();
    }

    public int victoryStatus(StateTypeable aggressive, StateTypeable victim){
        if(isTargetDestroyed(aggressive)) return -1;
        if(isTargetDestroyed(victim)) return 1;
        return 0;
    }

    public boolean isTargetDestroyed(StateTypeable state){
        Faction faction = state.getCapitalState();
        return faction.getCapitulated();
    }


    @Override
    public void destroy(Faction faction) {

    }
}
