package dev.magnitude.verification;

import dev.magnitude.physics.BodyBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.lang.management.ManagementFactory;
import java.util.Random;
import java.util.function.Consumer;

/** Compare against the former SAT implementation and measure warmed query allocations. */
public final class CollisionPerformanceTests {
    private static volatile double sink;
    private static final int COUNT=10000;
    public static void run(Consumer<String> passed) {
        Random random=new Random(5837);
        BodyBox[] boxes=new BodyBox[COUNT];ReferenceBodyBox[] reference=new ReferenceBodyBox[COUNT];
        AABB[] obstacles=new AABB[COUNT];Vec3[] movement=new Vec3[COUNT];
        for(int i=0;i<COUNT;i++) {
            double yaw=random.nextDouble()*Math.PI*2,pitch=random.nextDouble()*2-1;
            double c=Math.cos(yaw),s=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);
            Vec3 x=new Vec3(c,0,s),y=new Vec3(-s*sp,cp,c*sp),z=new Vec3(-s*cp,-sp,c*cp);
            Vec3 center=new Vec3(random.nextDouble()*4,random.nextDouble()*4,random.nextDouble()*4);
            Vec3 half=new Vec3(.01+random.nextDouble()*3,.01+random.nextDouble()*3,.01+random.nextDouble()*3);
            boxes[i]=new BodyBox(center,half,x,y,z);reference[i]=new ReferenceBodyBox(center,half,x,y,z);
            double ox=random.nextDouble()*6,oy=random.nextDouble()*6,oz=random.nextDouble()*6;
            obstacles[i]=new AABB(ox,oy,oz,ox+1,oy+1,oz+1);
            movement[i]=new Vec3(random.nextDouble()*8-4,random.nextDouble()*8-4,random.nextDouble()*8-4);
            compare(boxes[i],reference[i],obstacles[i],movement[i]);
            compare(boxes[i].move(movement[i]),reference[i].move(movement[i]),obstacles[i],movement[i].scale(-1));
        }
        passed.accept("cached SAT matches original sweep and overlap across 10000 rotated fixtures and translations");
        broadPhase(passed);
        for(int i=0;i<8;i++){measure(boxes,reference,obstacles,movement,false);measure(boxes,reference,obstacles,movement,true);}
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())return;
        bean.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().threadId();
        long before=bean.getThreadAllocatedBytes(id),start=System.nanoTime();
        measure(boxes,reference,obstacles,movement,false);
        long oldTime=System.nanoTime()-start,oldBytes=bean.getThreadAllocatedBytes(id)-before;
        before=bean.getThreadAllocatedBytes(id);start=System.nanoTime();
        measure(boxes,reference,obstacles,movement,true);
        long newTime=System.nanoTime()-start,newBytes=bean.getThreadAllocatedBytes(id)-before;
        System.out.println("SAT BENCHMARK: pairs="+COUNT+" baselineBytes="+oldBytes+" optimizedBytes="+newBytes+" baselineNs="+oldTime+" optimizedNs="+newTime);
        if(newBytes>=oldBytes/4)throw new AssertionError("cached SAT must remove at least 75% of warmed query allocations");
        passed.accept("cached SAT removes at least 75 percent of repeated query allocations");
    }
    @SuppressWarnings("unchecked")
    private static void broadPhase(Consumer<String> passed){
        try{
            var select=dev.magnitude.physics.BodyCollision.class.getDeclaredMethod("candidates",java.util.List.class,java.util.List.class,Vec3.class,double.class);select.setAccessible(true);
            var clip=dev.magnitude.physics.BodyCollision.class.getDeclaredMethod("clip",java.util.List.class,java.util.List.class,Vec3.class);clip.setAccessible(true);
            var random=new Random(20261004);long full=0,culled=0;
            for(int fixture=0;fixture<2000;fixture++){
                var parts=new java.util.ArrayList<BodyBox>();var obstacles=new java.util.ArrayList<AABB>();
                for(int i=0;i<3;i++){
                    double yaw=random.nextDouble()*Math.PI*2,pitch=random.nextDouble()*2-1,c=Math.cos(yaw),s=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);
                    parts.add(new BodyBox(new Vec3(random.nextDouble()*6-3,random.nextDouble()*6-3,random.nextDouble()*6-3),new Vec3(.1+random.nextDouble()*2,.1+random.nextDouble()*2,.1+random.nextDouble()*2),new Vec3(c,0,s),new Vec3(-s*sp,cp,c*sp),new Vec3(-s*cp,-sp,c*cp)));
                }
                for(int i=0;i<32;i++){
                    double x=random.nextDouble()*30-15,y=random.nextDouble()*30-15,z=random.nextDouble()*30-15;
                    obstacles.add(new AABB(x,y,z,x+.1+random.nextDouble()*3,y+.1+random.nextDouble()*3,z+.1+random.nextDouble()*3));
                }
                var movement=new Vec3(random.nextDouble()*8-4,random.nextDouble()*8-4,random.nextDouble()*8-4);double step=random.nextDouble()*4.8;
                var selected=(java.util.List<java.util.List<AABB>>)select.invoke(null,parts,obstacles,movement,step);
                var all=new java.util.ArrayList<java.util.List<AABB>>();for(int i=0;i<parts.size();i++)all.add(obstacles);
                full+=(long)parts.size()*obstacles.size();for(var nearby:selected)culled+=nearby.size();
                equivalent((Vec3)clip.invoke(null,parts,selected,movement),referenceClip(parts,obstacles,movement));
                var up=new Vec3(0,step,0);var actualUp=(Vec3)clip.invoke(null,parts,selected,up);var expectedUp=referenceClip(parts,obstacles,up);equivalent(actualUp,expectedUp);
                var raised=shift(parts,expectedUp);var across=new Vec3(movement.x,0,movement.z);
                var expectedAcross=referenceClip(raised,obstacles,across);equivalent((Vec3)clip.invoke(null,raised,selected,across),expectedAcross);
                var elevated=shift(raised,expectedAcross);var down=new Vec3(0,movement.y-expectedUp.y,0);
                equivalent((Vec3)clip.invoke(null,elevated,selected,down),referenceClip(elevated,obstacles,down));
            }
            if(culled>=full/4)throw new AssertionError("broad phase did not reduce sparse exact pairs");
            System.out.println("BROAD PHASE: full="+full+" selected="+culled);
            passed.accept("broad phase matches brute-force reference in 2000 rotated movement and step fixtures");
            passed.accept("broad phase removes at least 75 percent of exact pairs in sparse fixtures");
        }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
    }
    private static java.util.List<BodyBox> shift(java.util.List<BodyBox> parts,Vec3 movement){return parts.stream().map(p->p.move(movement)).toList();}
    private static void equivalent(Vec3 actual,Vec3 expected){if(actual.subtract(expected).lengthSqr()>1e-18)throw new AssertionError("broad phase omitted a relevant contact: "+actual+" versus "+expected);}
    private static Vec3 referenceClip(java.util.List<BodyBox> parts,java.util.List<AABB> obstacles,Vec3 movement){
        Vec3 result=new Vec3(0,movement.y*referenceAxis(parts,obstacles,new Vec3(0,movement.y,0)),0);
        var first=Math.abs(movement.x)<Math.abs(movement.z)?new Vec3(0,0,movement.z):new Vec3(movement.x,0,0);
        result=result.add(first.scale(referenceAxis(shift(parts,result),obstacles,first)));
        var second=Math.abs(movement.x)<Math.abs(movement.z)?new Vec3(movement.x,0,0):new Vec3(0,0,movement.z);
        return result.add(second.scale(referenceAxis(shift(parts,result),obstacles,second)));
    }
    private static double referenceAxis(java.util.List<BodyBox> parts,java.util.List<AABB> obstacles,Vec3 movement){
        if(movement.lengthSqr()==0)return 1;double fraction=1;
        for(var p:parts){var original=new ReferenceBodyBox(p.center(),p.half(),p.x(),p.y(),p.z());for(var obstacle:obstacles)fraction=Math.min(fraction,original.sweep(obstacle,movement));}
        return fraction;
    }
    private static void compare(BodyBox box,ReferenceBodyBox reference,AABB obstacle,Vec3 movement) {
        if(box.intersects(obstacle)!=reference.intersects(obstacle) || Math.abs(box.sweep(obstacle,movement)-reference.sweep(obstacle,movement))>1e-12)
            throw new AssertionError("SAT optimization changed collision result");
    }
    private static void measure(BodyBox[] boxes,ReferenceBodyBox[] reference,AABB[] obstacles,Vec3[] movement,boolean optimized) {
        double sum=0;
        for(int i=0;i<COUNT;i++)sum+=optimized ? boxes[i].sweep(obstacles[i],movement[i])+(boxes[i].intersects(obstacles[i])?1:0)
            : reference[i].sweep(obstacles[i],movement[i])+(reference[i].intersects(obstacles[i])?1:0);
        sink=sum;
    }
}
