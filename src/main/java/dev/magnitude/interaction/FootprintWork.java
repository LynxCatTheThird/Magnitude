package dev.magnitude.interaction;

import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
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
    private final boolean deferSupport;
    private int foot, ring, edge, layer;
    private Vec3 checkedRoot;
    private dev.magnitude.physics.BodyPose checkedPose;
    private long checkedScale;
    private float checkedYaw;
    private boolean occupied;
    FootprintWork(ServerPlayer actor,int side,boolean pressure,float hardness) {
        this(actor,side,pressure,hardness,1);
    }
    FootprintWork(ServerPlayer actor,int side,boolean pressure,float hardness,int layers) {
        this.layers=Math.clamp(layers,1,4);
        this.deferSupport=layers==1;
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
    /** Preserve load-bearing terrain until both actual legs leave this contact patch. */
    boolean occupied(ServerPlayer actor) {
        if(!deferSupport)return false;
        var currentRoot=actor.position();var currentPose=EntityState.of(actor).pose;
        long revision=Dimensions.snapshot(actor).revision();float rotation=actor.getYRot();
        if(currentRoot.equals(checkedRoot) && currentPose.equals(checkedPose) && checkedScale==revision && checkedYaw==rotation)return occupied;
        checkedRoot=currentRoot;checkedPose=currentPose;checkedScale=revision;checkedYaw=rotation;occupied=false;
        double c=Math.cos(yaw),s=Math.sin(yaw);
        var legs=PlayerBody.legs(actor,actor.position());
        for(var center:feet) {
            var contact=new dev.magnitude.physics.BodyBox(center.add(0,.01,0),
                new Vec3(width,.025,length),new Vec3(c,0,s),new Vec3(0,1,0),new Vec3(-s,0,c));
            for(var leg:legs)if(leg.intersects(contact.bounds())) {occupied=true;return true;}
        }
        return false;
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
        double c=Math.cos(yaw),s=Math.sin(yaw);
        // Four separating axes for a rotated rectangular sole and a unit block.
        double padding=.5*(Math.abs(c)+Math.abs(s));
        if(Math.abs(dx*c+dz*s)>=width+padding || Math.abs(-dx*s+dz*c)>=length+padding
            || Math.abs(dx)>=Math.abs(c)*width+Math.abs(s)*length+.5
            || Math.abs(dz)>=Math.abs(s)*width+Math.abs(c)*length+.5)return null;
        return pos;
    }
}
