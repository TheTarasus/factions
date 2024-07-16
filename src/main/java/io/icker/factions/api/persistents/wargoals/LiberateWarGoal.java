package io.icker.factions.api.persistents.wargoals;

import io.icker.factions.api.persistents.*;
import io.icker.factions.util.Message;

public class LiberateWarGoal extends BaseWarGoal{

    public boolean isVassal;

    public LiberateWarGoal(boolean isVassalAggressor) {
        super(isVassalAggressor);
    }

    LiberateWarGoal(){;}

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public boolean willVassalize(boolean agressiveWin) {
        return !agressiveWin;
    }

    @Override
    public boolean initConflict(WarGoal goal) {
        super.initConflict(goal);
        StateTypeable aggressor = goal.findStateTypeable(goal.agressor);
        StateTypeable victim = goal.findStateTypeable(goal.victim);

        Empire sourceEmpire = Empire.getEmpireByFaction(aggressor.getID());
        Empire targetEmpire = Empire.getEmpireByFaction(victim.getID());
        if(sourceEmpire.getID().equals(targetEmpire.getID())){
            this.isVassal = true;
            initSelfLiberation(aggressor, victim);
            return true;
        }
        new Message("§4Государство §6" +Faction.get(aggressor.getID()).getName() + "§4 начало войну против империи §6" + targetEmpire.name + "§4 за освобождение города §6" + Faction.get(victim.getID()).getName() + "§4!").sendToGlobalChat();
        return true;
    }

    private void initSelfLiberation(StateTypeable aggressor, StateTypeable victim) {
        Empire sourceEmpire = Empire.getEmpireByFaction(aggressor.getID());
        sourceEmpire.removeVassal(aggressor.getID());
        new Message("§4Самопровозглашённая республика §6" +Faction.get(aggressor.getID()).getName() + "§4 начала войну против оков империи §6" + Empire.getEmpireByFaction(victim.getID()).name +"§4!").sendToGlobalChat();
    }

    @Override
    public void destroy(Faction faction) {
        WarGoal goal = faction.getActiveWargoal();
        Faction aggro = goal.findStateTypeable(goal.agressor).getCapitalState();
        Faction victim = goal.findStateTypeable(goal.victim).getCapitalState();
        boolean isAggressor = goal.isAgressor(faction);
        Empire empire = Empire.getEmpireByFaction(victim.getID());
        if(isVassalAggressor && isAggressor){
            User user = vassalize(victim, aggro);
            String username = user == null ? "[ПУСТОТА]" : user.getName();
            new Message("§aИмперия §6" + Empire.getEmpireByFaction(victim.getID()).name + "§a смогла подавить восстание анархистов из провинции §6" + aggro.getName() + "§a. На место старых бузотёров, метрополия поставила более лояльного губернатора: §6" + username).sendToGlobalChat();
            stopTheWar(goal);
            return;
        }
        if(isVassalAggressor) {
            Empire chains = Empire.getEmpireByFaction(aggro.getID());
            if (chains != null) chains.removeVassal(aggro.getID());
            new Message("§aСамопровозглашённая республика §6" + aggro.getCapitalState().getName() + "§a освободилась от оков империи §6" + Empire.getEmpireByFaction(victim.getID()).name + "§a, и победила в войне за независимость!").sendToGlobalChat();
            stopTheWar(goal);
            return;
        }
        Empire aggroEmpire = Empire.getEmpireByFaction(aggro.getID());
        String aggroName = aggroEmpire == null ? aggro.getCapitalState().getName() : aggroEmpire.name;
        if(isAggressor){
            new Message("§aИмперия: §6[" + empire.name + "]§a смогла выдержать государтсвенный переворот, спланированный иностранными агентами из государства §6[" + aggroName + "]§a. Теперь, жалкие конспираторы будут платить репарации.").sendToGlobalChat();
            reparations(victim, aggro, 0.15f);
            stopTheWar(goal);
            return;
        }
        empire.removeVassal(victim.getID());
        new Message("§aОт королевства: §6[" + empire.name + "]§a откололся вассал: §6["+ victim.getName() +"]§a, при поддержке иностранных агентов из: §6[" + aggroName + "]§a.").sendToGlobalChat();
        stopTheWar(goal);
    }

}
