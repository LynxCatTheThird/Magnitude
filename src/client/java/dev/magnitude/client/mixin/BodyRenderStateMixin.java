package dev.magnitude.client.mixin;

import dev.magnitude.client.RenderPoseAccess;
import dev.magnitude.physics.BodyPose;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class BodyRenderStateMixin implements RenderPoseAccess {
    @Unique private BodyPose magnitude$pose;
    public BodyPose magnitude$pose(){return magnitude$pose;}
    public void magnitude$pose(BodyPose pose){magnitude$pose=pose;}
}
