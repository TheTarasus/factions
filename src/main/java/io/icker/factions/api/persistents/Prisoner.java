package io.icker.factions.api.persistents;

import io.icker.factions.database.Field;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.Date;
import java.util.UUID;

public class Prisoner {

    @Field("Prisoner")
    public String prisoner;
    @Field("JailFaction")
    public UUID jailFaction;
    @Field("World")
    public String world;
    @Field("DateBeforeFree")
    public long dateBeforeFree;

    @Field("x")
    public int x;

    @Field("y")
    public int y;

    @Field("z")
    public int z;

    public Prisoner(String prisoner, UUID jailFaction, long dateBeforeFree, int x, int y, int z, String world){
        this.prisoner = prisoner;
        this.dateBeforeFree = dateBeforeFree;
        this.jailFaction = jailFaction;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Prisoner(){;}

    public boolean isValid(MinecraftServer server){
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(prisoner);
        if(player == null) return false;
        if(prisoner == null) return false;
        User user = User.get(prisoner);
        Faction faction = user.getFaction();
        if(Permissions.check(player, "factions.prison.bypass")) return false;
        if(faction == null) return true;
        return !faction.isAdmin();
    }

    public boolean checkIfInJail(MinecraftServer server){
        ServerPlayerEntity playerEntity = server.getPlayerManager().getPlayer(prisoner);
        if(playerEntity == null) return true;
        if(this.dateBeforeFree < new Date().getTime()) return false;
        ChunkPos pos = playerEntity.getChunkPos();
        Claim claim = Claim.get(pos.x, pos.z, world);
        if(claim == null) return false;
        claim.getFaction();
        return false;
    }


}
