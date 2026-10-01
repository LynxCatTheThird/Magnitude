package dev.magnitude.content;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;

public final class EssenceBasinBlock extends Block {
    private final boolean ascending;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0,0,0,16,2,16), Block.box(0,2,0,2,16,16), Block.box(14,2,0,16,16,16), Block.box(2,2,0,14,16,2), Block.box(2,2,14,14,16,16));
    public EssenceBasinBlock(Properties properties, boolean ascending) { super(properties); this.ascending = ascending; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean moved) {
        if (entity.getY() > pos.getY() + 0.1 && entity.getY() < pos.getY() + 0.9) EssenceFluid.contact(level, entity, ascending);
    }
}
