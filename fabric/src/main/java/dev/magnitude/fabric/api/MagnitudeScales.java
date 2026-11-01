package dev.magnitude.fabric.api;

import dev.magnitude.fabric.internal.NativeScaleService;
import net.minecraft.server.MinecraftServer;

/** Public Fabric-facing access point; no core implementation type is exposed. */
public final class MagnitudeScales {
    private MagnitudeScales() {}
    public static ScaleAccess forServer(MinecraftServer server) {
        return NativeScaleService.forServer(server);
    }
}
