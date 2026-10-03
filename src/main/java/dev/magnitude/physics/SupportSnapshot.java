package dev.magnitude.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

/** Three loaded support probes, never a world-sized search. */
public record SupportSnapshot(BlockPos left, int leftState, BlockPos right, int rightState,
                              BlockPos center, int centerState, boolean complete) {
    public static SupportSnapshot capture(ServerPlayer player) {
        BlockPos left = BlockPos.containing(PlayerBody.foot(player, -1));
        BlockPos right = BlockPos.containing(PlayerBody.foot(player, 1));
        BlockPos center = BlockPos.containing(player.position().add(0, -0.01, 0));
        boolean known = player.level().hasChunkAt(left) && player.level().hasChunkAt(right)
            && player.level().hasChunkAt(center) && PhysicsWork.cells(3);
        return new SupportSnapshot(left, known ? Block.getId(player.level().getBlockState(left)) : -1,
            right, known ? Block.getId(player.level().getBlockState(right)) : -1,
            center, known ? Block.getId(player.level().getBlockState(center)) : -1, known);
    }
}
