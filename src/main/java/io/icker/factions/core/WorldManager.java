package io.icker.factions.core;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.MiscEvents;
import io.icker.factions.api.events.PlayerEvents;
import io.icker.factions.api.persistents.*;
import io.icker.factions.util.Message;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ChunkPos;


public class WorldManager {
    public static void register() {
        PlayerEvents.ON_MOVE.register(WorldManager::onMove);
        MiscEvents.ON_MOB_SPAWN_ATTEMPT.register(WorldManager::onMobSpawnAttempt);
    }

    private static void onMobSpawnAttempt() {
        // TODO Implement this
    }
 
    private static void onMove(ServerPlayerEntity player) {
        User user = User.get(player.getName().getString());
        ServerWorld world = player.getWorld();
        String dimension = world.getRegistryKey().getValue().toString();

        ChunkPos chunkPos = world.getChunk(player.getBlockPos()).getPos();

        Claim claim = Claim.get(chunkPos.x, chunkPos.z, dimension);
        if (user.autoclaim && claim == null) {
            Faction faction = user.getFaction();
            int requiredPower = (faction.getClaims().size() + 1) * FactionsMod.CONFIG.CLAIM_WEIGHT;
            int maxPower = FactionsMod.CONFIG.MAX_POWER;

            if (maxPower < requiredPower) {
                new Message("Not enough faction power to claim chunk, autoclaim toggled off").fail().send(player, false);
                user.autoclaim = false;
            } else {
                faction.addClaim(chunkPos.x, chunkPos.z, dimension);
                claim = Claim.get(chunkPos.x, chunkPos.z, dimension);
                new Message("Chunk (%d, %d) claimed by %s", chunkPos.x, chunkPos.z, player.getName().getString())
                    .send(faction);
            }
        }
        if(world.getTime() % 40 != 0) return;
        if (FactionsMod.CONFIG.RADAR && user.radar) {
            if (claim != null) {
                Message additional = new Message("");
                if(Empire.getEmpireByFaction(claim.factionID) != null){
                    Empire empire = Empire.getEmpireByFaction(claim.factionID);
                    additional.add(new Message(" | " + empire.name).format(empire.getCapitalState().getColor()));
                }
                if(claim.getFaction().getActiveWargoal() != null){
                    WarGoal goal = claim.getFaction().getCapitalState().getActiveWargoal();
                    additional.add(new Message(" | Воюет с " + goal.reverseName(claim.getFaction())).format(Formatting.DARK_RED));
                }
                new Message(claim.getFaction().getName())
                    .format(claim.getFaction().getColor())
                    .add(additional)
                    .send(player, true);
            } else {
                new Message("[ПУСТОШЬ]")
                    .format(Formatting.DARK_GRAY)
                    .send(player, true);
            }
        }
    }
}
