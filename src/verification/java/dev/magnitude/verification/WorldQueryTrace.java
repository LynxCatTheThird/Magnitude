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
    private static final ThreadLocal<List<AABB>> OBSTACLES=ThreadLocal.withInitial(List::of);
    private static final ThreadLocal<Map<String,Object>> PENDING=new ThreadLocal<>();
    public static void begin(Player player,List<AABB> regions){
        if(!Boolean.getBoolean("magnitude.queryTrace")||!Boolean.getBoolean("magnitude.naturalTerrainVerification")||!player.getName().getString().equals("NaturalWalker")||ROWS.size()>=64)return;
        var row=new LinkedHashMap<String,Object>();row.put("tick",player.level().getGameTime());row.put("before",EntityState.of(player).physicsCells);row.put("position",player.position().toString());
        row.put("requested",regions.stream().mapToLong(LocalProxy::cells).sum());row.put("scale",dev.magnitude.core.Dimensions.snapshot(player).base());
        row.put("caller",StackWalker.getInstance().walk(s->s.filter(f->f.getClassName().equals("dev.magnitude.physics.BodyCollision")&&!f.getMethodName().startsWith("handler$")).map(StackWalker.StackFrame::getMethodName).findFirst().orElse("other")));
        PENDING.set(row);
    }
    public static void wanted(net.minecraft.world.phys.Vec3 wanted){var row=PENDING.get();if(row!=null)row.put("wanted",wanted.toString());}
    public static void solved(Player player,net.minecraft.world.phys.Vec3 wanted,BodyCollision.Result result){
        if(!Boolean.getBoolean("magnitude.queryTrace")||MOVES.size()>=16)return;
        var row=new LinkedHashMap<String,Object>();row.put("tick",player.level().getGameTime());row.put("position",player.position().toString());row.put("wanted",wanted.toString());row.put("result",result.movement().toString());row.put("denied",result.denied());row.put("pose",EntityState.of(player).pose.toString());
        var parts=PlayerBody.parts(player,player.position());var hits=new ArrayList<Object>();
        var horizontal=new net.minecraft.world.phys.Vec3(wanted.x,0,wanted.z);
        for(int index=0;index<parts.size();index++)for(var box:OBSTACLES.get()){
            var part=parts.get(index).move(new net.minecraft.world.phys.Vec3(0,result.movement().y,0));double fraction=part.sweep(box,horizontal);
            if(fraction<1&&hits.size()<16)hits.add(Map.of("part",index,"box",box.toString(),"fraction",fraction,"afterStep",part.move(new net.minecraft.world.phys.Vec3(0,StepPolicy.height(player),0)).sweep(box,horizontal),"block",player.level().getBlockState(net.minecraft.core.BlockPos.containing(box.getCenter())).toString()));
        }
        row.put("hits",hits);MOVES.add(row);
    }
    public static void end(Player player,WorldObstacles.Result result){
        var row=PENDING.get();if(row==null)return;PENDING.remove();if(row.get("caller").equals("solveMovement"))OBSTACLES.set(result.boxes());row.put("after",EntityState.of(player).physicsCells);row.put("complete",result.complete());row.put("boxes",result.boxes().size());row.put("failure",EntityState.of(player).contacts.diagnostics.failure);ROWS.add(row);
    }
}
