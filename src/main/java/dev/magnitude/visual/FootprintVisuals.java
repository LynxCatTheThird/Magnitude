package dev.magnitude.visual;

import dev.magnitude.core.EntityState;
import dev.magnitude.physics.ContactEvent;
import dev.magnitude.physics.PlayerBody;
import dev.magnitude.network.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Bounded optional observer notifications independent of terrain mutation permission. */
public final class FootprintVisuals {
    private static int sends;
    // ContactState may reset on teleport/detach; observer identities must remain unique.
    private static long sequence;
    private FootprintVisuals(){}
    public static void register(){
        PayloadTypeRegistry.serverboundPlay().register(VisualSubscription.TYPE,VisualSubscription.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FootprintPayload.TYPE,FootprintPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(VisualSubscription.TYPE,(request,context)->{
            var state=EntityState.of(context.player());long tick=context.player().level().getGameTime();
            state.visualFootprints=request.footprints();
            if(request.footprints()&&tick>=state.nextVisualRequest&&context.player().onGround()) {
                state.nextVisualRequest=tick+5;
                long visualSequence=++sequence;
                var player=context.player();var scale=dev.magnitude.core.Dimensions.snapshot(player);
                for(int side:new int[]{-1,1})if((state.pose.support()&(side<0?1:2))!=0) {
                    var payload=new FootprintPayload(player.getUUID(),visualSequence,side,player.level().dimension().identifier(),PlayerBody.foot(player,side),scale.bootHalfWidth(),scale.bootHalfLength(),Math.toRadians(player.getYRot())%(Math.PI*2));
                    if(payload.valid())send(player,payload);
                }
            }
        });
        ServerTickEvents.START_SERVER_TICK.register(server->sends=0);
    }
    public static void contact(ContactEvent event){
        var player=event.player();if(!player.isAlive()||player.isSpectator()||player.getAbilities().flying||player.isNoGravity()||player.isPassenger())return;
        boolean stride=event.type()==ContactEvent.Type.WALKING_STRIDE;
        if(!stride&&event.type()!=ContactEvent.Type.TAKEOFF&&event.type()!=ContactEvent.Type.LANDING&&event.type()!=ContactEvent.Type.SUPPORT_CHANGED)return;
        if(event.type()==ContactEvent.Type.SUPPORT_CHANGED&&event.movement().horizontalDistanceSqr()>1e-6)return;
        var scale=event.scale();long visualSequence=++sequence;
        for(int side:new int[]{-1,1}){
            if((event.pose().support()&(side<0?1:2))==0)continue;
            var payload=new FootprintPayload(player.getUUID(),visualSequence,side,player.level().dimension().identifier(),PlayerBody.foot(player,side),scale.bootHalfWidth(),scale.bootHalfLength(),Math.toRadians(player.getYRot())%(Math.PI*2));
            if(!payload.valid())continue;
            send(player,payload);
            int observers=0;for(var observer:PlayerLookup.tracking(player)){if(++observers>128||sends>=256)break;send(observer,payload);}
        }
    }
    private static void send(net.minecraft.server.level.ServerPlayer observer,FootprintPayload payload){
        if(sends>=256||!EntityState.of(observer).visualFootprints||!ServerPlayNetworking.canSend(observer,FootprintPayload.TYPE))return;
        sends++;ServerPlayNetworking.send(observer,payload);
    }
}
