package dev.magnitude.verification.mixin;

import dev.magnitude.verification.SparseObstacleTests;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class SparseReadProbeMixin {
    @Inject(method="getBlockState",at=@At("HEAD"))
    private void magnitude$countSparseReads(BlockPos position,CallbackInfoReturnable<BlockState> info){
        if(SparseObstacleTests.reading)SparseObstacleTests.reads++;
    }
}
