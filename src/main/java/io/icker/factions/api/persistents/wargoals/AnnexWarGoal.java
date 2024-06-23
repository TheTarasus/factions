package io.icker.factions.api.persistents.wargoals;

import io.icker.factions.api.persistents.*;
import io.icker.factions.util.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AnnexWarGoal extends BaseWarGoal{
    AnnexWarGoal(boolean isVassalAggressor) {
        super(isVassalAggressor);
    }

    AnnexWarGoal(){;}

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
        StateTypeable victim = goal.findStateTypeable(goal.victim);
        StateTypeable aggressor = goal.findStateTypeable(goal.agressor);
        if(victim.getStateType() != WarGoal.StateType.FREE_STATE) return false;
        if(aggressor.getStateType() == WarGoal.StateType.VASSAL) return false;
        new Message("§4Государство §6" + aggressor.getCapitalState().getName() + "§4 объявило войну вольному городу §6" + victim.getCapitalState().getName() + "§4, чтобы его полностью аннексировать и присвоить все богатства").sendToGlobalChat();
        return true;
    }

    @Override
    public void destroy(Faction faction) {
        super.destroy(faction);
        WarGoal goal = faction.getActiveWargoal();
        boolean isVictim = goal.isVictim(faction);
        if(isVictim) {
            Faction aggroFaction = goal.findStateTypeable(goal.agressor).getCapitalState();
            annex(aggroFaction, faction);
            return;
        }
        Faction victimFaction = goal.findStateTypeable(goal.victim).getCapitalState();
        reparations(victimFaction, faction, 0.5f);
        new Message("§aГосударство §6" + faction.getCapitalState().getName() + "§a не смогло аннексировать вольный город §6" + victimFaction.getCapitalState().getName() + "§a, и теперь оно должно платить ему репарации.").sendToGlobalChat();
        stopTheWar(goal);
    }

    public void annex(StateTypeable aggressor, StateTypeable victim){
        aggressor.getActiveWargoal().remove();
        victim.getActiveWargoal().remove();

        List<Claim> claims = victim.getCapitalState().getClaims();
        Faction victor = aggressor.getCapitalState();
        for(Claim claim : claims){
            victor.addClaim(claim.x, claim.z, claim.level);
        }
        List<User> users = victim.getCapitalState().getUsers();
        for(User user : users){
            user.leaveFaction();
            user.joinFaction(aggressor.getCapitalState().getID(), User.Rank.MEMBER);
        }

        int power = victim.getCapitalState().getPower();
        victor.adjustPower(power);
        new Message("§aГосударство §6" + victor.getName() + "§a аннексировало вольный город §6" + victim.getCapitalState().getName() + "§a, и присвоило себе все его богатства.").sendToGlobalChat();
        victim.getCapitalState().remove();
        stopTheWar(aggressor.getActiveWargoal());
    }

}
