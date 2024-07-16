package io.icker.factions.api.persistents.wargoals;

import io.icker.factions.api.persistents.*;
import io.icker.factions.util.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ConquestWarGoal extends BaseWarGoal{

    @Override
    public boolean isLongWar() {
        return true;
    }

    @Override
    public boolean willVassalize(boolean aggressiveWin) {
        return true;
    }
    @Override
    public boolean initConflict(WarGoal goal) {
        super.initConflict(goal);
        StateTypeable aggressor = goal.findStateTypeable(goal.agressor);
        StateTypeable victim = goal.findStateTypeable(goal.victim);

        if(aggressor.getStateType() != WarGoal.StateType.EMPIRE) return false;
        if(victim.getStateType() == WarGoal.StateType.FREE_STATE) return false;
        Empire sourceEmpire = Empire.getEmpireByFaction(aggressor.getID());
        sourceEmpire = sourceEmpire == null ? Empire.getEmpire(aggressor.getID()) : sourceEmpire;

        Empire targetEmpire = Empire.getEmpireByFaction(victim.getID());
        targetEmpire = targetEmpire == null ? Empire.getEmpire(victim.getID()) : targetEmpire;

        new Message("§4Империя §6[" + sourceEmpire.name + "]§4 объявляет Великий Поход против §6[" + targetEmpire.name + "]§4. Это будет долгая война...").sendToGlobalChat();


        return true;
    }

    @Override
    public void destroy(Faction faction) {
        WarGoal goal = faction.getActiveWargoal();
        StateTypeable victim = goal.findStateTypeable(goal.victim);
        StateTypeable aggressor = goal.findStateTypeable(goal.agressor);


        Faction targetFaction = Faction.get(victim.getID());
        targetFaction = targetFaction == null ? victim.getCapitalState() : targetFaction;
        Empire sourceEmpire = Empire.getEmpireByFaction(aggressor.getID());
        sourceEmpire = sourceEmpire == null ? Empire.getEmpire(aggressor.getID()) : sourceEmpire;
        Empire targetEmpire = Empire.getEmpireByFaction(victim.getID());
        targetEmpire = targetEmpire == null ? Empire.getEmpire(victim.getID()) : targetEmpire;

        int victoryStatus = victoryStatus(sourceEmpire, targetEmpire);

        if (victoryStatus == 0) {
            boolean isVictim = goal.isVictim(faction);
            if(isVictim){
                new Message("§4Город: §6[" + faction.getName() +"]§4, некогда принадлежащий королевству: §6[" + targetEmpire.name + "]§4 - захвачен Империей: §6[" + sourceEmpire.name+"]§4.").sendToGlobalChat();
                subjugate(sourceEmpire, faction);
                return;
            }
            new Message("§4Город: §6[" + faction.getName() +"]§4, принадлежащий захватнической империи: §6[" + sourceEmpire.name + "]§4 - освобождён Королевством: §6[" + targetEmpire.name+"]§4.").sendToGlobalChat();
            subjugate(targetEmpire, faction);
            return;
        }
        if (victoryStatus == 1) {
            new Message("§4Империя §6[" + targetEmpire.name +"]§4 повержена под натиском Военной Машины: §6[" + sourceEmpire.name + "]§4. Это конец войны...").sendToGlobalChat();
            vassalize(sourceEmpire, targetFaction);
            stopTheWar(goal);
            return;
        }
        List<UUID> vassals = sourceEmpire.getVassalsIDList();
        vassals.add(sourceEmpire.getMetropolyID());
        List<Faction> nearFactions = new ArrayList<>();
        for(UUID id : vassals){
            Faction vassal = Faction.get(id);
            reparations(victim.getCapitalState(), vassal, 0.3f);
            nearFactions.add(vassal);
        }
        Home home = victim.getCapitalState().getHome();
        int totalFactions = (int)((float)nearFactions.size()*0.3f);
        int j = 0;
        for(int i = 0; i < totalFactions; i++){
            Faction vassal = nearFactions.get(i);
            Home fHome = vassal.getHome();
            boolean isNear = calculateRadiusDistance(home.x, home.z, fHome.x, fHome.z, 3072);
            if(j>totalFactions+5) break;
            j++;
            if(!isNear) {i--; continue;}
            sourceEmpire.removeVassal(vassal.getID());
            targetEmpire.addVassal(vassal.getID());
            new Message("§eГород: §c\""+vassal.getName() + "\"§e вошёл в состав Империи: §5\"" + targetEmpire.name + "\", в качестве репарации.");
        }
        stopTheWar(sourceEmpire.goal);
        new Message("§4Империя §6[" + sourceEmpire.name +"]§4 не смогла себя оправдать в войне против: §6[" + targetEmpire.name + "]§4. Это конец войны...").sendToGlobalChat();
    }

    public boolean calculateRadiusDistance(double x1, double z1, double x2, double z2, double max){
        max = max*max;
        double x = x1 - x2, z = z1 - z2;
        return (x*x + z*z) < max;
    }
}
