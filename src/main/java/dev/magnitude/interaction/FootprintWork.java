package dev.magnitude.interaction;

import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import dev.magnitude.core.Rules;
import dev.magnitude.physics.PlayerBody;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Constant-memory square-ring cursors over the actual sole, independent of size tiers. */
public final class FootprintWork {
    final float hardness;
    private final Vec3 root;
    private final Vec3[] feet;
    private final double size, yaw, width, length;
    private final boolean pressure;
    private final long expires;
    private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
    private final int radius, layers;
    private int foot, ring, edge, layer;
    FootprintWork(ServerPlayer actor,int side,boolean pressure,float hardness) {
        this(actor,side,pressure,hardness,1);
    }
    FootprintWork(ServerPlayer actor,int side,boolean pressure,float hardness,int layers) {
        this.layers=Math.clamp(layers,1,4);
        var snapshot=Dimensions.snapshot(actor);
        this.root=actor.position();this.size=snapshot.base();this.yaw=Math.toRadians(actor.getYRot());
        this.width=snapshot.bootHalfWidth();this.length=snapshot.bootHalfLength();
        this.pressure=pressure;this.hardness=hardness;this.expires=actor.level().getGameTime()+100;
        this.dimension=actor.level().dimension();
        var selected=new java.util.ArrayList<Vec3>(2);
        for(int sign:new int[]{-1,1})if((side==0 || side==sign) && (PlayerBody.support(actor)&(sign<0?1:2))!=0) {
            var center=PlayerBody.foot(actor,sign);
            if(actor.level().getWorldBorder().isWithinBounds(BlockPos.containing(center)))selected.add(center);
        }
        feet=selected.toArray(Vec3[]::new);
        radius=(int)Math.min(30_000_000,Math.ceil(Math.hypot(width,length)+1));
    }
    boolean valid(ServerPlayer actor) {
        return actor.level().dimension().equals(dimension) && actor.level().getGameTime()<=expires
            && Math.abs(Dimensions.snapshot(actor).base()-size)<=Math.max(1e-6,size*1e-6)
            && actor.position().distanceToSqr(root)<=Math.max(1,size*size)
            && (!pressure || dev.magnitude.Magnitude.settings.standingPressure && EntityState.of(actor).pressureEnabled);
    }
    boolean complete() {return feet.length==0 || layer>=layers;}
    BlockPos next() {
        var center=feet[foot];int x=0,z=0;
        if(ring>0) {
            int segment=edge/(2*ring), offset=edge%(2*ring);
            switch(segment) {
                case 0 -> {x=-ring+offset;z=-ring;}
                case 1 -> {x=ring;z=-ring+offset;}
                case 2 -> {x=ring-offset;z=ring;}
                default -> {x=-ring;z=ring-offset;}
            }
        }
        if(ring==0 || ++edge>=8L*ring) {edge=0;ring++;}
        if(ring>radius) {foot++;ring=0;edge=0;}
        var pos=BlockPos.containing(center).offset(x,-layer,z);
        if(foot>=feet.length) {foot=0;layer++;}
        double dx=pos.getX()+.5-center.x,dz=pos.getZ()+.5-center.z;
        if(!Rules.insideFootprint(dx,dz,yaw,width,length))return null;
        double rx=pos.getX()+.5-root.x,rz=pos.getZ()+.5-root.z;
        if(pressure && (rx*rx+rz*rz<.75*.75 || dx*dx+dz*dz<.55*.55))return null;
        return pos;
    }
}
