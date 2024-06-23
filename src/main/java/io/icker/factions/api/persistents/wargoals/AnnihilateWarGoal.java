package io.icker.factions.api.persistents.wargoals;

import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.StateTypeable;
import io.icker.factions.api.persistents.WarGoal;
import io.icker.factions.util.Message;

public class AnnihilateWarGoal extends BaseWarGoal{
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
        return aggressiveWin;
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
        if(aggressor.getStateType() == WarGoal.StateType.VASSAL) return false;
        new Message("§4Государство §6" + aggressor.getCapitalState().getName() + "§4 объявило войну вольному городу §6" + victim.getCapitalState().getName() + "§4, с целью стереть его с лица Земли.").sendToGlobalChat();

        return true;
    }

    @Override
    public void destroy(Faction faction) {

        WarGoal goal = faction.getActiveWargoal();
        boolean aggressorVictory = goal.isVictim(faction);

        StateTypeable aggressor = goal.findStateTypeable(goal.agressor);
        StateTypeable victim = goal.findStateTypeable(goal.victim);

        if(aggressorVictory) {aggressorVictory(aggressor, victim); return;}
        int power = (int)((float)aggressor.getCapitalState().getPower() * 0.9f);
        new Message("§aГосударство §6" + aggressor.getCapitalState().getName() + "§a хотело стереть с лица Земли вольный город §6" + victim.getCapitalState().getName() + "§a. Но что-то пошло не по плану, и теперь агрессору придётся платить репарации в размере: §6" + power + "₽").sendToGlobalChat();
        aggressor.getCapitalState().adjustPower(-power);
        victim.getCapitalState().adjustPower(power);
        stopTheWar(goal);
    }


    public void aggressorVictory(StateTypeable agressor, StateTypeable victim){
        new Message("§aГосударство §6" + agressor.getCapitalState().getName() + "§a стёрло с лица Земли вольный город §6" + victim.getCapitalState().getName() + "§a.").sendToGlobalChat();
        victim.getCapitalState().remove();
        stopTheWar(victim.getActiveWargoal());
    }
}
