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
    private static final class Changes { long revision; }
    private static final ThreadLocal<Changes> CHANGES=ThreadLocal.withInitial(Changes::new);
    /** A shape callback may write directly to a chunk while a query is in progress. */
    public static void changed(){CHANGES.get().revision++;}
    static long revision(){return CHANGES.get().revision;}
    private WorldObstacles() {}
    public static List<AABB> regions(List<BodyBox> parts,Vec3 movement,double step) {
        return parts.stream().map(p->p.bounds().expandTowards(movement).expandTowards(0,step,0).inflate(1e-7)).toList();
    }
    /** Only the upper extension is new work after the ordinary movement query. */
    public static List<AABB> above(List<AABB> regions,double height) {
        return regions.stream().map(r->new AABB(r.minX,r.maxY,r.minZ,r.maxX,r.maxY+height,r.maxZ)).toList();
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
    public static void prepare(Player player) {
        var state=EntityState.of(player);
        long now=player.level().getGameTime();
        if(state.physicsTick!=now) {
            state.physicsTick=now;state.physicsCells=0;state.physicsPairs=0;state.proxyFallback=false;
        }
    }
    private static Result denied(Player player,String reason) {
        EntityState.of(player).contacts.diagnostics.failure=reason;
        return new Result(List.of(),false);
    }
    private static boolean charge(Player player,long cells){
        var state=EntityState.of(player);
        if(state.physicsCells+cells>LocalProxy.CELLS_PER_TICK||!PhysicsWork.cells(cells))return false;
        state.physicsCells+=(int)cells;return true;
    }
    /** Metadata is paid before reading it; only loaded, entirely air sections can be skipped. */
    private static int occupied(Player player,AABB band){
        int minX=net.minecraft.util.Mth.floor(band.minX-1e-7)-1,maxX=net.minecraft.util.Mth.floor(band.maxX+1e-7)+1;
        int minZ=net.minecraft.util.Mth.floor(band.minZ-1e-7)-1,maxZ=net.minecraft.util.Mth.floor(band.maxZ+1e-7)+1;
        int minY=Math.max(player.level().getMinSectionY(),(net.minecraft.util.Mth.floor(band.minY-1e-7)-1)>>4);
        int maxY=Math.min(player.level().getMaxSectionY(),(net.minecraft.util.Mth.floor(band.maxY+1e-7)+1)>>4);
        boolean filled=false;
        for(int x=minX>>4;x<=maxX>>4;x++)for(int z=minZ>>4;z<=maxZ>>4;z++){
            if(!charge(player,1))return -1;
            var chunk=player.level().getChunkSource().getChunk(x,z,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false);
            if(chunk==null)return -2;
            for(int y=minY;y<=maxY;y++){
                if(!charge(player,1))return -1;
                filled|=!chunk.getSection(player.level().getSectionIndexFromSectionY(y)).hasOnlyAir();
            }
        }
        return filled?1:0;
    }
    public static Result query(Player player,List<AABB> regions,boolean entities) {
        prepare(player);
        var state=EntityState.of(player);
        if(!loaded(player,regions))return denied(player,"query envelope budget or unloaded chunks");
        long revision=CHANGES.get().revision;
        int before=state.physicsCells;
        var occupied=new ArrayList<AABB>();
        for(var region:regions){
            double run=Double.NaN;
            for(double low=region.minY;low<region.maxY;){
                double high=Math.min(region.maxY,(Math.floor(low/16)+1)*16);
                var band=new AABB(region.minX,low,region.minZ,region.maxX,high,region.maxZ);
                int status=occupied(player,band);
                if(status<0)return denied(player,status==-2?"unloaded collision neighbor":"section metadata budget");
                if(status==1){if(Double.isNaN(run))run=low;}
                else if(!Double.isNaN(run)){occupied.add(new AABB(region.minX,run,region.minZ,region.maxX,low,region.maxZ));run=Double.NaN;}
                low=high;
            }
            if(!Double.isNaN(run))occupied.add(new AABB(region.minX,run,region.minZ,region.maxX,region.maxY,region.maxZ));
        }
        long cells=0;for(var region:occupied)cells+=LocalProxy.cells(region);
        if(cells+state.physicsCells-before>LocalProxy.CELLS_PER_MOVE||!charge(player,cells))return denied(player,"occupied traversal budget");
        var boxes=new LinkedHashSet<AABB>();
        for(var region:occupied) {
            for(var shape:player.level().getBlockCollisions(player,region))for(var box:shape.toAabbs()) {
                boxes.add(box);if(boxes.size()>1024)return denied(player,"obstacle count budget");
            }
        }
        // Empty block sections do not imply the absence of entities or a world border.
        for(var region:regions){
            if(entities) {
                var result=EntityQueries.query(player.level(),region,player,256);
                if(!result.complete())return denied(player,"entity query budget");
                for(var entity:result.entities())if(player.canCollideWith(entity))boxes.add(entity.getBoundingBox());
            }
            var border=player.level().getWorldBorder();
            if(border.isInsideCloseToBorder(player,region))boxes.addAll(border.getCollisionShape().toAabbs());
            if(boxes.size()>1024)return denied(player,"obstacle count budget");
        }
        if(revision!=CHANGES.get().revision)return denied(player,"world changed during collision query");
        return new Result(new ArrayList<>(boxes),true);
    }
}
