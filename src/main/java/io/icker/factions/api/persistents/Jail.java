package io.icker.factions.api.persistents;

import io.icker.factions.core.FactionsManager;
import io.icker.factions.database.Field;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Jail {
    @Field("X")
    public double x;

    @Field("Y")
    public double y;

    @Field("Z")
    public double z;

    @Field("Yaw")
    public float yaw;

    @Field("Pitch")
    public float pitch;

    @Field("Level")
    public String level;

    @Field("Prisoners")
    public ArrayList<Prisoner> prisoners = new ArrayList<>();

    private UUID factionID;

    public Jail(UUID factionID, double x, double y, double z, float yaw, float pitch, String level, ArrayList<Prisoner> prisoners) {
        this.factionID = factionID;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.level = level;
        this.prisoners = prisoners;
    }

    public void releaseAllPrisonersBeforeWar(StateTypeable targetState){
        List<Prisoner> targetPrisoners = prisoners.stream().filter(p -> {
            User user = User.get(p.prisoner);
            StateTypeable typeable = user.getFaction();
            if(typeable == null) return false;
            return typeable.getCapitalState().getID().equals(targetState.getCapitalState().getID());
        }).toList();

        MinecraftServer server = FactionsManager.playerManager.getServer();
                targetPrisoners.forEach(p ->{
            User user = User.get(p.prisoner);
            user.resetImprisoned();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(user.getName());
            if(player == null) return;
            Faction faction = user.getFaction();
            Home home = faction.getHome();
            player.teleport(home.x, home.y, home.z);
        });
    }

    public Jail() { ; }

    public Faction getFaction() {
        return Faction.get(factionID);
    }
    
    public void updateWarPunishment(){
        this.prisoners.forEach(prisoner -> {
            User user = User.get(prisoner.prisoner);
            Faction faction = user.getFaction();
            if(faction == null) return;
            if(faction.getID().equals(this.factionID)) return;
            Faction prisonerCapital = faction.getCapitalState();
            Faction sourceFaction = Faction.get(this.factionID);
            Faction sourceCapital = sourceFaction.getCapitalState();
            UUID sourceCapitalID = sourceCapital.getID();

            float multuplier = 1;
            switch (user.rank){
                case OWNER -> multuplier        += 8;
                case LEADER -> multuplier       += 7;
                case COMMANDER -> multuplier    += 6;
                case SHERIFF -> multuplier      += 5;
                default -> multuplier           += 1;
            }



            WarGoal goal = WarGoal.findByAnyStates(sourceCapital, prisonerCapital);
            if(goal == null) return;
            boolean sourceIsAgressors = goal.agressor.id.equals(sourceCapitalID);
            float punishAmount = sourceIsAgressors ? goal.cost : goal.reverseCost;
            multuplier *= punishAmount;
            multuplier *= sourceIsAgressors ? 1 : -1;

            faction.adjustCapitulationPoints((int)multuplier);

        });
    }
}
