package dev.magnitude.mixin;

import dev.magnitude.physics.PacketSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Includes direct chunk writes, not just the public Level.setBlock path. */
@Mixin(LevelChunk.class)
public abstract class ChunkCollisionChangeMixin {
    @Inject(method="setBlockState",at=@At("HEAD"))
    private void magnitude$invalidateStep(BlockPos position,BlockState state,int flags,CallbackInfoReturnable<BlockState> info){
        dev.magnitude.physics.WorldObstacles.changed();
        PacketSteps.changed(((LevelChunk)(Object)this).getLevel());
    }
}
