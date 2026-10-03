package dev.magnitude.physics;

import dev.magnitude.core.ScaleSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** A server-owned fact; replaying its sequence cannot repeat its effects. */
public record ContactEvent(long sequence, Type type, ServerPlayer player, long tick, Vec3 position,
                           Vec3 velocity, Vec3 movement, double fallHeight, ScaleSnapshot scale,
                           BodyPose pose, SupportSnapshot support) {
    public enum Type { TAKEOFF, AIRBORNE, LANDING, SIZE_CHANGED, WALKING_STRIDE, SUPPORT_CHANGED, OBSTACLE }
}
