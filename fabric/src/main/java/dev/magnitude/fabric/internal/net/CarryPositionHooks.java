package dev.magnitude.fabric.internal.net;

import dev.magnitude.fabric.internal.carry.CarryRuntime;
import java.util.function.BiFunction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Side-neutral bridge; dedicated servers never link Minecraft client classes. */
public final class CarryPositionHooks {
    private static BiFunction<Entity, Entity, Vec3> client = (host, passenger) -> null;
    private CarryPositionHooks() {}
    public static void installClient(BiFunction<Entity, Entity, Vec3> resolver) { client = resolver; }
    public static Vec3 confirmedRoot(Entity host, Entity passenger) {
        return host.level().isClientSide() ? client.apply(host, passenger) : CarryRuntime.controlledPassengerPosition(host, passenger);
    }
}
