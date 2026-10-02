package dev.magnitude.network;

import dev.magnitude.core.EntityState;
import dev.magnitude.interaction.Interactions;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.minecraft.server.level.ServerPlayer;

public final class Messages {
    private Messages() {}
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE,ActionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CarryPayload.TYPE,CarryPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE,(payload,context) -> {
            ServerPlayer player=context.player();
            Interactions.action(player,payload.action());
        });
        EntityTrackingEvents.START_TRACKING.register((entity,observer) -> { if(entity instanceof ServerPlayer player) sendCarry(player,observer); });
    }
    public static void syncCarry(ServerPlayer carrier) {
        sendCarry(carrier,carrier);
        for (ServerPlayer observer:PlayerLookup.tracking(carrier)) sendCarry(carrier,observer);
    }
    private static void sendCarry(ServerPlayer carrier,ServerPlayer observer) {
        if (!ServerPlayNetworking.canSend(observer,CarryPayload.TYPE)) return;
        EntityState state=EntityState.of(carrier);
        ServerPlayNetworking.send(observer,new CarryPayload(carrier.getId(),state.carrying,state.carryPosition,state.offsetForward,state.offsetSide,state.offsetUp));
    }
}
