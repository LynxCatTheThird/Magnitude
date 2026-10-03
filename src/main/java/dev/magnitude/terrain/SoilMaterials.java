package dev.magnitude.terrain;

import dev.magnitude.content.CompactedSoilBlock;
import dev.magnitude.content.WorldContent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Explicit finite soil backend. Unknown materials are not silently replaced. */
public final class SoilMaterials {
    private SoilMaterials(){}
    public static Block compacted(BlockState state){
        if(state.is(Blocks.DIRT)||state.is(WorldContent.COMPACTED_DIRT))return WorldContent.COMPACTED_DIRT;
        if(state.is(Blocks.GRASS_BLOCK)||state.is(WorldContent.COMPACTED_GRASS))return WorldContent.COMPACTED_GRASS;
        return null;
    }
    public static int height(BlockState state){return state.getBlock() instanceof CompactedSoilBlock?state.getValue(CompactedSoilBlock.HEIGHT):16;}
    /** Pressure/area scaling produces a finite equilibrium; previous compaction cannot reset it. */
    public static int equilibrium(double size,double coverage,double impulse){
        if(!Double.isFinite(size)||!Double.isFinite(coverage)||!Double.isFinite(impulse)||size<=1||coverage<=0)return 16;
        double load=Math.max(0,size-1)*Math.clamp(coverage,0,1)+Math.clamp(impulse,0,64);
        double compression=8*(load/(load+12));
        return 16-Math.clamp((int)Math.floor(compression+1e-9),0,8);
    }
}
