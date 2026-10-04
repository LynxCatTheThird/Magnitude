package dev.magnitude.verification.mixin;

import dev.magnitude.verification.SparseObstacleTests;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class SparseShapeMutationMixin {
    @Inject(method="getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",at=@At("HEAD"))
    private void magnitude$changeSkippedSection(BlockGetter getter,BlockPos position,CollisionContext context,CallbackInfoReturnable<VoxelShape> info){
        if(SparseObstacleTests.mutateFromShape&&position.equals(SparseObstacleTests.ROOT)&&getter instanceof Level level){
            SparseObstacleTests.mutateFromShape=false;
            level.setBlock(SparseObstacleTests.mutation,Blocks.STONE.defaultBlockState(),2);
        }
    }
}
