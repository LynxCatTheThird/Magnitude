package dev.magnitude.verification;

import dev.magnitude.core.EntityState;
import dev.magnitude.physics.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Verification-only bounded trace, inactive unless the natural terrain experiment is selected. */
public final class WorldQueryTrace {
    public static final List<Object> ROWS=new ArrayList<>();
    public static final List<Object> MOVES=new ArrayList<>();
    public static final List<Object> POSES=new ArrayList<>();
    private static final ThreadLocal<Long> POSITION_TICK=ThreadLocal.withInitial(()->Long.MIN_VALUE);
    public static void positionBegin(){LAST.set(List.of());}
    public static void solveBegin(){OBSTACLES.set(List.of());}
    private static final ThreadLocal<List<AABB>> LAST=ThreadLocal.withInitial(List::of);
    private static boolean enabled(Player player){return Boolean.getBoolean("magnitude.queryTrace")&&(Boolean.getBoolean("magnitude.naturalTerrainVerification")&&player.getName().getString().equals("NaturalWalker")
            ||Boolean.getBoolean("magnitude.stepNetworkVerification")&&player.getName().getString().equals("StepWalker"));}
    private static final ThreadLocal<List<AABB>> OBSTACLES=ThreadLocal.withInitial(List::of);
    private static final ThreadLocal<Map<String,Object>> PENDING=new ThreadLocal<>();
    public static void begin(Player player,List<AABB> regions){
        PENDING.remove();
        if(!enabled(player)||ROWS.size()>=64)return;
        var row=new LinkedHashMap<String,Object>();row.put("tick",player.level().getGameTime());row.put("before",EntityState.of(player).physicsCells);row.put("position",player.position().toString());
        row.put("requested",regions.stream().mapToLong(LocalProxy::cells).sum());row.put("scale",dev.magnitude.core.Dimensions.snapshot(player).base());
        row.put("caller",StackWalker.getInstance().walk(s->s.filter(f->f.getClassName().equals("dev.magnitude.physics.BodyCollision")&&!f.getMethodName().startsWith("handler$")).map(StackWalker.StackFrame::getMethodName).findFirst().orElse("other")));
        PENDING.set(row);
    }
    public static void wanted(net.minecraft.world.phys.Vec3 wanted){var row=PENDING.get();if(row!=null)row.put("wanted",wanted.toString());}
    public static void solved(Player player,net.minecraft.world.phys.Vec3 wanted,BodyCollision.Result result){
        if(!enabled(player)||MOVES.size()>=16)return;
        var row=new LinkedHashMap<String,Object>();row.put("tick",player.level().getGameTime());row.put("position",player.position().toString());row.put("wanted",wanted.toString());row.put("result",result.movement().toString());row.put("denied",result.denied());row.put("pose",EntityState.of(player).pose.toString());
        var parts=PlayerBody.parts(player,player.position());var hits=new ArrayList<Object>();
        var horizontal=new net.minecraft.world.phys.Vec3(wanted.x,0,wanted.z);
        for(int index=0;index<parts.size();index++)for(var box:OBSTACLES.get()){
            var part=parts.get(index).move(new net.minecraft.world.phys.Vec3(0,result.movement().y,0));double fraction=part.sweep(box,horizontal);
            if(fraction<1&&hits.size()<16)hits.add(Map.of("part",index,"box",box.toString(),"fraction",fraction,"afterStep",part.move(new net.minecraft.world.phys.Vec3(0,StepPolicy.height(player),0)).sweep(box,horizontal),"block",player.level().getBlockState(net.minecraft.core.BlockPos.containing(box.getCenter())).toString()));
        }
        var verticalHits=new ArrayList<Object>();
        if(wanted.y<0)for(int index=0;index<parts.size();index++)for(var box:OBSTACLES.get()){
            double fraction=parts.get(index).sweep(box,new net.minecraft.world.phys.Vec3(0,wanted.y,0));
            if(fraction<1&&verticalHits.size()<16)verticalHits.add(Map.of("part",index,"box",box.toString(),"fraction",fraction));
        }
        row.put("verticalHits",verticalHits);row.put("hits",hits);MOVES.add(row);
    }
    public static void positionChecked(Player player,net.minecraft.world.phys.Vec3 oldRoot,net.minecraft.world.phys.Vec3 target,boolean rejected){
        if(!enabled(player)||!rejected||POSITION_TICK.get()==player.level().getGameTime())return;
        POSITION_TICK.set(player.level().getGameTime());
        var row=new LinkedHashMap<String,Object>();row.put("tick",player.level().getGameTime());row.put("old",oldRoot.toString());row.put("target",target.toString());row.put("current",player.position().toString());row.put("pose",EntityState.of(player).pose.toString());row.put("failure",EntityState.of(player).contacts.diagnostics.failure);row.put("queries",new ArrayList<>(ROWS));row.put("moves",new ArrayList<>(MOVES));row.put("takeoffTick",EntityState.of(player).contacts.takeoffTick);row.put("grounded",player.onGround());
        var before=PlayerBody.parts(player,oldRoot);var after=PlayerBody.parts(player,target);var hits=new ArrayList<Object>();
        for(var box:LAST.get()){
            boolean was=before.stream().anyMatch(p->p.intersects(box));
            for(int i=0;i<after.size();i++)if(after.get(i).intersects(box)&&hits.size()<32)hits.add(Map.of("part",i,"was",was,"box",box.toString(),"block",player.level().getBlockState(net.minecraft.core.BlockPos.containing(box.getCenter())).toString()));
        }
        row.put("hits",hits);
        try{java.nio.file.Files.writeString(java.nio.file.Path.of("natural-position-rejection.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(row));}catch(java.io.IOException e){throw new RuntimeException(e);}
    }
    public static void poseChecked(Player player,BodyPose proposed,boolean allowed){
        if(!enabled(player)||allowed||POSES.size()>=16)return;
        var before=PlayerBody.parts(player,player.position());var state=EntityState.of(player);var old=state.pose;
        java.util.List<BodyBox> after;state.pose=proposed;
        try{after=PlayerBody.parts(player,player.position());}finally{state.pose=old;}
        var hits=new ArrayList<Object>();
        for(var box:LAST.get())for(int i=0;i<after.size()&&hits.size()<16;i++)if(after.get(i).intersects(box)&&!before.get(i).intersects(box))
            hits.add(Map.of("part",i,"box",box.toString()));
        POSES.add(Map.of("tick",player.level().getGameTime(),"position",player.position().toString(),"old",old.toString(),"proposal",proposed.toString(),"hits",hits,"failure",state.contacts.diagnostics.failure));
    }
    public static void end(Player player,WorldObstacles.Result result){
        var row=PENDING.get();if(row==null)return;PENDING.remove();LAST.set(result.boxes());if(row.get("caller").equals("solveMovement")){var joined=new LinkedHashSet<AABB>(OBSTACLES.get());joined.addAll(result.boxes());OBSTACLES.set(new ArrayList<>(joined));}row.put("after",EntityState.of(player).physicsCells);row.put("complete",result.complete());row.put("boxes",result.boxes().size());row.put("failure",EntityState.of(player).contacts.diagnostics.failure);ROWS.add(row);
    }
}
