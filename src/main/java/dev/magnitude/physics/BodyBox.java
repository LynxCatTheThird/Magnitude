package dev.magnitude.physics;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Oriented box with continuous SAT against an axis-aligned obstacle. */
public final class BodyBox {
    private final Vec3 center, half, x, y, z;
    // Immutable orientation data shared by translated boxes. Each row contains an axis
    // and the body's projected radius; SAT queries allocate no temporary axes/vectors.
    private double[][] axes;
    public BodyBox(Vec3 center, Vec3 half, Vec3 x, Vec3 y, Vec3 z) {
        this(center,half,x,y,z,null);
    }
    private BodyBox(Vec3 center, Vec3 half, Vec3 x, Vec3 y, Vec3 z,double[][] axes) {
        this.center=center;this.half=half;this.x=x;this.y=y;this.z=z;this.axes=axes;
    }
    public Vec3 center(){return center;}
    public Vec3 half(){return half;}
    public Vec3 x(){return x;}
    public Vec3 y(){return y;}
    public Vec3 z(){return z;}
    public BodyBox move(Vec3 delta) { return new BodyBox(center.add(delta),half,x,y,z,axes()); }
    private double[][] axes() {
        if(axes==null)axes=axes(half,x,y,z);
        return axes;
    }
    public AABB bounds() {
        double rx=Math.abs(x.x)*half.x+Math.abs(y.x)*half.y+Math.abs(z.x)*half.z;
        double ry=Math.abs(x.y)*half.x+Math.abs(y.y)*half.y+Math.abs(z.y)*half.z;
        double rz=Math.abs(x.z)*half.x+Math.abs(y.z)*half.y+Math.abs(z.z)*half.z;
        return new AABB(center.x-rx,center.y-ry,center.z-rz,center.x+rx,center.y+ry,center.z+rz);
    }
    /** Covers destination volume outside this box for a fixed-orientation translation. */
    public java.util.List<AABB> enteredRegions(Vec3 delta){
        var result=new java.util.ArrayList<AABB>(3);
        Vec3[] local={x,y,z};double[] extents={half.x,half.y,half.z};
        for(int i=0;i<3;i++){
            double travel=delta.dot(local[i]);
            if(travel==0)continue;
            double thickness=Math.min(2*extents[i],Math.abs(travel));
            double[] reduced=extents.clone();reduced[i]=thickness/2;
            Vec3 middle=center.add(delta).add(local[i].scale(Math.copySign(extents[i]-thickness/2,travel)));
            result.add(new BodyBox(middle,new Vec3(reduced[0],reduced[1],reduced[2]),x,y,z).bounds().inflate(1e-7));
        }
        return result;
    }
    public boolean intersects(AABB obstacle) {
        double sx=center.x-(obstacle.minX+obstacle.maxX)/2,sy=center.y-(obstacle.minY+obstacle.maxY)/2,sz=center.z-(obstacle.minZ+obstacle.maxZ)/2;
        double ex=obstacle.getXsize()/2,ey=obstacle.getYsize()/2,ez=obstacle.getZsize()/2;
        for(double[] axis:axes()) {
            double radius=axis[3]+Math.abs(axis[0])*ex+Math.abs(axis[1])*ey+Math.abs(axis[2])*ez;
            if(Math.abs(sx*axis[0]+sy*axis[1]+sz*axis[2])>=radius-1e-9)return false;
        }
        return true;
    }
    private static double[][] axes(Vec3 half,Vec3 x,Vec3 y,Vec3 z) {
        Vec3[] world={new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1)};
        Vec3[] candidates=new Vec3[15];int n=0;
        for(Vec3 axis:world)candidates[n++]=axis;
        for(Vec3 axis:new Vec3[]{x,y,z})candidates[n++]=axis;
        for(Vec3 local:new Vec3[]{x,y,z})for(Vec3 axis:world)candidates[n++]=local.cross(axis);
        double[][] result=new double[15][];n=0;
        for(Vec3 axis:candidates) {
            if(axis.lengthSqr()<1e-16)continue;
            result[n++]=new double[]{axis.x,axis.y,axis.z,Math.abs(axis.dot(x))*half.x+Math.abs(axis.dot(y))*half.y+Math.abs(axis.dot(z))*half.z};
        }
        return java.util.Arrays.copyOf(result,n);
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
        double sx=center.x-(obstacle.minX+obstacle.maxX)/2,sy=center.y-(obstacle.minY+obstacle.maxY)/2,sz=center.z-(obstacle.minZ+obstacle.maxZ)/2;
        double ex=obstacle.getXsize()/2,ey=obstacle.getYsize()/2,ez=obstacle.getZsize()/2;
        double enter=0,leave=1;boolean penetrating=true;
        for(double[] axis:axes()) {
            double radius=axis[3]+Math.abs(axis[0])*ex+Math.abs(axis[1])*ey+Math.abs(axis[2])*ez;
            double distance=sx*axis[0]+sy*axis[1]+sz*axis[2],velocity=delta.x*axis[0]+delta.y*axis[1]+delta.z*axis[2];
            if(Math.abs(distance)>=radius-1e-8)penetrating=false;
            if(Math.abs(velocity)<1e-12) { if(Math.abs(distance)>=radius-1e-8)return 1;continue; }
            double a=(-radius-distance)/velocity,b=(radius-distance)/velocity;
            enter=Math.max(enter,Math.min(a,b));leave=Math.min(leave,Math.max(a,b));
            if(enter>=leave-1e-10)return 1;
        }
        return penetrating ? 1 : Math.clamp(enter,0,1);
    }
}
