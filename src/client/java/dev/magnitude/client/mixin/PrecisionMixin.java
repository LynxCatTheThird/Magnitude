package dev.magnitude.client.mixin;
import dev.magnitude.client.MagnitudeClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
@Mixin(MouseHandler.class)
public abstract class PrecisionMixin {
    @ModifyArg(method="turnPlayer",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"),index=0)
    private double magnitude$horizontal(double delta){return adjusted(delta);}
    @ModifyArg(method="turnPlayer",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"),index=1)
    private double magnitude$vertical(double delta){return adjusted(delta);}
    private static double adjusted(double delta){return MagnitudeClient.zoom?delta*MagnitudeClient.sensitivity/MagnitudeClient.magnification:delta;}
}
