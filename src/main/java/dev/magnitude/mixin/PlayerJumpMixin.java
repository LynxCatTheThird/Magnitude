package dev.magnitude.mixin;

import dev.magnitude.interaction.Interactions;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class PlayerJumpMixin {
    @Inject(method="jumpFromGround", at=@At("HEAD"))
    private void magnitude$jumpImpact(CallbackInfo info) { Interactions.jump((ServerPlayer)(Object)this); }
    @Inject(method="jumpFromGround", at=@At("RETURN"))
    private void magnitude$limitLaunch(CallbackInfo info) { Interactions.limitJumpVelocity((ServerPlayer)(Object)this); }
}
