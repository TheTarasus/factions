package io.icker.factions.mixin;

import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Executor;

@Mixin(ChunkHolder.class)
public abstract class ChunkHolder_tickMixin {

    @Shadow @Final
    ChunkPos pos;
    @Unique
    public long laggedMillis = 0;

    @Inject(at = @At("HEAD"), method = "tick")
    public void startProfiling(ThreadedAnvilChunkStorage chunkStorage, Executor executor, CallbackInfo ci){
        laggedMillis = System.currentTimeMillis();
    }


    @Inject(at = @At("HEAD"), method = "tick")
    public void endProfiling(ThreadedAnvilChunkStorage chunkStorage, Executor executor, CallbackInfo ci){
        long laggedMillisToShow = System.currentTimeMillis() - laggedMillis;
        if(laggedMillisToShow > 5)
            System.out.printf("[CHUNK LAG PROFILER]: Laggy chunk detected! Pos: {%d%d, %d}%n", this.pos.getStartX(), 4, this.pos.getEndX());
        laggedMillis = 0;
    }
}
