package dev.magnitude.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.*;

/** Native saved height. No block entity, per-tick solver or collision-only surface. */
public final class CompactedSoilBlock extends Block implements SimpleWaterloggedBlock {
    public static final IntegerProperty HEIGHT=IntegerProperty.create("height",8,16);
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    private static final VoxelShape[] SHAPES=new VoxelShape[17];
    static{for(int i=8;i<=16;i++)SHAPES[i]=Block.box(0,0,0,16,i,16);}
    public CompactedSoilBlock(BlockBehaviour.Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(HEIGHT,16).setValue(WATERLOGGED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(HEIGHT,WATERLOGGED);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPES[state.getValue(HEIGHT)];}
    @Override protected VoxelShape getOcclusionShape(BlockState state){return SHAPES[state.getValue(HEIGHT)];}
    @Override protected boolean useShapeForLightOcclusion(BlockState state){return true;}
    @Override public boolean canPlaceLiquid(net.minecraft.world.entity.LivingEntity actor,BlockGetter level,BlockPos pos,BlockState state,Fluid fluid){
        return state.getValue(HEIGHT)<16&&!state.getValue(WATERLOGGED)&&fluid==Fluids.WATER;
    }
    @Override public boolean placeLiquid(LevelAccessor level,BlockPos pos,BlockState state,FluidState fluid){
        return state.getValue(HEIGHT)<16&&SimpleWaterloggedBlock.super.placeLiquid(level,pos,state,fluid);
    }
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction direction,BlockPos neighbor,BlockState neighborState,RandomSource random){
        if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));
        return super.updateShape(state,level,ticks,pos,direction,neighbor,neighborState,random);
    }
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return type==PathComputationType.WATER&&state.getValue(WATERLOGGED);}
}
