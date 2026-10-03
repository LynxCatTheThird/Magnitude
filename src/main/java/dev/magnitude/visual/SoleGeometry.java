package dev.magnitude.visual;

import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.ArrayList;

/** Rectangular sole clipped to one world cell. Local coordinates avoid large-position cancellation. */
public final class SoleGeometry {
    private SoleGeometry(){}
    public record Point(double x,double z){}
    public static List<Point> clip(double dx,double dz,double halfWidth,double halfLength,double yaw){
        double c=Math.cos(yaw),s=Math.sin(yaw);
        List<Point> polygon=new ArrayList<>(4);
        for(int[] corner:new int[][]{{-1,-1},{-1,1},{1,1},{1,-1}}){
            double x=corner[0]*halfWidth,z=corner[1]*halfLength;
            polygon.add(new Point(dx+c*x-s*z,dz+s*x+c*z));
        }
        polygon=boundary(polygon,0,0,true);polygon=boundary(polygon,0,1,false);
        polygon=boundary(polygon,1,0,true);polygon=boundary(polygon,1,1,false);
        return List.copyOf(polygon);
    }
    private static List<Point> boundary(List<Point> input,int axis,double limit,boolean lower){
        if(input.isEmpty())return input;
        var result=new ArrayList<Point>(8);Point previous=input.getLast();
        for(Point current:input){
            double a=axis==0?previous.x:previous.z,b=axis==0?current.x:current.z;
            boolean insideA=lower?a>=limit:a<=limit,insideB=lower?b>=limit:b<=limit;
            if(insideA!=insideB){double t=(limit-a)/(b-a);result.add(new Point(previous.x+(current.x-previous.x)*t,previous.z+(current.z-previous.z)*t));}
            if(insideB)result.add(current);previous=current;
        }
        return result;
    }
    public static final class Cursor {
        private final int radius;
        private int ring,edge;
        private boolean complete;
        public Cursor(double width,double length){radius=(int)Math.min(30_000_000,Math.ceil(Math.hypot(width,length)+1));}
        public boolean complete(){return complete;}
        public int[] next(){
            int x=0,z=0;
            if(ring>0){int segment=edge/(2*ring),offset=edge%(2*ring);switch(segment){case 0->{x=-ring+offset;z=-ring;}case 1->{x=ring;z=-ring+offset;}case 2->{x=ring-offset;z=ring;}default->{x=-ring;z=ring-offset;}}}
            if(ring==0||++edge>=8L*ring){edge=0;ring++;}
            if(ring>radius)complete=true;
            return new int[]{x,z};
        }
    }
}
