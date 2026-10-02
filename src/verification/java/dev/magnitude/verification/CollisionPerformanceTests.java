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
