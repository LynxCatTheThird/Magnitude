package dev.magnitude.client.mixin;
import dev.magnitude.client.MagnitudeClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EntityRenderer.class)
public abstract class NameMixin {
    @Inject(method="shouldShowName",at=@At("HEAD"),cancellable=true)
    private void magnitude$names(Entity entity,double distance,CallbackInfoReturnable<Boolean> result){if(MagnitudeClient.hiddenNames)result.setReturnValue(false);}
}
