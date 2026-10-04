package dev.magnitude.verification;

import dev.magnitude.Magnitude;
import dev.magnitude.core.*;
import dev.magnitude.interaction.*;
import dev.magnitude.physics.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.biome.Biomes;
import java.nio.file.*;
import java.util.*;

/** Native generated terrain with an actual walking client; no block fixture or forced chunks. */
public final class NaturalTerrainTests implements ModInitializer {
    private static final double[] SIZES={5,50,49.875};
    private final com.google.gson.Gson json=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    private final List<Object> results=new ArrayList<>();
    private TimingWindow ticks=new TimingWindow(2048);
    private int phase=-1,tick,denied,stalled,waiting,grounded,completeSupport,supportedFeet;
    private boolean ready;
    private final Map<String,Integer> reasons=new LinkedHashMap<>();
    private final List<Object> startSurfaces=new ArrayList<>();
    private long start,writes,checks,cells,pairs,rejections,totalWriteCost;
    private int compactedContactTicks;
    private net.minecraft.world.phys.Vec3 origin,previous;
    private BlockPos site;
    private double low,high;
    @Override public void onInitialize(){
        if(!Boolean.getBoolean("magnitude.naturalTerrainVerification"))return;
        NaturalTerrainProtocol.register();
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(NaturalTerrainProtocol.Ready.TYPE,(packet,context)->{
            if(packet.index()==phase&&context.player().getName().getString().equals("NaturalWalker"))ready=true;
        });
        ServerTickEvents.START_SERVER_TICK.register(server->{start=System.nanoTime();WorldQueryTrace.ROWS.clear();WorldQueryTrace.MOVES.clear();});
        ServerTickEvents.END_SERVER_TICK.register(server->{
            var p=server.getPlayerList().getPlayerByName("NaturalWalker");if(p==null||phase>=SIZES.length)return;
            try{
                if(phase>=0)totalWriteCost+=Magnitude.settings.blocksPerTick-Impact.remaining();
                if(site==null){
                    var found=server.overworld().findClosestBiome3d(b->b.is(Biomes.PLAINS),new BlockPos(0,80,0),2048,32,64);
                    if(found==null)throw new AssertionError("no native plains site found");site=found.getFirst();
                }
                if(phase<0||tick>=600){
                    if(phase>=0){
                        var row=new LinkedHashMap<String,Object>();row.put("scale",SIZES[phase]);row.put("origin",origin.toString());row.put("end",p.position().toString());row.put("forward",p.getZ()-origin.z);row.put("minimumY",low);row.put("maximumY",high);row.put("deniedTicks",denied);row.put("rejectionEvents",EntityState.of(p).contacts.diagnostics.rejectionEvents-rejections);row.put("stalledTicks",stalled);row.put("writes",writes);row.put("checks",checks);row.put("cells",cells);row.put("pairs",pairs);row.put("serverTick",ticks.summary());row.put("groundedTicks",grounded);row.put("completeSupportTicks",completeSupport);row.put("supportedFeetTicks",supportedFeet);row.put("contactReasons",new LinkedHashMap<>(reasons));row.put("initialSurfaces",new ArrayList<>(startSurfaces));row.put("readyWaitTicks",waiting);row.put("standingPressure",Magnitude.settings.standingPressure);row.put("stride",Dimensions.snapshot(p).stride());row.put("eventCounts",EntityState.of(p).contacts.counts.clone());row.put("allStageWriteCost",totalWriteCost);row.put("compactedContactTicks",compactedContactTicks);results.add(row);
                        Files.writeString(Path.of("natural-server-progress.json"),json.toJson(results));
                    }
                    if(++phase>=SIZES.length){Files.writeString(Path.of("natural-server-results.json"),json.toJson(Map.of("success",true,"results",results,"scope","three 30-second native terrain walks; reports failures as measurements, not acceptance")));return;}
                    int x=site.getX()+phase*160,z=site.getZ();var level=p.level();level.getChunk(new BlockPos(x,80,z));
                    startSurfaces.clear();int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                    // Establish a nonpenetrating drop above both true sole areas, without changing terrain.
                    Dimensions.set(p,SIZES[phase],0);double width=Dimensions.snapshot(p).modelWidth(),half=Dimensions.snapshot(p).bootHalfWidth();
                    for(int side:new int[]{-1,1}){
                        double center=x+.5+side*.15*width;int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
                        for(int sx=(int)Math.floor(center-half);sx<=Math.floor(center+half);sx++)for(int sz=(int)Math.floor(z+.5-half);sz<=Math.floor(z+.5+half);sz++){
                            level.getChunk(new BlockPos(sx,80,sz));int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,sx,sz);min=Math.min(min,surface);max=Math.max(max,surface);
                        }
                        startSurfaces.add(Map.of("side",side,"minimum",min,"maximum",max));y=Math.max(y,max);
                    }
                    y+=2;
                    p.teleportTo(level,x+.5,y,z+.5,Set.of(),0,10,true);Dimensions.set(p,SIZES[phase],0);p.getAbilities().mayBuild=true;p.getAbilities().invulnerable=true;
                    EntityState.of(p).terrainEnabled=true;EntityState.of(p).pressureEnabled=true;
                    Magnitude.settings.maximum=100;Magnitude.settings.terrainDamage=true;Magnitude.settings.shallowDeformation=true;Magnitude.settings.standingPressure=true;Magnitude.settings.bodyDamage=false;
                    ready=false;waiting=grounded=completeSupport=supportedFeet=compactedContactTicks=0;totalWriteCost=0;reasons.clear();
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,new NaturalTerrainProtocol.Phase(phase,SIZES[phase],false));
                    origin=previous=p.position();low=high=p.getY();rejections=EntityState.of(p).contacts.diagnostics.rejectionEvents;tick=denied=stalled=0;writes=checks=cells=pairs=0;ticks=new TimingWindow(2048);
                }
                if(phase>=SIZES.length)return;
                if(!ready){if(++waiting>1200)throw new AssertionError("native client did not load complete body at phase "+phase);return;}
                if(tick==0){origin=previous=p.position();low=high=p.getY();rejections=EntityState.of(p).contacts.diagnostics.rejectionEvents;
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,new NaturalTerrainProtocol.Phase(phase,SIZES[phase],true));}
                tick++;if(tick>100){
                    var contact=EntityState.of(p).contacts;
                    if(p.onGround())grounded++;
                    if(contact.support!=null&&contact.support.complete())completeSupport++;
                    if(EntityState.of(p).pose.support()!=0)supportedFeet++;
                    reasons.merge(contact.reason,1,Integer::sum);
                    if(contact.support!=null&&java.util.stream.Stream.of(contact.support.leftContact(),contact.support.rightContact())
                        .filter(Objects::nonNull).flatMap(c->c.patches().stream()).anyMatch(patch->net.minecraft.world.level.block.Block.stateById(patch.state()).getBlock() instanceof dev.magnitude.content.CompactedSoilBlock))compactedContactTicks++;
                    ticks.add(System.nanoTime()-start);writes+=Magnitude.settings.blocksPerTick-Impact.remaining();checks+=Magnitude.settings.checksPerTick-Impact.checksRemaining();
                    cells+=524288-PhysicsWork.cellsRemaining();pairs+=524288-PhysicsWork.pairsRemaining();
                    if(EntityState.of(p).movementDenied)denied++;if(p.position().subtract(previous).horizontalDistance()<.01)stalled++;
                    low=Math.min(low,p.getY());high=Math.max(high,p.getY());
                }
                if(WorldQueryTrace.ROWS.stream().anyMatch(r->!Boolean.TRUE.equals(((Map<?,?>)r).get("complete"))))Files.writeString(Path.of("natural-server-denied-trace.json"),json.toJson(WorldQueryTrace.ROWS));
                if(tick%20==0&&!WorldQueryTrace.MOVES.isEmpty())Files.writeString(Path.of("natural-server-movement-trace.json"),json.toJson(WorldQueryTrace.MOVES));
                previous=p.position();
                if(phase==1&&tick%20==0)Files.writeString(Path.of("natural-query-trace.json"),json.toJson(WorldQueryTrace.ROWS));
                if(tick%20==0)Files.writeString(Path.of("natural-live.json"),json.toJson(Map.of("phase",phase,"tick",tick,"position",p.position().toString(),"denied",denied,"stalled",stalled,"reason",EntityState.of(p).contacts.diagnostics.failure)));
            }catch(Throwable error){error.printStackTrace();try{Files.writeString(Path.of("natural-server-results.json"),json.toJson(Map.of("success",false,"failure",error.toString(),"results",results)));}catch(Exception ignored){}phase=SIZES.length;}
        });
    }
}
