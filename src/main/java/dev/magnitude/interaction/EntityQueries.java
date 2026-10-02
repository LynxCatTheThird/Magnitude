package dev.magnitude.interaction;

import dev.magnitude.core.Rules;
import dev.magnitude.mixin.LevelEntitiesMixin;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Continuation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;

/** Aborts spatial iteration before building an unbounded list of candidates. */
public final class EntityQueries {
    private static final Rules.Budget CANDIDATES = new Rules.Budget();
    private static final Rules.Budget QUERIES = new Rules.Budget();
    private EntityQueries() {}
    public static void beginTick() { CANDIDATES.reset(2048);QUERIES.reset(128); }
    public static int remaining() { return CANDIDATES.remaining(); }
    public static List<Entity> nearby(ServerLevel level, AABB box, Entity except, int limit) {
        if (limit<=0 || CANDIDATES.remaining()==0 || !QUERIES.take()) return List.of();
        int maximum=Math.min(256,limit);
        List<Entity> result=new ArrayList<>(maximum);
        ((LevelEntitiesMixin)level).magnitude$entities().get(EntityTypeTest.forClass(Entity.class),box,entity -> {
            if (!CANDIDATES.take()) return Continuation.ABORT;
            if (entity!=except) result.add(entity);
            return Continuation.abortIf(result.size()>=maximum || CANDIDATES.remaining()==0);
        });
        return result;
    }
}
