package dev.magnitude.interaction;

import dev.magnitude.core.Rules;
import dev.magnitude.mixin.LevelEntitiesMixin;
import net.minecraft.world.level.Level;
import net.minecraft.util.Continuation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;

/** Aborts spatial iteration before building an unbounded list of candidates. */
public final class EntityQueries {
    private record Budgets(Rules.Budget candidates, Rules.Budget queries) {}
    private static final ThreadLocal<Budgets> BUDGETS=ThreadLocal.withInitial(()->new Budgets(new Rules.Budget(),new Rules.Budget()));
    private EntityQueries() {}
    public static void beginTick() { BUDGETS.get().candidates().reset(2048);BUDGETS.get().queries().reset(128); }
    public static int remaining() { return BUDGETS.get().candidates().remaining(); }
    public record Result(List<Entity> entities, boolean complete) {}
    public static List<Entity> nearby(Level level,AABB box,Entity except,int limit) {return query(level,box,except,limit).entities();}
    public static Result query(Level level, AABB box, Entity except, int limit) {
        if (limit<=0 || BUDGETS.get().candidates().remaining()==0 || !BUDGETS.get().queries().take()) return new Result(List.of(),false);
        // Bound section traversal even when there are no candidates at all.
        double cells=(Math.ceil(box.getXsize()/16)+2)*(Math.ceil(box.getYsize()/16)+2)*(Math.ceil(box.getZsize()/16)+2);
        if(!Double.isFinite(cells) || cells>4096)return new Result(List.of(),false);
        int maximum=Math.min(256,limit);
        List<Entity> result=new ArrayList<>(maximum);
        ((LevelEntitiesMixin)level).magnitude$entities().get(EntityTypeTest.forClass(Entity.class),box,entity -> {
            if (!BUDGETS.get().candidates().take()) return Continuation.ABORT;
            if (entity!=except) result.add(entity);
            return Continuation.abortIf(result.size()>=maximum || BUDGETS.get().candidates().remaining()==0);
        });
        return new Result(result,result.size()<maximum && BUDGETS.get().candidates().remaining()>0);
    }
}
