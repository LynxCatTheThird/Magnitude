package dev.magnitude.verification;

import dev.magnitude.client.visual.FootprintRenderer;
import dev.magnitude.client.settings.ClientMetrics;
import dev.magnitude.network.FootprintPayload;
import dev.magnitude.physics.TimingWindow;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

/** Fixed client camera and synthetic decoration events over real server-loaded blocks. */
public final class VisualPerformanceTests implements ClientModInitializer {
    private final List<Object> results=new ArrayList<>();
    private final com.google.gson.Gson json=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    private int stage,tick,profile=-1,frames;
    private long sequence;
    private int peakChecks,peakDrawn;
    private double cameraY;
    private static final int[] LIMITS={0,512,2048,4096};
    @Override public void onInitializeClient(){
        if(!Boolean.getBoolean("magnitude.visualPerformanceVerification"))return;
        LevelExtractionEvents.END_EXTRACTION.register(context->{if(stage==3&&tick>=160){frames++;peakDrawn=Math.max(peakDrawn,FootprintRenderer.lastDrawn);}});
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            net.minecraft.client.KeyMapping.releaseAll();
            try{
                switch(stage){
                    case 0 -> {
                        if(!(client.gui.screen() instanceof TitleScreen)||client.gui.overlay()!=null)return;
                        String address=System.getProperty("magnitude.testServer");
                        ConnectScreen.startConnecting(client.gui.screen(),client,ServerAddress.parseString(address),new ServerData("Visual cost verification",address,ServerData.Type.OTHER),false,null);stage=1;
                    }
                    case 1 -> {
                        if(client.player==null||client.level==null||client.gui.overlay()!=null)return;
                        if(client.player.getAbilities().mayfly&&!client.player.getAbilities().flying){client.player.getAbilities().flying=true;client.player.onUpdateAbilities();}
                        if(Math.abs(client.player.getY()-230)>1)return;
                        if(!client.level.getBlockState(new BlockPos(99,199,99)).is(net.minecraft.world.level.block.Blocks.STONE))return;
                        if(!client.level.getBlockState(new BlockPos(60,199,60)).is(net.minecraft.world.level.block.Blocks.STONE))return;
                        client.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
                        cameraY=client.player.getY();client.gui.setScreen(null);stage=2;
                    }
                    case 2 -> {
                        if(++profile>=LIMITS.length*2){Files.writeString(Path.of("visual-performance-results.json"),json.toJson(Map.of("success",true,"scope","fixed camera, real client, synthetic local decoration input; frame interval is not GPU duration","results",results)));client.stop();stage=4;return;}
                        FootprintRenderer.enabled=false;FootprintRenderer.reset();FootprintRenderer.cacheLimit=LIMITS[profile%LIMITS.length]==0?2048:LIMITS[profile%LIMITS.length];FootprintRenderer.distance=128;
                        tick=frames=peakChecks=peakDrawn=0;stage=3;
                    }
                    case 3 -> {
                        client.player.setYRot(0);client.player.setXRot(90);
                        if(Math.abs(client.player.getY()-cameraY)>1e-5||Math.abs(client.player.getX()-100.5)>1e-5||Math.abs(client.player.getZ()-100.5)>1e-5)throw new AssertionError("visual profiling camera moved");
                        int limit=LIMITS[profile%LIMITS.length];
                        if(tick==1){FootprintRenderer.enabled=limit!=0;if(limit!=0)FootprintRenderer.accept(new FootprintPayload(UUID.randomUUID(),++sequence,-1,client.level.dimension().identifier(),new Vec3(100,199,100),32,32,0));}
                        if(tick==160){ClientMetrics.FRAMES.clear();FootprintRenderer.TICK_TIMES.clear();FootprintRenderer.EXTRACTION_TIMES.clear();frames=peakChecks=peakDrawn=0;}
                        if(tick>=160)peakChecks=Math.max(peakChecks,FootprintRenderer.lastChecks);
                        if(++tick>=240){
                            if(limit!=0&&FootprintRenderer.cached()!=limit)throw new AssertionError("profile cache not filled: "+FootprintRenderer.cached()+" target "+limit);
                            if(limit!=0&&peakDrawn!=limit)throw new AssertionError("rectangular cache did not draw every cell once: "+peakDrawn+" target "+limit);
                            if(limit==4096){
                                for(int x=68;x<132;x++)for(int z=68;z<132;z++)if(!FootprintRenderer.hasSurface(new BlockPos(x,199,z)))throw new AssertionError("positive-area surface was evicted by a degenerate border cell: "+x+","+z);
                            }
                            if(peakChecks>256||peakDrawn>4096)throw new AssertionError("visual work exceeded hard bounds");
                            var row=new LinkedHashMap<String,Object>();row.put("cycle",profile/LIMITS.length);row.put("cacheLimit",limit);row.put("cached",FootprintRenderer.cached());row.put("queued",FootprintRenderer.queued());row.put("frames",ClientMetrics.FRAMES.summary());row.put("cacheTick",FootprintRenderer.TICK_TIMES.summary());row.put("extraction",FootprintRenderer.EXTRACTION_TIMES.summary());row.put("peakChecks",peakChecks);row.put("peakDrawn",peakDrawn);row.put("drawFrames",frames);results.add(row);
                            Files.writeString(Path.of("visual-performance-progress.json"),json.toJson(results));
                            if(profile==LIMITS.length-1)net.minecraft.client.Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(),image->{try{image.writeToFile(Path.of("dense-overlays.png"));}catch(Exception error){throw new RuntimeException(error);}finally{image.close();}});
                            stage=2;
                        }
                    }
                    default -> {}
                }
            }catch(Throwable error){error.printStackTrace();try{Files.writeString(Path.of("visual-performance-results.json"),json.toJson(Map.of("success",false,"failure",error.toString(),"results",results)));}catch(Exception ignored){}client.stop();stage=4;}
        });
    }
}
