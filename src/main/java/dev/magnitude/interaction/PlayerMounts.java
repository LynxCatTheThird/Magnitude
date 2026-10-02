package dev.magnitude.interaction;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Allows a player vehicle only during one already validated Magnitude operation. */
public final class PlayerMounts {
    private record Request(Entity passenger, ServerPlayer vehicle) {}
    private static final ThreadLocal<Request> REQUEST = new ThreadLocal<>();
    private PlayerMounts() {}

    public static boolean start(Entity passenger, ServerPlayer vehicle) {
        if (REQUEST.get() != null) return false;
        REQUEST.set(new Request(passenger, vehicle));
        try { return passenger.startRiding(vehicle, true, true); }
        finally { REQUEST.remove(); }
    }

    public static boolean authorized(Entity passenger, Entity vehicle) {
        Request request = REQUEST.get();
        return request != null && request.passenger == passenger && request.vehicle == vehicle;
    }
}
