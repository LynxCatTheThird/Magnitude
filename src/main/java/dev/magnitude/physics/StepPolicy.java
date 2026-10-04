package dev.magnitude.physics;

import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Continuous anatomical reach; traversal capacity bounds the extra step envelope. */
public final class StepPolicy {
    private StepPolicy() {}
    public static double height(Player player) {return Math.max(0,0.6*Dimensions.snapshot(player).base());}
    public static double height(Player player,List<BodyBox> parts,Vec3 movement){
        if(movement.horizontalDistanceSqr()==0)return 0;
        long limit=Math.min(LocalProxy.CELLS_PER_MOVE,Math.min(PhysicsWork.cellsRemaining(),LocalProxy.CELLS_PER_TICK-EntityState.of(player).physicsCells));
        double reach=height(player);
        var regions=WorldObstacles.regions(parts,movement,0);
        if(cells(regions,reach)<=limit)return reach;
        if(cells(regions,0)>limit)return 0;
        double low=0,high=reach;
        for(int i=0;i<24;i++){double middle=(low+high)*.5;if(cells(regions,middle)<=limit)low=middle;else high=middle;}
        return low;
    }
    private static long cells(List<AABB> regions,double step){
        // The caller has already paid for the ordinary envelope. Match the actual
        // additional query, including its voxel margins, rather than charging it twice.
        long sum=0;
        for(var region:WorldObstacles.above(regions,step)){
            long count=LocalProxy.cells(region);
            if(count>LocalProxy.CELLS_PER_MOVE-sum)return Long.MAX_VALUE;
            sum+=count;
        }
        return sum;
    }
}
