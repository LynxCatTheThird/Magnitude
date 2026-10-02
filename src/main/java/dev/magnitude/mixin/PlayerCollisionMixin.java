package dev.magnitude.mixin;

import dev.magnitude.physics.BodyCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import dev.magnitude.core.EntityState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class PlayerCollisionMixin {
    @Inject(method="move",at=@At("HEAD"),cancellable=true)
    private void magnitude$preflight(MoverType type,Vec3 movement,CallbackInfo info) {
        if((Object)this instanceof Player player && !player.noPhysics && BodyCollision.active(player) && movement.y <= 0 && !BodyCollision.permitted(player,movement)) {
            player.setDeltaMovement(Vec3.ZERO);info.cancel();
        }
    }
    @Inject(method="move",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",shift=At.Shift.AFTER),cancellable=true)
    private void magnitude$denyUnknown(MoverType type,Vec3 movement,CallbackInfo info) {
        if((Object)this instanceof Player player && BodyCollision.active(player) && EntityState.of(player).movementDenied) {
            player.setDeltaMovement(Vec3.ZERO);info.cancel();
        }
    }
    @Inject(method="collide",at=@At("HEAD"),cancellable=true)
    private void magnitude$bodyCollision(Vec3 movement,CallbackInfoReturnable<Vec3> result) {
        if((Object)this instanceof Player player && BodyCollision.active(player))result.setReturnValue(BodyCollision.move(player,movement));
    }
}
