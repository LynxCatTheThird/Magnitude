package dev.magnitude.client.mixin;
import dev.magnitude.client.MagnitudeClient;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Camera.class)
public abstract class ObservationMixin {
    @Inject(method="calculateFov",at=@At("RETURN"),cancellable=true)
    private void magnitude$zoom(float partialTick,CallbackInfoReturnable<Float> result){
        if(MagnitudeClient.zoom)result.setReturnValue((float)Math.toDegrees(2*Math.atan(Math.tan(Math.toRadians(result.getReturnValueF())/2)/MagnitudeClient.magnification)));
    }
}
