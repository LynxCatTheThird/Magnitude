package dev.magnitude.verification;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Frozen pre-optimization SAT oracle for equivalence and allocation measurements. */
public record ReferenceBodyBox(Vec3 center, Vec3 half, Vec3 x, Vec3 y, Vec3 z) {
    public ReferenceBodyBox move(Vec3 delta) { return new ReferenceBodyBox(center.add(delta),half,x,y,z); }
    public AABB bounds() {
        double rx=Math.abs(x.x)*half.x+Math.abs(y.x)*half.y+Math.abs(z.x)*half.z;
        double ry=Math.abs(x.y)*half.x+Math.abs(y.y)*half.y+Math.abs(z.y)*half.z;
        double rz=Math.abs(x.z)*half.x+Math.abs(y.z)*half.y+Math.abs(z.z)*half.z;
        return new AABB(center.x-rx,center.y-ry,center.z-rz,center.x+rx,center.y+ry,center.z+rz);
    }
    public boolean intersects(AABB obstacle) {
        Vec3 separation=center.subtract(obstacle.getCenter());
        Vec3 extent=new Vec3(obstacle.getXsize()/2,obstacle.getYsize()/2,obstacle.getZsize()/2);
        for(Vec3 axis:axes()) {
            if(axis.lengthSqr()<1e-16)continue;
            double radius=Math.abs(axis.dot(x))*half.x+Math.abs(axis.dot(y))*half.y+Math.abs(axis.dot(z))*half.z
                +Math.abs(axis.x)*extent.x+Math.abs(axis.y)*extent.y+Math.abs(axis.z)*extent.z;
            if(Math.abs(separation.dot(axis))>=radius-1e-9)return false;
        }
        return true;
    }
    private List<Vec3> axes() {
        Vec3[] world={new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1)};
        List<Vec3> axes=new ArrayList<>(15);axes.addAll(List.of(world));axes.addAll(List.of(x,y,z));
        for(Vec3 local:List.of(x,y,z))for(Vec3 axis:world)axes.add(local.cross(axis));
        return axes;
    }
    public java.util.Optional<Vec3> ray(Vec3 start,Vec3 end) {
        Vec3 a=start.subtract(center),b=end.subtract(center);
        var local=new AABB(-half.x,-half.y,-half.z,half.x,half.y,half.z);
        Vec3 origin=new Vec3(a.dot(x),a.dot(y),a.dot(z));
        if(local.contains(origin))return java.util.Optional.of(start);
        var hit=local.clip(origin,new Vec3(b.dot(x),b.dot(y),b.dot(z)));
        return hit.map(point->center.add(x.scale(point.x)).add(y.scale(point.y)).add(z.scale(point.z)));
    }
    /** First contact fraction; pre-existing penetration may move out without trapping the player. */
    public double sweep(AABB obstacle, Vec3 delta) {
        Vec3 separation=center.subtract(obstacle.getCenter());
        Vec3 extent=new Vec3(obstacle.getXsize()/2,obstacle.getYsize()/2,obstacle.getZsize()/2);
        Vec3[] world={new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1)};
        List<Vec3> axes=new ArrayList<>(15);axes.addAll(List.of(world));axes.addAll(List.of(x,y,z));
        for(Vec3 local:List.of(x,y,z)) for(Vec3 axis:world) axes.add(local.cross(axis));
        double enter=0,leave=1;boolean penetrating=true;
        for(Vec3 axis:axes) {
            if(axis.lengthSqr()<1e-16)continue;
            double radius=Math.abs(axis.dot(x))*half.x+Math.abs(axis.dot(y))*half.y+Math.abs(axis.dot(z))*half.z
                +Math.abs(axis.x)*extent.x+Math.abs(axis.y)*extent.y+Math.abs(axis.z)*extent.z;
            double distance=separation.dot(axis),velocity=delta.dot(axis);
            if(Math.abs(distance)>=radius-1e-8)penetrating=false;
            if(Math.abs(velocity)<1e-12) { if(Math.abs(distance)>=radius-1e-8)return 1;continue; }
            double a=(-radius-distance)/velocity,b=(radius-distance)/velocity;
            enter=Math.max(enter,Math.min(a,b));leave=Math.min(leave,Math.max(a,b));
            if(enter>=leave-1e-10)return 1;
        }
        return penetrating ? 1 : Math.clamp(enter,0,1);
    }
}
