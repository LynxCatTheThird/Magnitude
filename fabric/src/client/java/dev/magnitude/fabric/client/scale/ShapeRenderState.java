/* SPDX-License-Identifier: MIT
 * Copyright (c) 2019 - 2024 Virtuoel
 * Derived from Pehkui Rebuilt PehkuiEntityRenderStateExtensions; see docs/THIRD_PARTY.md.
 */
package dev.magnitude.fabric.client.scale;

public interface ShapeRenderState {
    float magnitude$width();
    float magnitude$height();
    void magnitude$setShape(float width, float height);
}
