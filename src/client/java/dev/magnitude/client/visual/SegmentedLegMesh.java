package dev.magnitude.client.visual;

import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Splits existing skin faces at joints, retaining their UVs instead of stretching a whole leg. */
public final class SegmentedLegMesh {
    public static long renderCalls;
    private record V(float x,float y,float z,float u,float v){}
    private record Face(int segment,List<V> vertices,org.joml.Vector3fc normal){}
    private final List<Face> faces;
    public SegmentedLegMesh(ModelPart.Cube cube){
        var out=new ArrayList<Face>(18);
        for(var polygon:cube.polygons){
            var source=new ArrayList<V>(4);
            for(var v:polygon.vertices())source.add(new V(v.x(),v.y(),v.z(),v.u(),v.v()));
            for(int segment=0;segment<3;segment++) {
                List<V> clipped=source;
                if(segment>0)clipped=clip(clipped,segment==1?6:10,true);
                if(segment<2)clipped=clip(clipped,segment==0?6:10,false);
                if(clipped.size()>=3)out.add(new Face(segment,List.copyOf(clipped),polygon.normal()));
            }
        }
        faces=List.copyOf(out);
    }
    private static List<V> clip(List<V> input,float plane,boolean above){
        var out=new ArrayList<V>(8);if(input.isEmpty())return out;var previous=input.getLast();
        for(var current:input){
            boolean a=above?previous.y>=plane:previous.y<=plane,b=above?current.y>=plane:current.y<=plane;
            if(a!=b){float t=(plane-previous.y)/(current.y-previous.y);out.add(new V(previous.x+(current.x-previous.x)*t,plane,previous.z+(current.z-previous.z)*t,previous.u+(current.u-previous.u)*t,previous.v+(current.v-previous.v)*t));}
            if(b)out.add(current);previous=current;
        }
        return out;
    }
    public void render(PoseStack.Pose pose,VertexConsumer consumer,int light,int overlay,int color,double hip,double knee){
        renderCalls++;
        var normal=new Vector3f();var point=new Vector3f();
        for(var face:faces){
            double angle=face.segment==1?knee:face.segment==2?-hip:0,c=Math.cos(angle),s=Math.sin(angle);
            normal.set(face.normal);float ny=normal.y,nz=normal.z;normal.y=(float)(c*ny-s*nz);normal.z=(float)(s*ny+c*nz);
            pose.transformNormal(normal,normal);
            for(int i=1;i<face.vertices.size()-1;i++) {
                emit(face.vertices.getFirst(),face.segment,c,s,knee,pose,consumer,point,normal,light,overlay,color);
                emit(face.vertices.get(i),face.segment,c,s,knee,pose,consumer,point,normal,light,overlay,color);
                emit(face.vertices.get(i+1),face.segment,c,s,knee,pose,consumer,point,normal,light,overlay,color);
                emit(face.vertices.get(i+1),face.segment,c,s,knee,pose,consumer,point,normal,light,overlay,color);
            }
        }
    }
    private static void emit(V v,int segment,double c,double s,double knee,PoseStack.Pose pose,VertexConsumer consumer,
                              Vector3f point,Vector3f normal,int light,int overlay,int color){
        double y=v.y,z=v.z;
        if(segment==1){y=6+(v.y-6)*c-v.z*s;z=(v.y-6)*s+v.z*c;}
        else if(segment==2){y=6+4*Math.cos(knee)+(v.y-10)*c-v.z*s;z=4*Math.sin(knee)+(v.y-10)*s+v.z*c;}
        pose.pose().transformPosition(v.x/16f,(float)y/16f,(float)z/16f,point);
        consumer.addVertex(point.x,point.y,point.z,color,v.u,v.v,overlay,light,normal.x,normal.y,normal.z);
    }
}
