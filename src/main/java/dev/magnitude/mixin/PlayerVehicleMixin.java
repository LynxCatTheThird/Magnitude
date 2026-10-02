package dev.magnitude.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.magnitude.interaction.PlayerMounts;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class PlayerVehicleMixin {
    @WrapOperation(method="startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/EntityType;canSerialize()Z"))
    private boolean magnitude$playerVehicle(EntityType<?> type, Operation<Boolean> original,
                                            @Local(argsOnly=true) Entity vehicle) {
        return original.call(type) || PlayerMounts.authorized((Entity)(Object)this, vehicle);
    }
}
