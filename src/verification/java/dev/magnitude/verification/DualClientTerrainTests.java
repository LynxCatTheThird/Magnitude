package dev.magnitude.verification;

import dev.magnitude.client.settings.SettingsConnection;
import dev.magnitude.client.visual.FootprintRenderer;
import dev.magnitude.content.CompactedSoilBlock;
import dev.magnitude.content.WorldContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Two concurrent real clients with independent visual preferences on one server-owned surface. */
public final class DualClientTerrainTests implements ClientModInitializer {
    private static final BlockPos SHARED=new BlockPos(99,199,100);
    private int stage,frame,stable;
    private double settled=Double.NaN;
    private final List<String> passed=new ArrayList<>();
    private final com.google.gson.Gson json=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);passed.add(label);}
    @Override public void onInitializeClient(){
        if(!Boolean.getBoolean("magnitude.dualTerrainVerification"))return;
        boolean writer=Boolean.getBoolean("magnitude.terrainWriter");
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            net.minecraft.client.KeyMapping.releaseAll();
            if(++frame%5!=0)return;
            try{
                if(client.player!=null&&client.level!=null)Files.writeString(Path.of("progress.json"),json.toJson(Map.of("stage",stage,"position",client.player.position().toString(),"surface",client.level.getBlockState(SHARED).toString(),"grounded",client.player.onGround(),"pose",dev.magnitude.core.EntityState.of(client.player).pose.toString())));
                switch(stage){
                    case 0 -> {
                        if(!(client.gui.screen() instanceof TitleScreen)||client.gui.overlay()!=null)return;
                        String address=System.getProperty("magnitude.testServer");
                        ConnectScreen.startConnecting(client.gui.screen(),client,ServerAddress.parseString(address),new ServerData("Two-client terrain verification",address,ServerData.Type.OTHER),false,null);
                    }
                    case 1 -> {
                        if(client.player==null||client.level==null||client.gui.overlay()!=null||client.player.getY()<199||client.player.getY()>201)return;
                        if(!client.level.getBlockState(SHARED).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK))return;
                        FootprintRenderer.enabled=writer;
                        client.player.connection.sendCommand("magnitude config player terrain "+(writer?"on":"off"));
                        client.player.connection.sendCommand("magnitude config player pressure "+(writer?"on":"off"));
                        client.player.connection.sendCommand("magnitude scale set "+(writer?5:1)+" 0");
                    }
                    case 2 -> {
                        if(Math.abs(dev.magnitude.core.Dimensions.snapshot(client.player).base()-(writer?5:1))>1e-6)return;
                        if(writer&&!FootprintRenderer.hasSurface(SHARED))return;
                        if(writer)check(BlockPos.containing(dev.magnitude.physics.PlayerBody.foot(client.player,-1)).equals(SHARED),"shared soil fixture samples the actual left sole without relying on proxy escape drift");
                        check(writer?FootprintRenderer.cached()>0:FootprintRenderer.cached()==0,"visual preferences are independent before real terrain changes");
                        Files.writeString(Path.of("ready.json"),json.toJson(Map.of("writer",writer,"cache",FootprintRenderer.cached())));
                    }
                    case 3 -> {
                        var surface=client.level.getBlockState(SHARED);
                        if(!surface.is(WorldContent.COMPACTED_GRASS))return;
                        double actualSurface=199+surface.getValue(CompactedSoilBlock.HEIGHT)/16d;
                        if(actualSurface<199.5||actualSurface>199.875||Math.abs(client.player.getY()-actualSurface)>1e-4){stable=0;return;}
                        if(Double.isFinite(settled)&&Math.abs(settled-actualSurface)>1e-4)stable=0;
                        settled=actualSurface;if(++stable<20)return;stable=0;
                        check(surface.getCollisionShape(client.level,SHARED).max(net.minecraft.core.Direction.Axis.Y)==surface.getValue(CompactedSoilBlock.HEIGHT)/16d,"client receives native soil height and matching collision");
                        check(writer||FootprintRenderer.cached()==0,"visuals disabled still receives authoritative terrain and support");
                        settled=client.player.getY();
                    }
                    case 4 -> {
                        check(Math.abs(client.player.getY()-settled)<1e-4,"shared settled surface remains stable while both players stand");
                        if(++stable<12)return;
                        Files.writeString(Path.of("settled.json"),json.toJson(Map.of("writer",writer,"height",client.player.getY(),"frames",dev.magnitude.client.settings.ClientMetrics.FRAMES.summary())));
                    }
                    case 5 -> {
                        if(!Files.exists(Path.of("backend-off.signal")))return;
                        settled=client.player.getY();
                        if(SettingsConnection.busy())return;
                        if(!SettingsConnection.request(0,Map.of()))return;
                    }
                    case 6 -> {
                        if(SettingsConnection.busy()||SettingsConnection.view==null)return;
                        if(SettingsConnection.view.server().get("shallowDeformation")!=0){SettingsConnection.request(0,Map.of());return;}
                        check(client.level.getBlockState(SHARED).is(WorldContent.COMPACTED_GRASS)&&Math.abs(client.player.getY()-settled)<1e-4,"disabling backend preserves both clients' existing soil and support");
                        FootprintRenderer.enabled=false;
                    }
                    case 7 -> {
                        if(FootprintRenderer.cached()!=0||FootprintRenderer.queued()!=0)return;
                        check(Math.abs(client.player.getY()-settled)<1e-4,"disabling visual cache cannot change authoritative collision");
                        net.minecraft.client.Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(),image->{try{image.writeToFile(Path.of("shared-soil.png"));}catch(Exception error){throw new RuntimeException(error);}finally{image.close();}});
                        Files.writeString(Path.of("dual-results.json"),json.toJson(Map.of("success",true,"passed",passed,"frames",dev.magnitude.client.settings.ClientMetrics.FRAMES.summary(),"server",SettingsConnection.view.tickTimings(),"movement",SettingsConnection.view.moveTimings())));client.stop();
                    }
                    default -> {return;}
                }
                stage++;
            }catch(Throwable error){error.printStackTrace();try{Files.writeString(Path.of("dual-results.json"),json.toJson(Map.of("success",false,"passed",passed,"failure",error.toString(),"stage",stage)));}catch(Exception ignored){}client.stop();}
        });
    }
}
