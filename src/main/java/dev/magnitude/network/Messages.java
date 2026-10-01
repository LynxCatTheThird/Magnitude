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
            if (!player.isAlive() || player.isSpectator()) return;
            switch(payload.action()) {
                case 0 -> Interactions.blow(player);
                case 1 -> Interactions.shock(player,Math.min(6,dev.magnitude.core.Dimensions.size(player)*0.3),false);
                case 2 -> { if (Interactions.cooldown(player,10)) Interactions.release(player,false); }
                case 3 -> { if (Interactions.cooldown(player,10)) Interactions.release(player,true); }
                case 4 -> Interactions.ability(player);
                case 5 -> { var target=Interactions.aim(player,16); if(target!=null) Interactions.ride(player,target); }
                default -> { /* Unknown actions cannot mutate game state. */ }
            }
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
