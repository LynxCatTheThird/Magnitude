package dev.magnitude.physics;

import dev.magnitude.core.EntityState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import virtuoel.pehkui.api.ScaleTypes;

/** Adaptive root proxy bounded by loaded chunks and a voxel traversal budget, not a fixed scale. */
public final class LocalProxy {
    public static final int CELLS_PER_MOVE=131072;
    public static final int CELLS_PER_TICK=262144;
    public static final int PAIRS_PER_TICK=65536;
    private LocalProxy() {}
    public static long cells(AABB box) {
        if(!Double.isFinite(box.getSize()))return Long.MAX_VALUE;
        double x=Math.ceil(box.maxX)-Math.floor(box.minX)+3,y=Math.ceil(box.maxY)-Math.floor(box.minY)+3,z=Math.ceil(box.maxZ)-Math.floor(box.minZ)+3;
        double count=x*y*z;
        return count>=Long.MAX_VALUE ? Long.MAX_VALUE : (long)count;
    }
    public static boolean loaded(Level level,AABB box) {
        if(cells(box)>CELLS_PER_MOVE)return false;
        int minX=((int)Math.floor(box.minX))>>4,maxX=((int)Math.floor(box.maxX))>>4;
        int minZ=((int)Math.floor(box.minZ))>>4,maxZ=((int)Math.floor(box.maxZ))>>4;
        if((long)(maxX-minX+1)*(maxZ-minZ+1)>16)return false;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(!level.getChunkSource().hasChunk(x,z))return false;
        return true;
    }
    public static void update(ServerPlayer player) {
        var state=EntityState.of(player);
        double size=dev.magnitude.core.Dimensions.snapshot(player).base();
        // Small/ordinary entities already fit inside their current proxy. Movement still
        // validates chunks every time; growing an unused proxy only wastes world queries.
        if(size<=state.proxyLimit)return;
        // Search a dimension budget, independent of the (possibly enormous) BASE value.
        double low=0,high=32;
        for(int i=0;i<12;i++) {
            double middle=(low+high)/2;
            AABB box=box(player,middle).inflate(1).expandTowards(0,StepPolicy.height(player),0);
            if(cells(box)<=CELLS_PER_MOVE/2 && loaded(player.level(),box))low=middle;else high=middle;
        }
        double next=Math.max(1,low);
        // Shrink immediately for missing regions; grow gradually and only into verified free space.
        next=Math.min(next,state.proxyLimit+0.25);
        if(next>state.proxyLimit) {
            long now=player.level().getGameTime();
            if(now<state.nextProxyGrowth)return;
            state.nextProxyGrowth=now+5;
            AABB expansion=box(player,next);
            if(!PhysicsWork.cells(cells(expansion)) || !free(player,expansion))next=state.proxyLimit;
        }
        if(Math.abs(next-state.proxyLimit)>0.001)apply(player,next);
    }
    private static boolean free(ServerPlayer player,AABB box) {
        if(!loaded(player.level(),box) || !player.level().getWorldBorder().isWithinBounds(box))return false;
        int shapes=0;
        for(var shape:player.level().getBlockCollisions(player,box)) {
            if(++shapes>1024 || !shape.isEmpty())return false;
        }
        var entities=dev.magnitude.interaction.EntityQueries.query(player.level(),box,player,256);
        if(!entities.complete())return false;
        for(var entity:entities.entities())if(player.canCollideWith(entity))return false;
        return true;
    }
    private static AABB box(ServerPlayer player,double scale) {
        return new AABB(player.getX()-.3*scale,player.getY(),player.getZ()-.3*scale,player.getX()+.3*scale,player.getY()+1.8*scale,player.getZ()+.3*scale);
    }
    public static void apply(net.minecraft.world.entity.player.Player player,double limit) {
        if(!Double.isFinite(limit) || limit<1 || limit>32)throw new IllegalArgumentException("Invalid local proxy bound");
        EntityState.of(player).proxyLimit=limit;
        ScaleTypes.HITBOX_WIDTH.getScaleData(player).onUpdate();
        ScaleTypes.HITBOX_HEIGHT.getScaleData(player).onUpdate();
        player.refreshDimensions();
        dev.magnitude.core.ScaleService.invalidate(player);
    }
}
