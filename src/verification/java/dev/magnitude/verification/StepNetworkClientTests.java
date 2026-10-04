package dev.magnitude.verification;

import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import dev.magnitude.physics.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import java.nio.file.*;

public final class StepNetworkClientTests implements ClientModInitializer {
    private int phase=-1,ticks;
    private double size;
    private boolean walking,ready;
    @Override public void onInitializeClient(){
        if(!Boolean.getBoolean("magnitude.stepNetworkVerification"))return;
        ClientPlayNetworking.registerGlobalReceiver(NaturalTerrainProtocol.Phase.TYPE,(packet,context)->{
            phase=packet.index();size=packet.size();walking=packet.running();if(walking)ready=true;else if(phase>=0)ready=false;
        });
        ClientTickEvents.START_CLIENT_TICK.register(client->{
            net.minecraft.client.KeyMapping.releaseAll();
            if(client.player==null||client.level==null)return;
            client.player.setYRot(0);client.player.setXRot(0);client.options.keyUp.setDown(walking);
            if(!walking&&phase>=0&&!ready&&Math.abs(Dimensions.snapshot(client.player).base()-size)<1e-5
                &&EntityState.of(client.player).physicsRevision>0
                &&WorldObstacles.loaded(client.player,WorldObstacles.regions(PlayerBody.parts(client.player,client.player.position()),net.minecraft.world.phys.Vec3.ZERO,0))){
                ClientPlayNetworking.send(new NaturalTerrainProtocol.Ready(phase));ready=true;
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            try{
                if(client.player==null){
                    if(client.gui.screen() instanceof TitleScreen&&client.gui.overlay()==null){var address=System.getProperty("magnitude.testServer");ConnectScreen.startConnecting(client.gui.screen(),client,ServerAddress.parseString(address),new ServerData("Step verification",address,ServerData.Type.OTHER),false,null);}
                    return;
                }
                if(++ticks%20==0)Files.writeString(Path.of("step-client-live.json"),new com.google.gson.Gson().toJson(java.util.Map.of("tick",ticks,"phase",phase,"walking",walking,"position",client.player.position().toString(),"pose",EntityState.of(client.player).pose.toString(),"grounded",client.player.onGround())));
                if(Files.exists(Path.of("stop.signal")))client.stop();
            }catch(java.io.IOException error){throw new RuntimeException(error);}
        });
    }
}
