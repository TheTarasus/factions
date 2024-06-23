package io.icker.factions.mixin;

import io.icker.factions.FactionsMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityBorderMixin {

    @Shadow public abstract double getX();

    @Shadow public abstract double getZ();

    @Shadow public abstract boolean hasVehicle();

    @Shadow public abstract void teleport(double destX, double destY, double destZ);

    @Shadow public abstract double getY();

    @Shadow public World world;

    @Inject(method = "move", at = @At("HEAD"))
    public void move(MovementType movementType, Vec3d movement, CallbackInfo ci){
        if(!FactionsMod.CONFIG.EARTH_BORDERS_ENABLED) return;
        boolean out = outOfBorders((int)this.getX(), (int)this.getZ());
        if(!out) return;
        if(this.hasVehicle()) return;
        int destX = destinationPolarX((int)this.getX());
        int destZ = (int) (this.getZ() > FactionsMod.CONFIG.EARTH_MAX_Z ? this.getZ() - 16 : this.getZ() + 16);
        this.teleport(destX, this.getY(), destZ);

    }

    @Unique
    public int destinationPolarX(int x){
        int min = FactionsMod.CONFIG.EARTH_MIN_X, max = FactionsMod.CONFIG.EARTH_MAX_X;
        int range = max - min;
        int center = (range>>1) + min;
        x = Math.max(min, Math.min(max, x));
        if(x >= max - 8) return min + 16;
        if(x <= min + 8) return max - 16;
        return x > center ? x - (range>>1) : x + (range>>1);


    }

    @Unique
    public boolean outOfBorders(int entityX, int entityZ){
        int mapMinX = FactionsMod.CONFIG.EARTH_MIN_X,
            mapMinZ = FactionsMod.CONFIG.EARTH_MIN_Z,
            mapMaxX = FactionsMod.CONFIG.EARTH_MAX_X,
            mapMaxZ = FactionsMod.CONFIG.EARTH_MAX_Z;

        return  entityX<mapMinX + 12 &&
                entityX>mapMaxX - 12 &&
                entityZ<mapMinZ + 12 &&
                entityZ>mapMaxZ + 12;
    }

}
