package dev.magnitude.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

public final class ReactiveLiquidBlock extends LiquidBlock {
    private final boolean ascending;
    public ReactiveLiquidBlock(FlowingFluid fluid, Properties properties, boolean ascending) { super(fluid, properties); this.ascending = ascending; }
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean moved) {
        super.entityInside(state, level, pos, entity, effects, moved);
        EssenceFluid.contact(level, entity, ascending);
    }
}
