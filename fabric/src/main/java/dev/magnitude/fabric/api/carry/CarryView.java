package dev.magnitude.fabric.api.carry;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** Immutable view; anchor is the last server-verified root position, never an unverified prediction. */
public record CarryView(UUID hostId, UUID targetId, int hostEntityId, int targetEntityId,
                        String dimension, Vec3 anchor, Phase phase, long revision) {
    public enum Phase { HELD, RECOVERY }
}
