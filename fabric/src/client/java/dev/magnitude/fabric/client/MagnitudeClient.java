package dev.magnitude.fabric.client;

import dev.magnitude.fabric.api.carry.CarryView;
import dev.magnitude.fabric.internal.net.CarryPositionHooks;
import dev.magnitude.fabric.internal.net.CarryStatePayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Only confirmed server anchors affect passenger positioning. No client authority is introduced. */
public final class MagnitudeClient implements ClientModInitializer {
    private static final Map<UUID, CarryView> VIEWS = new HashMap<>();
    private static UUID epoch;
    private static long sequence = -1;
    @Override public void onInitializeClient() {
        CarryPositionHooks.installClient(MagnitudeClient::anchor);
        ClientPlayNetworking.registerGlobalReceiver(CarryStatePayload.TYPE, (payload, context) -> {
            var recipient = context.player();
            context.client().execute(() -> {
            if (context.client().player != recipient) return;
            if (payload.epoch().equals(epoch) && payload.sequence() <= sequence) return;
            epoch = payload.epoch(); sequence = payload.sequence();
            VIEWS.clear();
            for (CarryView view : payload.views()) VIEWS.put(view.targetId(), view);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { VIEWS.clear(); epoch = null; sequence = -1; });
    }
    private static Vec3 anchor(Entity host, Entity passenger) {
        CarryView view = VIEWS.get(passenger.getUUID());
        if (view == null || view.phase() != CarryView.Phase.HELD || !host.getUUID().equals(view.hostId())
                || host.getId() != view.hostEntityId() || passenger.getId() != view.targetEntityId()
                || !host.level().dimension().identifier().toString().equals(view.dimension())) return null;
        return view.anchor();
    }
}
