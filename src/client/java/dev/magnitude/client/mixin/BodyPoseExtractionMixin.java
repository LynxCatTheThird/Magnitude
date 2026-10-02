package dev.magnitude.client.mixin;

import dev.magnitude.client.RenderPoseAccess;
import dev.magnitude.core.EntityState;
import dev.magnitude.physics.BodyCollision;
import dev.magnitude.physics.BodyPose;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class BodyPoseExtractionMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",at=@At("TAIL"))
    private void magnitude$extract(Avatar avatar,AvatarRenderState render,float delta,CallbackInfo info) {
        BodyPose pose=null;
        if(avatar instanceof Player player && BodyCollision.active(player)) {
            var state=EntityState.of(player);
            if(state.physicsRevision>0)pose=BodyPose.interpolate(state.previousPose,state.pose,delta);
        }
        ((RenderPoseAccess)render).magnitude$pose(pose);
    }
}
