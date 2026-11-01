package dev.magnitude.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.magnitude.fabric.internal.carry.CarryRuntime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Allows only an already-authorized, journaled carry installation to use player transport.
 * Does not change EntityType serialization, saved passenger trees or ordinary /ride behavior.
 */
@Mixin(Entity.class)
public abstract class CarryTransportMixin {
    @WrapOperation(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z"))
    private boolean magnitude$authorizedPlayerCarrier(EntityType<?> vehicleType, Operation<Boolean> original,
                                                       @Local(argsOnly = true) Entity vehicle) {
        return original.call(vehicleType)
                || CarryRuntime.permitsPlayerTransport((Entity) (Object) this, vehicle);
    }
}
