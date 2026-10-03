package dev.magnitude.verification;

import dev.magnitude.core.EntityState;
import dev.magnitude.physics.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Verification-only bounded trace, inactive unless the natural terrain experiment is selected. */
public final class WorldQueryTrace {
    public static final List<Object> ROWS=new ArrayList<>();
    private static final ThreadLocal<Map<String,Object>> PENDING=new ThreadLocal<>();
    public static void begin(Player player,List<AABB> regions){
        if(!Boolean.getBoolean("magnitude.queryTrace")||!Boolean.getBoolean("magnitude.naturalTerrainVerification")||!player.getName().getString().equals("NaturalWalker")||ROWS.size()>=64)return;
        var row=new LinkedHashMap<String,Object>();row.put("tick",player.level().getGameTime());row.put("before",EntityState.of(player).physicsCells);row.put("position",player.position().toString());
        row.put("requested",regions.stream().mapToLong(LocalProxy::cells).sum());row.put("scale",dev.magnitude.core.Dimensions.snapshot(player).base());
        row.put("caller",StackWalker.getInstance().walk(s->s.filter(f->f.getClassName().equals("dev.magnitude.physics.BodyCollision")).map(StackWalker.StackFrame::getMethodName).findFirst().orElse("other")));
        PENDING.set(row);
    }
    public static void wanted(net.minecraft.world.phys.Vec3 wanted){var row=PENDING.get();if(row!=null)row.put("wanted",wanted.toString());}
    public static void end(Player player,WorldObstacles.Result result){
        var row=PENDING.get();if(row==null)return;PENDING.remove();row.put("after",EntityState.of(player).physicsCells);row.put("complete",result.complete());row.put("boxes",result.boxes().size());row.put("failure",EntityState.of(player).contacts.diagnostics.failure);ROWS.add(row);
    }
}
