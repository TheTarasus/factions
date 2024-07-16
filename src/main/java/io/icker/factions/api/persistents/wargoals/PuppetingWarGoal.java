package io.icker.factions.api.persistents.wargoals;

import io.icker.factions.api.persistents.*;
import io.icker.factions.util.Message;

public class PuppetingWarGoal extends BaseWarGoal{


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
        StateTypeable aggressor = goal.findStateTypeable(goal.agressor);
        StateTypeable victim = goal.findStateTypeable(goal.victim);

        if(victim.getStateType() != WarGoal.StateType.FREE_STATE) return false;
        if(aggressor.getStateType() != WarGoal.StateType.EMPIRE) return false;
        Empire empire = Empire.getEmpireByFaction(aggressor.getID());
        new Message("§4Империя §6" + empire.name + "§4 объявило войну вольному городу §6" + victim.getCapitalState().getName() + "§4, чтобы заселить туда своих наместников.").sendToGlobalChat();
        return true;
    }

    @Override
    public void destroy(Faction faction) {

        boolean kingdomWin = faction.getStateType().equals(WarGoal.StateType.FREE_STATE);
        WarGoal goal = faction.getActiveWargoal();
        StateTypeable victim = goal.findStateTypeable(goal.victim);
        StateTypeable aggressor = goal.findStateTypeable(goal.agressor);

        if(kingdomWin) {
            User goodUser = vassalize(aggressor, faction);
            String username = goodUser == null ? "[Призрак]" : goodUser.getName();
            Empire empire = Empire.getEmpireByFaction(aggressor.getID());
            new Message("§aИмперия §6" + empire.name + "§a подчинила себе вольный город §6" + victim.getCapitalState().getName() + "§a, теперь им управляет наместник - §6" + username).sendToGlobalChat();
            stopTheWar(goal);
            return;
        }
        reparations(victim, aggressor, 0.4f);
        Empire empire = Empire.getEmpireByFaction(aggressor.getID());
        new Message("§aИмперия §6" + empire.name + "§a проиграла войну с городом §6" + victim.getCapitalState().getName() + "§a, и теперь должна ему репарации").sendToGlobalChat();
        stopTheWar(goal);
    }

}
