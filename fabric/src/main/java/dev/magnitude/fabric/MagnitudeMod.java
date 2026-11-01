package dev.magnitude.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import dev.magnitude.fabric.internal.NativeScaleService;
import dev.magnitude.fabric.internal.ScaleCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric lifecycle entrypoint. Common code must not reference client classes. */
public final class MagnitudeMod implements ModInitializer {
    public static final String ID = "magnitude";
    private static final Logger LOGGER = LoggerFactory.getLogger(ID);

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(NativeScaleService::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(NativeScaleService::stop);
        ScaleCommands.register();
        LOGGER.info("Magnitude S0 prototype initialized; advanced interaction capabilities are not enabled.");
    }
}
