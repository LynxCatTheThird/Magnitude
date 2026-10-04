package dev.magnitude.physics;

import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import dev.magnitude.visual.SoleGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Bounded surface-only traversal. Does not load chunks, move entities or create terrain. */
public final class FootContacts {
    public static final int CELLS_PER_QUERY=1024, PATCHES_PER_QUERY=512, BOXES_PER_CELL=32;
    private FootContacts(){}
    /** A proposed translation can be a walking step only if a real sole lands on a known surface. */
    public static boolean supportedAt(Player player,Vec3 movement){
        var scale=Dimensions.snapshot(player);var pose=EntityState.of(player).pose;var root=player.position();
        float yaw=player.getYRot();double height=PlayerBody.stanceHeight(player);long revision=WorldObstacles.revision();
        boolean supported=false;
        for(int side:new int[]{-1,1}){
            Vec3 sole=PlayerBody.foot(player,side).add(0,.01,0).add(movement);
            var contact=query(player,sole,scale.bootHalfWidth(),scale.bootHalfLength(),Math.toRadians(player.getYRot()),.0625,.0625);
            if(!contact.complete())return false;
            supported|=contact.supported()&&Math.abs(contact.height()-sole.y)<1e-6;
        }
        return supported&&revision==WorldObstacles.revision()&&root.equals(player.position())
            &&pose.equals(EntityState.of(player).pose)&&yaw==player.getYRot()
            &&scale.modelWidth()==Dimensions.snapshot(player).modelWidth()&&height==PlayerBody.stanceHeight(player);
    }
    public static FootContact capture(Player player,int side){
        var scale=Dimensions.snapshot(player);
        // A contact plane is the sole's actual height, not the block containing its center.
        Vec3 sole=PlayerBody.foot(player,side).add(0,.01,0);
        return query(player,sole,scale.bootHalfWidth(),scale.bootHalfLength(),Math.toRadians(player.getYRot()),.0625,.0625);
    }
    public static FootContact query(Player player,Vec3 sole,double width,double length,double yaw,double below,double above){
        return query(player,sole,width,length,yaw,below,above,true);
    }
    public static FootContact surfaceLayers(Player player,int side){
        var scale=Dimensions.snapshot(player);
        return query(player,PlayerBody.foot(player,side).add(0,.01,0),scale.bootHalfWidth(),scale.bootHalfLength(),Math.toRadians(player.getYRot()),.5,.0625,false);
    }
    private static FootContact query(Player player,Vec3 sole,double width,double length,double yaw,double below,double above,boolean highestOnly){
        var level=player.level();var found=new ArrayList<FootContact.Patch>();
        double highest=Double.NaN,area=0;long revision=1;String reason="complete";
        boolean complete=true;
        if(!Double.isFinite(sole.lengthSqr())||!Double.isFinite(width)||!Double.isFinite(length)||width<=0||length<=0
            ||Math.abs(sole.y)>30_000_000||!Double.isFinite(yaw)||!Double.isFinite(below)||!Double.isFinite(above)||below<0||above<0||below+above>16)
            return result(player,sole,width,length,yaw,highest,area,found,false,"invalid geometry",revision);
        double c=Math.cos(yaw),s=Math.sin(yaw),rx=Math.abs(c)*width+Math.abs(s)*length,rz=Math.abs(s)*width+Math.abs(c)*length;
        double lowX=Math.floor(sole.x-rx),highX=Math.floor(sole.x+rx),lowZ=Math.floor(sole.z-rz),highZ=Math.floor(sole.z+rz);
        double lowY=Math.floor(sole.y-below-1e-7)-1,highY=Math.floor(sole.y+above+1e-7);
        double cells=(highX-lowX+1)*(highZ-lowZ+1)*(highY-lowY+1);
        if(!Double.isFinite(cells)||cells>CELLS_PER_QUERY||Math.abs(lowX)>30_000_000||Math.abs(highX)>30_000_000
            ||Math.abs(lowZ)>30_000_000||Math.abs(highZ)>30_000_000)
            return result(player,sole,width,length,yaw,highest,area,found,false,"surface envelope budget",revision);
        WorldObstacles.prepare(player);var state=EntityState.of(player);
        if(state.physicsCells+cells>LocalProxy.CELLS_PER_TICK||!PhysicsWork.cells((long)cells))
            return result(player,sole,width,length,yaw,highest,area,found,false,"surface traversal budget",revision);
        state.physicsCells+=(int)cells;
        outer:for(int x=(int)lowX;x<=highX;x++)for(int z=(int)lowZ;z<=highZ;z++) {
            if(level.getChunkSource().getChunk(x>>4,z>>4,ChunkStatus.FULL,false)==null) {complete=false;reason="unloaded surface";continue;}
            for(int y=(int)lowY;y<=highY;y++) {
                var pos=new BlockPos(x,y,z);var block=level.getBlockState(pos);
                int id=Block.getId(block);revision=31*revision+pos.asLong();revision=31*revision+id;
                var shape=block.getCollisionShape(level,pos,net.minecraft.world.phys.shapes.CollisionContext.of(player));
                if(shape.isEmpty())continue;
                var boxes=shape.toAabbs();
                if(boxes.size()>BOXES_PER_CELL){complete=false;reason="surface shape budget";break outer;}
                for(var box:boxes) {
                    double height=y+box.maxY;
                    if(height<sole.y-below-1e-7||height>sole.y+above+1e-7||highestOnly&&Double.isFinite(highest)&&height<highest-1e-7)continue;
                    var polygon=SoleGeometry.clipRectangle(sole.x-x,sole.z-z,width,length,yaw,box.minX,box.minZ,box.maxX,box.maxZ);
                    double covered=SoleGeometry.area(polygon);if(covered<=0)continue;
                    if(!Double.isFinite(highest)||height>highest+1e-7){highest=height;if(highestOnly){area=0;found.clear();}}
                    if(found.size()>=PATCHES_PER_QUERY){complete=false;reason="surface patch budget";break outer;}
                    // VoxelShape.toAabbs partitions occupied voxels; equal-height top faces do not overlap.
                    found.add(new FootContact.Patch(pos,id,height,covered,polygon));area+=covered;
                }
            }
        }
        return result(player,sole,width,length,yaw,highest,area,found,complete,reason,revision);
    }
    private static FootContact result(Player p,Vec3 sole,double w,double l,double yaw,double height,double area,
                                      List<FootContact.Patch> patches,boolean complete,String reason,long revision){
        return new FootContact(p.level().dimension().identifier(),sole,w,l,yaw,height,area,List.copyOf(patches),complete,reason,revision);
    }
}
