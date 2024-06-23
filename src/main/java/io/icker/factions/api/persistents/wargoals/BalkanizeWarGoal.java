package io.icker.factions.api.persistents.wargoals;

import io.icker.factions.api.persistents.*;
import io.icker.factions.util.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BalkanizeWarGoal extends BaseWarGoal{
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
        super.initConflict(goal);
        StateTypeable victim = goal.findStateTypeable(goal.victim);
        StateTypeable aggressor = goal.findStateTypeable(goal.agressor);


        if(victim.getStateType() != WarGoal.StateType.EMPIRE) return false;
        if(aggressor.getStateType() == WarGoal.StateType.VASSAL) return false;
        Empire sourceEmpire = Empire.getEmpireByFaction(aggressor.getID());
        Empire targetEmpire = Empire.getEmpireByFaction(victim.getID());
        targetEmpire = targetEmpire == null ? Empire.getEmpire(victim.getID()) : targetEmpire;
        new Message("§4Государство §6" + aggressor.getCapitalState().getName() + "§4 объявило войну иному Царству: §6" + targetEmpire.name + "§4, с целью §l§nРАСЧЛЕНИТЬ§r§4 его.").sendToGlobalChat();

        return true;
    }

    @Override
    public void destroy(Faction faction) {
        WarGoal goal = faction.getActiveWargoal();
        StateTypeable aggressor = goal.findStateTypeable(goal.agressor);
        StateTypeable victim = goal.findStateTypeable(goal.victim);

        boolean isVictory = faction.getID().equals(faction.getCapitalState().getID());
        if(!isVictory) {
            String empireName = Empire.getEmpireByFaction(faction.getID()).name;
            empireName = empireName == null ? " самому себе " : "Королевству: §6["+empireName+"]§4";

            StateTypeable reverse = goal.findReverse(faction);
            Empire reverseEmpire = Empire.getEmpireByFaction(reverse.getID());
            String reverseName = reverseEmpire == null ? " цыганами из: §6[" + reverse.getCapitalState().getName() + "]§4" : "фашистами-фетишистами из: §6[" + reverseEmpire.name + "]§4.";
            new Message("§4Город: §6[" + faction.getName() + "]§4, принадлежащий" + empireName + ", был варварски опустошён" + reverseName).sendToGlobalChat();
            Empire origEmpire = Empire.getEmpireByFaction(faction.getID());
            return;
        }
        Empire targetEmpire = Empire.getEmpireByFaction(victim.getID());
        targetEmpire = targetEmpire == null ? Empire.getEmpire(victim.getID()) : targetEmpire;

        boolean aggressiveVictory = goal.isVictim(faction);
        if(aggressiveVictory){
            Faction sourceFaction = Faction.get(aggressor.getID());
            Faction targetFaction = Faction.get(victim.getID());


            List<UUID> vassals = targetEmpire.getVassalsIDList();
            vassals.add(targetEmpire.metropolyID);
            for(UUID id : vassals){
                reparations(aggressor.getCapitalState(), Faction.get(id), 0.3f);
            }
            targetEmpire.remove();
            new Message("§aГосударство §6" + aggressor.getCapitalState().getName() + "§a победило Империю §6" + targetEmpire.name + "§a. На месте проигравшей теперь осколки.").sendToGlobalChat();
            this.stopTheWar(aggressor.getActiveWargoal());
            return;
        }
        Empire sourceEmpire = Empire.getEmpireByFaction(aggressor.getID());
        sourceEmpire = sourceEmpire == null ? Empire.getEmpire(aggressor.getID()) : sourceEmpire;
        if(sourceEmpire != null){

            List<UUID> vassals = sourceEmpire.getVassalsIDList();
            vassals.add(sourceEmpire.metropolyID);
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
                sourceEmpire.getVassalsIDList().remove(vassal.getID());
                targetEmpire.getVassalsIDList().add(vassal.getID());
                new Message("§eГород: §c\""+ vassal.getName() + "\"§e вошёл в состав Империи: §5\"" + targetEmpire.name + "\", и выплатил репарации за причинённый ущерб.");
            }
            new Message("§eИмперия: \"§5" + targetEmpire.name + "§e\" победила королевство: \"§5" + sourceEmpire.name + "§e\" в оборонительной войне; Агрессорам пришлось заплатить огромную цену...").sendToGlobalChat();
            return;
        }
        int power = (int)((float)aggressor.getCapitalState().getPower() * 0.9f);
        aggressor.getCapitalState().adjustPower(-power);
        victim.getCapitalState().adjustPower(power);

        User user = vassalize(victim, aggressor);
        String username = user == null ? "[ПРИЗРАК]" : user.getName();

        new Message("§eИмперия: \"§5" + targetEmpire.name + "§e\" победила в оборонительной войне против Города: \"§5" + aggressor.getCapitalState().getName() + "§e\"; Теперь, этот жалкий городок разграблен и вассализирован.").sendToGlobalChat();

    }

    public boolean calculateRadiusDistance(double x1, double z1, double x2, double z2, double max){
        max = max*max;
        double x = x1 - x2, z = z1 - z2;
        return (x*x + z*z) < max;
    }

}
