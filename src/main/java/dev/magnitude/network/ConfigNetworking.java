package dev.magnitude.network;

import com.google.gson.Gson;
import dev.magnitude.Magnitude;
import dev.magnitude.config.ConfigService;
import dev.magnitude.config.ConfigView;
import dev.magnitude.core.EntityState;
import dev.magnitude.core.Dimensions;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class ConfigNetworking {
    private static final Gson JSON=new Gson();
    private ConfigNetworking(){}
    public static void register(){
        PayloadTypeRegistry.serverboundPlay().register(ConfigRequest.TYPE,ConfigRequest.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ConfigSnapshot.TYPE,ConfigSnapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ConfigRequest.TYPE,(request,context)->{
            var player=context.player();var state=EntityState.of(player);long now=player.level().getGameTime();
            if(now<state.nextConfigRequest){
                if(now>=state.nextConfigReply){state.nextConfigReply=now+5;send(player,request.requestId(),false,new ConfigService.Result("rateLimited",""));}
                return;
            }
            state.nextConfigRequest=now+5;
            send(player,request.requestId(),false,apply(player,request));
        });
    }
    /** Receiver and verification share the same authorization and version checks. */
    public static ConfigService.Result apply(ServerPlayer player,ConfigRequest request){
        if(request.revision()<0 && request.scope()!=0)return new ConfigService.Result("invalid","");
        return switch(request.scope()){
            case 0 -> request.changes().isEmpty()?new ConfigService.Result("ready",""):new ConfigService.Result("invalid","");
            case 1 -> ConfigService.INSTANCE.personal(player,request.revision(),request.changes());
            case 2 -> ConfigService.INSTANCE.server(player.createCommandSourceStack(),request.revision(),request.changes());
            default -> new ConfigService.Result("invalid","");
        };
    }
    public static ConfigView view(ServerPlayer player,ConfigService.Result result){
        var state=EntityState.of(player);var settings=Magnitude.settings;var metrics=state.contacts.diagnostics;
        String common=!player.getAbilities().mayBuild||!player.isAlive()||player.isSpectator()?"actorBlocked":"";
        String terrain=!settings.terrainDamage?"serverOff":!state.terrainEnabled?"personalOff":!common.isEmpty()?common:"enabled";
        String pressure=!terrain.equals("enabled")?terrain:!settings.standingPressure?"serverPressureOff":!state.pressureEnabled?"personalPressureOff":!player.onGround()||player.getAbilities().flying||player.isNoGravity()||player.isPassenger()?"notSupported":"enabled";
        return new ConfigView(ConfigService.INSTANCE.revision(),state.configRevision,ConfigService.administrator(player.createCommandSourceStack()),
            ConfigService.INSTANCE.values(),ConfigService.personalValues(player),result.code(),result.detail(),Dimensions.size(player),terrain.equals("enabled"),pressure.equals("enabled"),terrain,pressure,
            player.level().getServer().getAverageTickTimeNanos()/1_000_000d,metrics.averageMillis(),metrics.maximumNanos/1_000_000d,metrics.denied,metrics.samples,state.contacts.footprints.size(),metrics.failure,state.contacts.reason,dev.magnitude.physics.ServerMetrics.TICKS.summary(),metrics.timings.summary(),
            dev.magnitude.physics.ServerMetrics.cellsUsed,dev.magnitude.physics.ServerMetrics.pairsUsed,dev.magnitude.physics.ServerMetrics.materialChecks,dev.magnitude.physics.ServerMetrics.blockWrites);
    }
    public static void send(ServerPlayer player,long requestId,boolean open,ConfigService.Result result){
        if(ServerPlayNetworking.canSend(player,ConfigSnapshot.TYPE))ServerPlayNetworking.send(player,new ConfigSnapshot(requestId,open,JSON.toJson(view(player,result))));
    }
}
