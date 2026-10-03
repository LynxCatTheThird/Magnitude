package dev.magnitude.verification;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import dev.magnitude.client.settings.ClientMetrics;
import dev.magnitude.client.visual.FootprintRenderer;
import java.nio.file.*;
import java.util.*;

/** Sends normal forward input; terrain and movement remain server authoritative. */
public final class NaturalTerrainClientTests implements ClientModInitializer {
    private int ticks;
    @Override public void onInitializeClient(){
        if(!Boolean.getBoolean("magnitude.naturalTerrainVerification"))return;
        ClientTickEvents.START_CLIENT_TICK.register(client->{
            WorldQueryTrace.ROWS.clear();WorldQueryTrace.MOVES.clear();
            net.minecraft.client.KeyMapping.releaseAll();
            if(client.player!=null&&client.level!=null&&client.gui.overlay()==null){
                client.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
                client.player.setYRot(0);client.player.setXRot(10);client.options.keyUp.setDown(true);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            try{
                if(client.player==null){
                    if(client.gui.screen() instanceof TitleScreen&&client.gui.overlay()==null){
                        String address=System.getProperty("magnitude.testServer");ConnectScreen.startConnecting(client.gui.screen(),client,ServerAddress.parseString(address),new ServerData("Native terrain verification",address,ServerData.Type.OTHER),false,null);
                    }return;
                }
                if(WorldQueryTrace.ROWS.stream().anyMatch(r->!Boolean.TRUE.equals(((Map<?,?>)r).get("complete"))))Files.writeString(Path.of("natural-client-denied-trace.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(WorldQueryTrace.ROWS));
                if(++ticks%20==0){
                    if(!WorldQueryTrace.MOVES.isEmpty())Files.writeString(Path.of("natural-client-movement-trace.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(WorldQueryTrace.MOVES));
                    var data=new LinkedHashMap<String,Object>();data.put("ticks",ticks);data.put("position",client.player.position().toString());data.put("scale",dev.magnitude.core.Dimensions.snapshot(client.player).base());data.put("frames",ClientMetrics.FRAMES.summary());data.put("cache",FootprintRenderer.cached());data.put("cacheTick",FootprintRenderer.TICK_TIMES.summary());data.put("extraction",FootprintRenderer.EXTRACTION_TIMES.summary());data.put("failure",dev.magnitude.core.EntityState.of(client.player).contacts.diagnostics.failure);data.put("rejectionEvents",dev.magnitude.core.EntityState.of(client.player).contacts.diagnostics.rejectionEvents);
                    var regions=dev.magnitude.physics.WorldObstacles.regions(dev.magnitude.physics.PlayerBody.parts(client.player,client.player.position()),client.player.getDeltaMovement(),dev.magnitude.physics.StepPolicy.height(client.player));
                    var geometry=new ArrayList<Object>();long total=0;
                    for(var region:regions){long count=dev.magnitude.physics.LocalProxy.cells(region);total+=count;geometry.add(Map.of("box",region.toString(),"cells",count,"loaded",dev.magnitude.physics.LocalProxy.loaded(client.level,region)));}
                    data.put("envelopeCells",total);data.put("regions",geometry);data.put("velocity",client.player.getDeltaMovement().toString());data.put("grounded",client.player.onGround());data.put("inputForward",client.options.keyUp.isDown());data.put("input",client.player.input.keyPresses.toString());data.put("moveVector",client.player.input.getMoveVector().toString());data.put("snapshot",dev.magnitude.core.Dimensions.snapshot(client.player).toString());
                    if(ticks%200==0)net.minecraft.client.Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(),image->{try{image.writeToFile(Path.of("natural-"+ticks+".png"));}catch(Exception error){throw new RuntimeException(error);}finally{image.close();}});
                    Files.writeString(Path.of("natural-client-live.json"),new com.google.gson.Gson().toJson(data));
                }
                if(Files.exists(Path.of("stop.signal"))){net.minecraft.client.KeyMapping.releaseAll();client.stop();}
            }catch(Exception error){throw new RuntimeException(error);}
        });
    }
}
