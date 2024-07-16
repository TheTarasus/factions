package io.icker.factions.api.persistents.wargoals;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.persistents.*;
import io.icker.factions.database.Field;
import io.icker.factions.util.Message;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;

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

        Faction victimFaction = victim.getStateType().equals(WarGoal.StateType.EMPIRE) ? victim.getCapitalState() : (Faction) victim;

        List<User> badUsers = victimFaction.getUsers().stream().filter(user -> !user.getRank().equals(User.Rank.MEMBER)).toList();

        if(sourceEmpire == null){
            return null;
        }
        if(targetEmpire == null){
            return vassalizeTargetCity(sourceEmpire, victimFaction, badUsers);
        }

        if(targetEmpire.getMetropolyID().equals(victim.getID())){
            targetEmpire.getCapitalState().setCapitulated(true);
            targetEmpire.remove();
            return null;
        }

        sourceEmpire.addVassal(victim.getID());
        targetEmpire.removeVassal(victim.getID());

        badUsers.forEach(u -> {
            ServerPlayerEntity player = FactionsMod.SERVER_INSTANCE.getPlayerManager().getPlayer(u.getName());
            if(player != null) {
                new Message("§4Твой клан разрушен, ты теперь §nбомжик с палкой").send(player, false);
                for(int i = 0; i < 3; i++)
                    player.playSound(SoundEvents.ENTITY_BLAZE_AMBIENT, 1.0f, 0.5f);
            }
            u.leaveFaction();});
        List<User> users = vassalizer.getCapitalState().getUsers();
        User goodUser = users.stream().filter(user -> user.getRank().equals(User.Rank.COMMANDER)).findFirst().orElse(null);
        goodUser = goodUser == null ? users.stream().filter(user -> user.getRank().equals(User.Rank.SHERIFF)).findFirst().orElse(null) : goodUser;
        goodUser = goodUser == null ? users.stream().filter(user -> user.getRank().equals(User.Rank.MEMBER)).findFirst().orElse(null) : goodUser;
        if(goodUser == null) {victimFaction.remove(); return null;}
            goodUser.leaveFaction();
            goodUser.joinFaction(victimFaction.getID(), User.Rank.OWNER);
        return goodUser;
    }

    private User vassalizeTargetCity(Empire vassalizer, Faction capitalState, List<User> badUsers) {



        vassalizer.addVassal(capitalState.getID());

        badUsers.forEach(u -> {
            ServerPlayerEntity player = FactionsMod.SERVER_INSTANCE.getPlayerManager().getPlayer(u.getName());
            if(player != null) {
                new Message("§4Твой клан захвачен. Твоего лидера сместили, и тебя - в том числе. Теперь, ты бомжик с палкой.").send(player, false);
                for(int i = 0; i < 3; i++)
                    player.playSound(SoundEvents.ENTITY_BLAZE_AMBIENT, 1.0f, 0.5f);
            }
            u.leaveFaction();});
        List<User> users = vassalizer.getCapitalState().getUsers();
        User goodUser = users.stream().filter(user -> user.getRank() == User.Rank.COMMANDER).findFirst().orElse(null);
        goodUser = goodUser == null ? users.stream().filter(user -> user.getRank() == User.Rank.SHERIFF).findFirst().orElse(null) : goodUser;
        goodUser = goodUser == null ? users.stream().filter(user -> user.getRank() == User.Rank.MEMBER).findFirst().orElse(null) : goodUser;
        if(goodUser == null) {capitalState.remove(); return null;}
            goodUser.leaveFaction();
            goodUser.joinFaction(capitalState.getID(), User.Rank.OWNER);
        return goodUser;
    }

    public void subjugate(StateTypeable subjugator, StateTypeable victim){
        Empire targetEmpire = Empire.getEmpireByFaction(victim.getID());
        List<User> badUsers = victim.getCapitalState().getUsers().stream().filter(user -> !user.getRank().equals(User.Rank.MEMBER)).toList();
        badUsers.forEach(u -> {
            ServerPlayerEntity player = FactionsMod.SERVER_INSTANCE.getPlayerManager().getPlayer(u.getName());
            if(player != null) {
                new Message("§6Твой клан захватили, но не беспокойся - ты остался при своей должности.").send(player, false);
                for(int i = 0; i < 3; i++)
                    player.playSound(SoundEvents.ENTITY_BLAZE_AMBIENT, 1.0f, 0.5f);
            }
        });
        if(targetEmpire != null) {targetEmpire.removeVassal(victim.getID());}
        Empire.getEmpireByFaction(subjugator.getID()).addVassal(victim.getID());
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
