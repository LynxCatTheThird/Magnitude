package dev.magnitude.fabric.api;

import dev.magnitude.fabric.internal.scale.ScaleService;
import net.minecraft.server.MinecraftServer;

/** Public Fabric-facing access point; no core implementation type is exposed. */
public final class MagnitudeScales {
    private MagnitudeScales() {}
    public static ScaleAccess forServer(MinecraftServer server) {
        return ScaleService.forServer(server);
    }
}
