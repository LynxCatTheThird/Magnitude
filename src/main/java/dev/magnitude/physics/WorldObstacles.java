package dev.magnitude.physics;

import dev.magnitude.core.EntityState;
import dev.magnitude.interaction.EntityQueries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/** Visits each true body-part envelope, skipping the large empty space between limbs. */
public final class WorldObstacles {
    public record Result(List<AABB> boxes, boolean complete) {}
    private WorldObstacles() {}
    public static List<AABB> regions(List<BodyBox> parts,Vec3 movement,double step) {
        return parts.stream().map(p->p.bounds().expandTowards(movement).expandTowards(0,step,0).inflate(1e-7)).toList();
    }
    public static boolean loaded(Player player,List<AABB> regions) {
        long total=0;
        for(var region:regions) {
            long count=LocalProxy.cells(region);
            if(count>LocalProxy.CELLS_PER_MOVE || total>LocalProxy.CELLS_PER_MOVE-count
                || !LocalProxy.loaded(player.level(),region))return false;
            total+=count;
        }
        return true;
    }
    public static Result query(Player player,List<AABB> regions,boolean entities) {
        var state=EntityState.of(player);
        long now=player.level().getGameTime();
        if(state.physicsTick!=now) {
            state.physicsTick=now;state.physicsCells=0;state.physicsPairs=0;state.proxyFallback=false;
        }
        if(!loaded(player,regions))return new Result(List.of(),false);
        long cells=0;for(var region:regions)cells+=LocalProxy.cells(region);
        if(state.physicsCells+cells>LocalProxy.CELLS_PER_TICK || !PhysicsWork.cells(cells))return new Result(List.of(),false);
        state.physicsCells+=(int)cells;
        var boxes=new LinkedHashSet<AABB>();
        for(var region:regions) {
            for(var shape:player.level().getBlockCollisions(player,region))for(var box:shape.toAabbs()) {
                boxes.add(box);if(boxes.size()>1024)return new Result(List.of(),false);
            }
            if(entities) {
                var result=EntityQueries.query(player.level(),region,player,256);
                if(!result.complete())return new Result(List.of(),false);
                for(var entity:result.entities())if(player.canCollideWith(entity))boxes.add(entity.getBoundingBox());
            }
            var border=player.level().getWorldBorder();
            if(border.isInsideCloseToBorder(player,region))boxes.addAll(border.getCollisionShape().toAabbs());
            if(boxes.size()>1024)return new Result(List.of(),false);
        }
        return new Result(new ArrayList<>(boxes),true);
    }
}
