package io.icker.factions.mixin;

import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import io.icker.factions.api.persistents.Claim;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBreakingMovementBehaviour.class)
public class BlockBreakingMovementBehaviourMixin {
    @Inject(at = @At("HEAD"), method = "canBreak", cancellable = true, remap = false)
    protected void getBlockBreakingSpeed(World world, BlockPos breakingPos, BlockState state, CallbackInfoReturnable<Boolean> ci) {
        BlockPos pos = new BlockPos(breakingPos);
        String dimension = world.getRegistryKey().getValue().toString();
        Claim claim = Claim.get(pos.getX()>>4, pos.getZ()>>4, dimension);
        if(claim == null) return;
        if(!claim.isCreate()) ci.setReturnValue(false);
    }
}
