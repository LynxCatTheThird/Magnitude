/* SPDX-License-Identifier: MIT
 * Copyright (c) 2019 - 2024 Virtuoel
 * Derived from Pehkui Rebuilt EntityRendererMixin#updateRenderState; see docs/THIRD_PARTY.md.
 */
package dev.magnitude.fabric.mixin.client;

import dev.magnitude.fabric.client.scale.ShapeRenderState;

import dev.magnitude.fabric.internal.scale.ShapeState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class ShapeRenderExtractMixin<T extends Entity, S extends EntityRenderState> {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void magnitude$extractShape(T entity, S state, float tickDelta, CallbackInfo info) {
        var view = (ShapeRenderState)state;
        if (entity instanceof LivingEntity && entity.getPose() != Pose.SLEEPING) {
            var shape = ShapeState.confirmed(entity);
            view.magnitude$setShape((float)shape.width(), (float)shape.height());
        } else view.magnitude$setShape(1, 1);
    }
}
