/* SPDX-License-Identifier: MIT
 * Copyright (c) 2019 - 2024 Virtuoel
 * Derived from Pehkui Rebuilt EntityRenderStateMixin; see docs/THIRD_PARTY.md.
 */
package dev.magnitude.fabric.mixin.client;

import dev.magnitude.fabric.client.scale.ShapeRenderState;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class ShapeRenderStateMixin implements ShapeRenderState {
    @Unique private float magnitude$width = 1;
    @Unique private float magnitude$height = 1;
    @Override public float magnitude$width() { return magnitude$width; }
    @Override public float magnitude$height() { return magnitude$height; }
    @Override public void magnitude$setShape(float width, float height) {
        magnitude$width = width; magnitude$height = height;
    }
}
