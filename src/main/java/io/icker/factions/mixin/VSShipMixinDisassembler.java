package io.icker.factions.mixin;

import io.icker.factions.api.persistents.Claim;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.joml.Vector3ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.util.datastructures.DenseBlockPosSet;

import java.util.Iterator;

@Mixin(org.valkyrienskies.eureka.blockentity.ShipHelmBlockEntity.class)
public abstract class VSShipMixinDisassembler {

    @Shadow protected abstract ServerShip getShip();

    @Inject(at = @At("HEAD"), method = "disassemble", cancellable = true, remap = false)
    public void bfsOverride(CallbackInfo ci){
        Ship ship = this.getShip();
        String dimension = ship.getChunkClaimDimension();
        ship.getActiveChunksSet().forEach(((x, z) ->
        {
            Claim claim = Claim.get(x, z, dimension);
            if(claim != null) {
                ci.cancel();
                return;
            }
        }));

    }

}
