package dev.magnitude.fabric.mixin;

import dev.magnitude.fabric.internal.net.CarryPositionHooks;
import dev.magnitude.fabric.internal.carry.CarryRuntime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuses native passenger synchronization, but never the default head-top seat. */
@Mixin(LivingEntity.class)
public abstract class CarryPassengerMixin {
    @Inject(method = "getPassengerRidingPosition", at = @At("HEAD"), cancellable = true)
    private void magnitude$confirmedHandAnchor(Entity passenger, CallbackInfoReturnable<Vec3> result) {
        Entity host = (Entity) (Object) this;
        Vec3 root = CarryPositionHooks.confirmedRoot(host, passenger);
        // positionRider subtracts this attachment point. Convert confirmed root back to its seat convention.
        if (root != null) result.setReturnValue(root.add(passenger.getVehicleAttachmentPoint(host)));
    }
    @Inject(method = "dismountVehicle", at = @At("HEAD"), cancellable = true)
    private void magnitude$controlledDismount(Entity oldVehicle, CallbackInfo info) {
        Entity self = (Entity) (Object) this;
        // Native dismount has already removed the transport link. The service observes
        // that transition and performs its own bounded safe release on this tick.
        if (!self.level().isClientSide() && CarryRuntime.isControlled(self)) info.cancel();
    }

}
