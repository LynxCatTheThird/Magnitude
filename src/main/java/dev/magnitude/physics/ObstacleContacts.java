package dev.magnitude.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.ArrayList;
import java.util.List;

/** Loaded, budgeted leg sweep; candidates are actual voxel-shape contacts, never a center sphere. */
public final class ObstacleContacts {
    public record Result(List<BlockPos> blocks, boolean complete) {}
    private ObstacleContacts() {}
    public static Result capture(ServerPlayer player, Vec3 movement) {
        if (!Double.isFinite(movement.lengthSqr()) || movement.lengthSqr()<1e-8)
            return new Result(List.of(),true);
        WorldObstacles.prepare(player);
        var legs = PlayerBody.legs(player,player.position());
        Vec3 sweep = movement;
        var regions=WorldObstacles.regions(legs,sweep,0);
        long cells=0;for(var region:regions)cells+=LocalProxy.cells(region);
        var state=dev.magnitude.core.EntityState.of(player);
        if (!WorldObstacles.loaded(player,regions)
            || state.physicsCells+cells>LocalProxy.CELLS_PER_TICK || !PhysicsWork.cells(cells))
            return new Result(List.of(),false);
        state.physicsCells+=(int)cells;
        var result=new java.util.LinkedHashSet<BlockPos>();
        double solePlane=Math.min(PlayerBody.foot(player,-1).y,PlayerBody.foot(player,1).y)+.01;
        var context=CollisionContext.of(player);
        for(var region:regions)for(var pos:BlockPos.betweenClosed(BlockPos.containing(region.minX,region.minY,region.minZ),
            BlockPos.containing(region.maxX,region.maxY,region.maxZ))) {
            if(pos.getY()<Math.ceil(player.getY()-1e-7))continue;
            var block=player.level().getBlockState(pos);
            var shape=block.getCollisionShape(player.level(),pos,context);
            // Vegetation and cobwebs have contact even when they do not block movement.
            if(shape.isEmpty() && !block.isAir() && block.getFluidState().isEmpty() && !block.hasBlockEntity())
                shape=block.getShape(player.level(),pos,context);
            if(shape.isEmpty())continue;
            boolean hit=false;
            for(var box:shape.toAabbs()) {
                if(state.physicsPairs+legs.size()>LocalProxy.PAIRS_PER_TICK || !PhysicsWork.pairs(legs.size()))return ordered(result,player,false);
                state.physicsPairs+=legs.size();
                var world=box.move(pos.getX(),pos.getY(),pos.getZ());
                // Pelvis coordinates can sit below planted soles after articulation.
                // Supporting surfaces stay in the footprint/soil path, not leg kicks.
                if(world.maxY<=solePlane+1e-6)continue;
                boolean underFoot=false;
                for(int boot=4;boot<6;boot++){
                    var sole=legs.get(boot).bounds().expandTowards(movement.x,0,movement.z);
                    if(world.maxY<=sole.minY+1e-6&&world.maxX>sole.minX&&world.minX<sole.maxX
                        &&world.maxZ>sole.minZ&&world.minZ<sole.maxZ){underFoot=true;break;}
                }
                if(underFoot)continue;
                for(var leg:legs)hit|=leg.intersects(world) || leg.sweep(world,sweep)<1-1e-8;
            }
            if(hit) {
                if(result.size()==256)return ordered(result,player,false);
                result.add(pos.immutable());
            }
        }
        return ordered(result,player,true);
    }
    private static Result ordered(java.util.Collection<BlockPos> result,ServerPlayer player,boolean complete) {
        var sorted=new ArrayList<>(result);
        sorted.sort(java.util.Comparator.comparingDouble(pos->Vec3.atCenterOf(pos).distanceToSqr(player.position())));
        return new Result(List.copyOf(sorted),complete);
    }
}
