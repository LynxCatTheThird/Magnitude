package dev.magnitude.fabric.api.carry;

import dev.magnitude.fabric.internal.carry.CarryRuntime;
import net.minecraft.server.MinecraftServer;

public final class MagnitudeCarry {
    private MagnitudeCarry() {}
    public static CarryAccess forServer(MinecraftServer server) { return CarryRuntime.forServer(server); }
}
