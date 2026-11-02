package dev.magnitude.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import dev.magnitude.fabric.internal.NativeScaleService;
import dev.magnitude.fabric.internal.ScaleCommands;
import dev.magnitude.fabric.internal.CarryCommands;
import dev.magnitude.fabric.internal.CarryInteractionGuards;
import dev.magnitude.fabric.internal.carry.CarryRuntime;
import dev.magnitude.fabric.internal.carry.persistence.CarryRecoveryStore;
import dev.magnitude.fabric.internal.net.CarryNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
        CarryRecoveryStore.initAttachments();
        CarryNetworking.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> { CarryRuntime.start(server); CarryNetworking.start(server); });
        ServerLifecycleEvents.SERVER_STOPPING.register(CarryRuntime::stop);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { CarryRuntime.close(server); CarryNetworking.stop(server); });
        ServerTickEvents.END_SERVER_TICK.register(server -> { CarryRuntime.tick(server); CarryNetworking.tick(server); });
        ServerEntityEvents.ENTITY_LOAD.register(CarryRuntime::onEntityLoad);
        ServerEntityEvents.ENTITY_UNLOAD.register(CarryRuntime::onEntityUnload);
        ServerPlayerEvents.LEAVE.register(CarryRuntime::onPlayerLeave);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> CarryNetworking.joined(handler.player));
        ScaleCommands.register();
        CarryCommands.register();
        CarryInteractionGuards.register();
        LOGGER.info("Magnitude {} initialized: native sizing and consent-based hand carrying.",
                FabricLoader.getInstance().getModContainer(ID).orElseThrow().getMetadata().getVersion().getFriendlyString());
    }
}
