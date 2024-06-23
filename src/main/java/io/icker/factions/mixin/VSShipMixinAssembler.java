package io.icker.factions.mixin;

import io.icker.factions.api.persistents.Claim;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.joml.Vector3ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.util.datastructures.DenseBlockPosSet;

import java.util.Iterator;

@Mixin(org.valkyrienskies.eureka.util.ShipAssembler.class)
public class VSShipMixinAssembler {

    @Inject(at = @At("HEAD"), method = "bfs", cancellable = true, remap = false)
    public void bfsOverride(ServerWorld level, BlockPos start, DenseBlockPosSet blocks, kotlin.jvm.functions.Function1<? super BlockState, Boolean> predicate, CallbackInfoReturnable<Boolean> cir){
        Iterator<Vector3ic> iter = blocks.iterator();
        while(iter.hasNext()){
            Vector3ic vec3 = iter.next();
            ChunkPos chunkPos = new ChunkPos(vec3.x()>>4, vec3.z()>>4);
            String dimension = level.getRegistryKey().getValue().toString();
            Claim claim = Claim.get(chunkPos.x, chunkPos.z, dimension);
            if(claim != null) {
                cir.setReturnValue(false);
                return;
            }
        }

    }

}
