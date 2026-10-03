package dev.magnitude.client.mixin;

import dev.magnitude.client.LegModelAccess;
import dev.magnitude.client.visual.SegmentedLegMesh;
import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ModelPart.class)
public abstract class LegModelMixin implements LegModelAccess {
    @Unique private boolean magnitude$leg;
    @Unique private double magnitude$hip,magnitude$knee;
    @Unique private final java.util.IdentityHashMap<ModelPart.Cube,SegmentedLegMesh> magnitude$meshes=new java.util.IdentityHashMap<>();
    public void magnitude$leg(boolean enabled,double hip,double knee){magnitude$leg=enabled;magnitude$hip=hip;magnitude$knee=knee;}
    @Redirect(method="compile",at=@At(value="INVOKE",target="Lnet/minecraft/client/model/geom/ModelPart$Cube;compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void magnitude$compileLeg(ModelPart.Cube cube,PoseStack.Pose pose,VertexConsumer consumer,int light,int overlay,int color){
        if(!magnitude$leg||Math.abs(magnitude$hip)+Math.abs(magnitude$knee)<1e-7){cube.compile(pose,consumer,light,overlay,color);return;}
        var mesh=magnitude$meshes.get(cube);
        if(mesh==null){if(magnitude$meshes.size()>=64){cube.compile(pose,consumer,light,overlay,color);return;}mesh=new SegmentedLegMesh(cube);magnitude$meshes.put(cube,mesh);}
        mesh.render(pose,consumer,light,overlay,color,magnitude$hip,magnitude$knee);
    }
}
