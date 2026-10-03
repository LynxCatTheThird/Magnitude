package dev.magnitude.verification;

import com.mojang.authlib.GameProfile;
import dev.magnitude.Magnitude;
import dev.magnitude.core.*;
import dev.magnitude.interaction.*;
import dev.magnitude.physics.*;
import dev.magnitude.terrain.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;
import java.lang.management.ManagementFactory;

/** Real server ticks, one deterministic unconnected actor; no simulated client/FPS claims. */
public final class TerrainPerformanceTests implements ModInitializer {
    private static final BlockPos ROOT=new BlockPos(24000,200,24000);
    private record Profile(String name,boolean terrain,boolean shallow,int writes,int checks,int impact){}
    private static final Profile[] PROFILES={new Profile("off",false,false,256,2048,64),new Profile("compatibility",true,false,256,2048,64),new Profile("shallow32",true,true,32,256,16),new Profile("shallow256",true,true,256,2048,64)};
    private final List<Object> results=new ArrayList<>();
    private final Map<String,Window> windows=new LinkedHashMap<>();
    private final com.google.gson.Gson json=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    private ServerPlayer actor;
    private int scenario=-1,tick;
    private long began;
    private int cycle;
    private boolean ready;
    private final com.sun.management.ThreadMXBean allocation=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static final class Window {
        final TimingWindow work=new TimingWindow(512),server=new TimingWindow(512);
        long allocated,writes,checks,cells,pairs;int peakQueue,peakWrites,denied;
    }
    @Override public void onInitialize(){
        if(!Boolean.getBoolean("magnitude.performanceVerification"))return;
        if(allocation.isThreadAllocatedMemorySupported())allocation.setThreadAllocatedMemoryEnabled(true);
        ServerLifecycleEvents.SERVER_STARTED.register(server->ready=true);
        ServerTickEvents.START_SERVER_TICK.register(server->began=System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(!ready)return;
            try{
                if(scenario<0||tick>=360){
                    if(scenario>=0)finishScenario();
                    if(++scenario>=PROFILES.length*2&&++cycle<2)scenario=0;
                    if(scenario>=PROFILES.length*2){Files.writeString(Path.of("performance-results.json"),json.toJson(Map.of("success",true,"results",results,"scope","one unconnected actor on real server ticks; excludes client, network and GPU")));ready=false;server.halt(false);return;}
                    prepare(server);return;
                }
                runTick(server);
            }catch(Throwable error){error.printStackTrace();try{Files.writeString(Path.of("performance-results.json"),json.toJson(Map.of("success",false,"failure",error.toString(),"results",results)));}catch(Exception ignored){}ready=false;server.halt(false);}
        });
    }
    private void prepare(MinecraftServer server)throws Exception{
        var profile=PROFILES[scenario%PROFILES.length];double size=scenario<PROFILES.length?5:50;
        Magnitude.settings=new Settings();Magnitude.settings.maximum=100;Magnitude.settings.terrainDamage=profile.terrain;Magnitude.settings.shallowDeformation=profile.shallow;
        Magnitude.settings.blocksPerTick=profile.writes;Magnitude.settings.checksPerTick=profile.checks;Magnitude.settings.blocksPerImpact=profile.impact;Magnitude.settings.bodyDamage=false;
        var level=server.overworld();
        for(int x=-2;x<=2;x++)for(int z=-1;z<=7;z++){
            var position=ROOT.offset(x*16,0,z*16);level.setChunkForced(position.getX()>>4,position.getZ()>>4,true);level.getChunk(position);
        }
        for(var pos:BlockPos.betweenClosed(ROOT.offset(-16,0,-8),ROOT.offset(16,12,100)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        for(var pos:BlockPos.betweenClosed(ROOT.offset(-16,-3,-8),ROOT.offset(16,-2,100)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
        for(var pos:BlockPos.betweenClosed(ROOT.offset(-16,-1,-8),ROOT.offset(16,-1,100))){level.setBlock(pos,Blocks.GRASS_BLOCK.defaultBlockState(),2);if((pos.getX()+pos.getZ())%3==0)level.setBlock(pos.above(),Blocks.SHORT_GRASS.defaultBlockState(),2);}
        if(actor!=null)actor.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        actor=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"TerrainPerformance"),ClientInformation.createDefault());
        actor.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),actor,CommonListenerCookie.createInitial(actor.getGameProfile(),false));
        actor.setPos(ROOT.getX()+.5,200,ROOT.getZ()+.5);actor.setYRot(0);actor.setOnGround(true);actor.getAbilities().mayBuild=true;Dimensions.set(actor,size,0);EntityState.of(actor).terrainEnabled=true;
        tick=0;windows.clear();Files.writeString(Path.of("performance-progress.json"),json.toJson(Map.of("scenario",scenario,"profile",profile.name,"scale",size)));System.out.println("PERFORMANCE start "+profile.name+" scale="+size);
    }
    private void runTick(MinecraftServer server){
        String phase=tick<60?"warmup":tick<160?"standing":tick<280?"walking":"settling";
        long start=System.nanoTime(),id=Thread.currentThread().threadId(),before=allocation.isThreadAllocatedMemoryEnabled()?allocation.getThreadAllocatedBytes(id):0;
        double motion=Dimensions.snapshot(actor).motionFactor();
        actor.move(MoverType.SELF,new Vec3(0,-.08/motion,phase.equals("walking")?.5/motion:0));
        Impact.continueFeet(actor);SoilDeformation.continueWork(actor);ContactEvents.sample(actor);
        if(!phase.equals("warmup")){
            var window=windows.computeIfAbsent(phase,key->new Window());var state=EntityState.of(actor);
            window.work.add(System.nanoTime()-start);window.server.add(System.nanoTime()-began);
            if(allocation.isThreadAllocatedMemoryEnabled())window.allocated+=allocation.getThreadAllocatedBytes(id)-before;
            int writes=Magnitude.settings.blocksPerTick-Impact.remaining();window.writes+=writes;window.peakWrites=Math.max(window.peakWrites,writes);
            window.checks+=Magnitude.settings.checksPerTick-Impact.checksRemaining();window.cells+=state.physicsCells;window.pairs+=state.physicsPairs;
            window.peakQueue=Math.max(window.peakQueue,state.contacts.soil.size()+state.contacts.footprints.size());if(state.movementDenied)window.denied++;
            if(state.movementDenied)throw new AssertionError("performance fixture movement denied: "+state.contacts.diagnostics.failure+" position="+actor.position());
        }
        tick++;
    }
    private void finishScenario(){
        var profile=PROFILES[scenario%PROFILES.length];var phases=new LinkedHashMap<String,Object>();
        for(var entry:windows.entrySet()){
            var w=entry.getValue();phases.put(entry.getKey(),Map.of("workMs",w.work.summary(),"tickMs",w.server.summary(),"allocatedBytes",w.allocated,"writes",w.writes,"checks",w.checks,"cells",w.cells,"pairs",w.pairs,"peakQueue",w.peakQueue,"peakWrites",w.peakWrites,"denied",w.denied));
        }
        var result=Map.of("cycle",cycle,"profile",profile.name,"size",Dimensions.snapshot(actor).base(),"position",actor.position().toString(),"phases",phases);results.add(result);System.out.println("PERFORMANCE result "+json.toJson(result));
    }
}
