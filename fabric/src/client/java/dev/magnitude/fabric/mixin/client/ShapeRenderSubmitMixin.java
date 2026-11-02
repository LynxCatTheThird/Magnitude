/* SPDX-License-Identifier: MIT
 * Copyright (c) 2019 - 2024 Virtuoel
 * Derived from Pehkui Rebuilt EntityRenderManagerMixin#render; see docs/THIRD_PARTY.md.
 */
package dev.magnitude.fabric.mixin.client;

import dev.magnitude.fabric.client.scale.ShapeRenderState;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public class ShapeRenderSubmitMixin {
    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private <S extends EntityRenderState> void magnitude$submitShape(EntityRenderer<?, ? super S> renderer,
            S state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera, Operation<Void> original) {
        var shape = (ShapeRenderState)state;
        matrices.pushPose();
        try {
            // Relative width/height only: LivingEntityRenderer applies the vanilla base itself.
            matrices.scale(shape.magnitude$width(), shape.magnitude$height(), shape.magnitude$width());
            original.call(renderer, state, matrices, queue, camera);
        } finally { matrices.popPose(); }
    }
}
