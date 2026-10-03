package dev.magnitude.physics;

import dev.magnitude.core.Dimensions;
import net.minecraft.world.entity.player.Player;

/** Continuous leg reach in world units, independently bounded from visual size. */
public final class StepPolicy {
    private StepPolicy() {}
    public static double height(Player player) {
        double physical = Dimensions.snapshot(player).base();
        return Math.clamp(0.6 * physical, 0, 4.8);
    }
}
