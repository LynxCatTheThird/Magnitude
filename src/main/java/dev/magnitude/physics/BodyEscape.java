package dev.magnitude.physics;

import dev.magnitude.core.EntityState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Native escape probes use occupied body parts instead of the empty proxy between legs. */
public final class BodyEscape {
    public enum Occupancy { CLEAR, BLOCKED, UNKNOWN }
    private BodyEscape(){}
    public static Occupancy probe(Player player,BlockPos column){
        WorldObstacles.prepare(player);
        var state=EntityState.of(player);var level=player.level();
        var parts=PlayerBody.parts(player,player.position());
        var regions=new java.util.ArrayList<AABB>();
        var probe=new AABB(column.getX(),level.getMinY(),column.getZ(),column.getX()+1,level.getMaxY()+1,column.getZ()+1);
        for(var part:parts)if(part.bounds().intersects(probe))regions.add(part.bounds().intersect(probe));
        if(regions.isEmpty())return Occupancy.CLEAR;
        if(!WorldObstacles.loaded(player,regions))return Occupancy.UNKNOWN;
        long count=regions.stream().mapToLong(LocalProxy::cells).sum();
        if(state.physicsCells+count>LocalProxy.CELLS_PER_TICK||!PhysicsWork.cells(count))return Occupancy.UNKNOWN;
        state.physicsCells+=(int)count;
        var visited=new java.util.HashSet<Integer>();
        var context=CollisionContext.of(player);
        for(var region:regions)for(int y=(int)Math.floor(region.minY);y<=Math.floor(region.maxY);y++){
            if(!visited.add(y))continue;
            var pos=new BlockPos(column.getX(),y,column.getZ());
            var block=level.getBlockState(pos);
            if(!block.isSuffocating(level,pos))continue;
            for(var local:block.getCollisionShape(level,pos,context).toAabbs()){
                if(state.physicsPairs+parts.size()>LocalProxy.PAIRS_PER_TICK||!PhysicsWork.pairs(parts.size()))return Occupancy.UNKNOWN;
                state.physicsPairs+=parts.size();
                var obstacle=local.move(pos);
                for(var part:parts)if(part.intersects(obstacle))return Occupancy.BLOCKED;
            }
        }
        return Occupancy.CLEAR;
    }
}
