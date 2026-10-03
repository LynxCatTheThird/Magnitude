package dev.magnitude.client.visual;

import dev.magnitude.network.*;
import dev.magnitude.visual.SoleGeometry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import java.util.*;

/** Local clipped surface patches. No entities, particles, world writes or collision overrides. */
public final class FootprintRenderer {
    public static boolean enabled=true;
    public static double distance=128;
    public static int cacheLimit=2048;
    private static final ArrayDeque<Task> pending=new ArrayDeque<>(16);
    private static final LinkedHashMap<Long,Patch> patches=new LinkedHashMap<>();
    private static final LinkedHashMap<EventKey,Boolean> seen=new LinkedHashMap<>();
    private static List<Quad> frame=List.of();
    private static final net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey<List<Quad>> FRAME=net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey.create();
    private static net.minecraft.resources.Identifier dimension;
    private static boolean subscribed;
    private static long nextSubscription;
    public static int lastChecks,lastDrawn,dropped;
    private record EventKey(UUID actor,long sequence,int side){}
    private record Patch(BlockPos pos,int state,List<SoleGeometry.Point> polygon,long expires){}
    private record Quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color){}
    private static final class Task {
        final FootprintPayload payload;final SoleGeometry.Cursor cursor;final BlockPos origin;final EventKey key;final long expires;
        Task(FootprintPayload p,long tick){payload=p;cursor=new SoleGeometry.Cursor(p.width(),p.length());origin=BlockPos.containing(p.center());key=new EventKey(p.actor(),p.sequence(),p.side());expires=tick+600;}
    }
    private FootprintRenderer(){}
    public static void invalidate(BlockPos pos){patches.remove(pos.asLong());patches.remove(pos.below().asLong());}
    public static boolean hasSurface(BlockPos pos){return patches.containsKey(pos.asLong());}
    public static int cached(){return patches.size();}
    public static int queued(){return pending.size();}
    public static void register(){
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->reset());
        ClientPlayNetworking.registerGlobalReceiver(FootprintPayload.TYPE,(payload,context)->accept(payload));
        ClientTickEvents.END_CLIENT_TICK.register(client->tick(client));
        LevelExtractionEvents.END_EXTRACTION.register(context->{extract(context.level(),context.camera().position());context.levelState().setData(FRAME,frame);});
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{
            var quads=context.levelState().getDataOrDefault(FRAME,List.of());if(quads.isEmpty())return;
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(),RenderTypes.debugQuads(),(pose,vertices)->{
                for(var q:quads){vertex(vertices,pose,q.a,q.color);vertex(vertices,pose,q.b,q.color);vertex(vertices,pose,q.c,q.color);vertex(vertices,pose,q.d,q.color);}
            });
        });
    }
    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer consumer,com.mojang.blaze3d.vertex.PoseStack.Pose pose,Vec3 p,int color){consumer.addVertex(pose,(float)p.x,(float)p.y,(float)p.z).setColor(color);}
    public static void reset(){dimension=null;pending.clear();patches.clear();seen.clear();frame=List.of();subscribed=false;nextSubscription=0;lastChecks=lastDrawn=dropped=0;}
    public static boolean accept(FootprintPayload payload){
        var client=Minecraft.getInstance();if(!enabled||client.level==null||!payload.valid()||!payload.dimension().equals(client.level.dimension().identifier()))return false;
        if(!payload.dimension().equals(dimension)){pending.clear();patches.clear();seen.clear();frame=List.of();dimension=payload.dimension();}
        var key=new EventKey(payload.actor(),payload.sequence(),payload.side());
        if(seen.putIfAbsent(key,true)!=null)return false;
        while(seen.size()>1024)seen.remove(seen.keySet().iterator().next());
        if(pending.size()>=16){pending.removeFirst();dropped++;}
        pending.addLast(new Task(payload,client.level.getGameTime()));return true;
    }
    private static void tick(Minecraft client){
        if(client.level==null)return;
        if(!client.level.dimension().identifier().equals(dimension)){pending.clear();patches.clear();seen.clear();frame=List.of();dimension=client.level.dimension().identifier();}
        long now=client.level.getGameTime();
        if(subscribed!=enabled&&now>=nextSubscription&&ClientPlayNetworking.canSend(VisualSubscription.TYPE)){
            ClientPlayNetworking.send(new VisualSubscription(enabled));subscribed=enabled;nextSubscription=now+10;
        }
        if(!enabled){pending.clear();patches.clear();frame=List.of();lastChecks=lastDrawn=0;return;}
        patches.values().removeIf(p->p.expires<now);
        while(patches.size()>cacheLimit)patches.remove(patches.keySet().iterator().next());
        int checks=0;
        // A bounded rotating validation pass handles chunk replacement/unload and missed notifications.
        int validate=Math.min(64,patches.size());
        for(int i=0;i<validate;i++) {
            var entry=patches.entrySet().iterator().next();var key=entry.getKey();var patch=entry.getValue();patches.remove(key);checks++;
            var pos=patch.pos;
            if(loaded(client.level,pos)&&Block.getId(client.level.getBlockState(pos))==patch.state
                &&client.level.getBlockState(pos.above()).getCollisionShape(client.level,pos.above()).isEmpty())patches.put(key,patch);
        }
        int unknown=0;
        while(!pending.isEmpty()&&checks<256){
            var task=pending.removeFirst();if(task.expires<now)continue;
            if(!loaded(client.level,task.origin)) {pending.addLast(task);if(++unknown>=pending.size())break;continue;}
            unknown=0;
            // Fair per-task slice; each visit counts even outside the sole or in unknown space.
            for(int slice=0;slice<32&&!task.cursor.complete()&&checks<256;slice++){
                checks++;int[] offset=task.cursor.next();var pos=task.origin.offset(offset[0],0,offset[1]);
                var polygon=SoleGeometry.clip(task.payload.center().x-pos.getX(),task.payload.center().z-pos.getZ(),task.payload.width(),task.payload.length(),task.payload.yaw());
                if(polygon.size()<3||!loaded(client.level,pos))continue;
                var state=client.level.getBlockState(pos);
                if(!state.isCollisionShapeFullBlock(client.level,pos)||!client.level.getBlockState(pos.above()).getCollisionShape(client.level,pos.above()).isEmpty()||!state.getFluidState().isEmpty())continue;
                patches.put(pos.asLong(),new Patch(pos.immutable(),Block.getId(state),polygon,task.expires));
                while(patches.size()>cacheLimit)patches.remove(patches.keySet().iterator().next());
            }
            if(!task.cursor.complete())pending.addLast(task);
        }
        lastChecks=checks;
    }
    private static void extract(net.minecraft.client.multiplayer.ClientLevel level,Vec3 camera){
        if(!enabled){frame=List.of();lastDrawn=0;return;}
        var quads=new ArrayList<Quad>(1024);var iterator=patches.entrySet().iterator();int checked=0;
        while(iterator.hasNext()&&checked++<cacheLimit){
            var patch=iterator.next().getValue();var pos=patch.pos;
            if(pos.distToCenterSqr(camera)>distance*distance)continue;
            if(!loaded(level,pos))continue;
            var polygon=patch.polygon;Vec3 first=point(pos,polygon.getFirst(),camera);
            double fade=Math.clamp((patch.expires-level.getGameTime())/100d,0,1);int color=((int)(fade*90)<<24)|0x453720;
            for(int i=1;i<polygon.size()-1&&quads.size()<4096;i++){
                Vec3 b=point(pos,polygon.get(i),camera),c=point(pos,polygon.get(i+1),camera);quads.add(new Quad(first,b,c,c,color));
            }
            if(quads.size()>=4096)break;
        }
        lastDrawn=quads.size();frame=List.copyOf(quads);
    }
    private static boolean loaded(net.minecraft.client.multiplayer.ClientLevel level,BlockPos pos){
        // ClientLevel.hasChunk() always returns true; request=false distinguishes missing chunks.
        return level.getChunkSource().getChunk(pos.getX()>>4,pos.getZ()>>4,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)!=null;
    }
    private static Vec3 point(BlockPos pos,SoleGeometry.Point p,Vec3 camera){return new Vec3(pos.getX()+p.x()-camera.x,pos.getY()+1.003-camera.y,pos.getZ()+p.z()-camera.z);}
}
