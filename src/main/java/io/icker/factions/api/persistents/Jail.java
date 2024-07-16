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
    private ArrayList<Prisoner> prisoners = new ArrayList<>();

    @Field("UUID")
    public UUID factionID;

    public Jail(UUID factionID, double x, double y, double z, float yaw, float pitch, String level, ArrayList<Prisoner> prisoners) {
        this.factionID = factionID;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.level = level;
        this.setPrisoners(prisoners);
    }

    public void releaseAllPrisonersBeforeWar(StateTypeable targetState){
        if(this.factionID == null) return;
        List<Prisoner> targetPrisoners = getPrisoners().stream().filter(p -> {
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
        if(this.factionID == null) return;
        this.getPrisoners().forEach(prisoner -> {
            User user = User.get(prisoner.prisoner);
            Faction faction = user.getFaction();
            if(faction == null) return;
            if(faction.getID().equals(this.factionID)) return;
            Faction prisonerCapital = faction.getCapitalState();
            Faction sourceFaction = getFaction();
            Faction sourceCapital = sourceFaction.getCapitalState();
            UUID sourceCapitalID = sourceCapital.getID();

            float multuplier = switch (user.getRank()) {
                case OWNER -> prisonerCapital.getID().equals(faction.getID()) ? 100 : 12;
                case LEADER -> 9;
                case COMMANDER -> 5;
                case SHERIFF -> 3;
                default -> 1;
            };


            WarGoal goal = WarGoal.findByAnyStates(sourceCapital, prisonerCapital);
            if(goal == null) return;

            faction.adjustCapitulationPoints((int)multuplier);

        });
    }

    public ArrayList<Prisoner> getPrisoners() {
        return prisoners;
    }

    public void setPrisoners(ArrayList<Prisoner> prisoners) {
        if(this.factionID == null) return;
        this.prisoners = prisoners;
    }
}
