package dev.magnitude.client.mixin;

import dev.magnitude.client.RenderPoseAccess;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class BodyAnimationMixin {
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",at=@At("TAIL"))
    private void magnitude$animate(AvatarRenderState state,CallbackInfo info) {
        var pose=((RenderPoseAccess)state).magnitude$pose();
        var model=(PlayerModel)(Object)this;
        for(var part:new net.minecraft.client.model.geom.ModelPart[]{model.leftLeg,model.leftPants})
            ((dev.magnitude.client.LegModelAccess)(Object)part).magnitude$leg(pose!=null,pose==null?0:pose.leftLeg(),pose==null?0:pose.leftKnee());
        for(var part:new net.minecraft.client.model.geom.ModelPart[]{model.rightLeg,model.rightPants})
            ((dev.magnitude.client.LegModelAccess)(Object)part).magnitude$leg(pose!=null,pose==null?0:pose.rightLeg(),pose==null?0:pose.rightKnee());
        if(pose==null)return;
        model.leftLeg.xRot=(float)pose.leftLeg();model.rightLeg.xRot=(float)pose.rightLeg();
        model.leftPants.xRot=model.leftLeg.xRot;model.rightPants.xRot=model.rightLeg.xRot;
        // Item-use and attack poses retain their normal animation; leg support stays authoritative.
        if(!state.isUsingItem && state.swingAnimation==0 && state.leftArmPose==net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY && state.rightArmPose==net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY) {
            model.leftArm.xRot=(float)pose.leftArm();model.rightArm.xRot=(float)pose.rightArm();
            model.leftSleeve.xRot=model.leftArm.xRot;model.rightSleeve.xRot=model.rightArm.xRot;
        }
    }
}
