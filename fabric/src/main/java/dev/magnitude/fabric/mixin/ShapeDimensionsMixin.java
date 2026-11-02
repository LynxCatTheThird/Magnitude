/* SPDX-License-Identifier: MIT
 * Copyright (c) 2019 - 2024 Virtuoel
 * Derived from Pehkui Rebuilt LivingEntityMixin#getDimensions and ScaleUtils#getScaledDimensions.
 * Source commit and adaptations: docs/THIRD_PARTY.md. Magnitude changes copyright 2026 contributors.
 */
package dev.magnitude.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.magnitude.fabric.internal.scale.ShapeState;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class ShapeDimensionsMixin {
    @ModifyReturnValue(method = "getDimensions(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/entity/EntityDimensions;", at = @At("RETURN"))
    private EntityDimensions magnitude$shapeDimensions(EntityDimensions original, Pose pose) {
        if (original.fixed() || pose == Pose.SLEEPING) return original;
        var shape = ShapeState.geometry((LivingEntity) (Object) this);
        if (shape.width() == 1 && shape.height() == 1) return original;
        // Vanilla already applied BASE. These factors are relative, not another BASE multiplier.
        return original.scale((float)shape.width(), (float)shape.height())
                .withEyeHeight(original.eyeHeight() * (float)shape.height());
    }
}
