package io.icker.factions.api.persistents.wargoals;

import io.icker.factions.api.persistents.Empire;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.StateTypeable;
import io.icker.factions.api.persistents.WarGoal;
import io.icker.factions.util.Message;

public class SubjugateWarGoal extends BaseWarGoal{


    @Override
    public boolean isPrimitive() {
        return true;
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
        super.initConflict(goal);
        StateTypeable victim = goal.findStateTypeable(goal.victim);
        StateTypeable aggressor = goal.findStateTypeable(goal.agressor);


        if(victim.getStateType() != WarGoal.StateType.FREE_STATE) return false;
        if(aggressor.getStateType() != WarGoal.StateType.EMPIRE) return false;
        Empire empire = Empire.getEmpireByFaction(aggressor.getID());
        new Message("§4Империя §6" + empire.name + "§4 объявило войну вольному городу §6" + victim.getCapitalState().getName() + "§4, чтобы подчинить его").sendToGlobalChat();

        return true;
    }

    @Override
    public void destroy(Faction faction) {
        WarGoal goal = faction.getActiveWargoal();
        StateTypeable aggressor = goal.findStateTypeable(goal.agressor);
        StateTypeable victim = goal.findStateTypeable(goal.victim);
        boolean kingdomWin = faction.getStateType() == WarGoal.StateType.FREE_STATE;

        if(kingdomWin) {
            subjugate(aggressor, victim);
            Empire empire = Empire.getEmpireByFaction(aggressor.getID());
            new Message("§aИмперия §6" + empire.name + "§a подчинила себе вольный город §6" + victim.getCapitalState().getName() + "§a.").sendToGlobalChat();
            stopTheWar(goal);
            return;
        }
        reparations(victim, aggressor, 0.3f);
        Empire empire = Empire.getEmpireByFaction(aggressor.getID());
        new Message("§aИмперия §6" + empire.name + "§a проиграла войну с городом §6" + victim.getCapitalState().getName() + "§a, и теперь должна ему репарации").sendToGlobalChat();
        stopTheWar(goal);
    }
}
