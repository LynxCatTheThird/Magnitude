package dev.magnitude.physics;

import dev.magnitude.core.EntityState;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Bounded continuous, axis-ordered movement against block voxel boxes and collidable entities. */
public final class BodyCollision {
    private BodyCollision() {}
    public static boolean active(Player player) {
        return !player.isPassenger() && (player.getPose()==Pose.STANDING || player.getPose()==Pose.CROUCHING);
    }
    public static boolean poseAllowed(Player player,BodyPose proposed) {
        var state=EntityState.of(player);BodyPose old=state.pose;
        var before=PlayerBody.parts(player,player.position());
        state.pose=proposed;
        List<BodyBox> after;
        try { after=PlayerBody.parts(player,player.position()); } finally {state.pose=old;}
        var changed=new ArrayList<BodyBox>();
        for(int i=0;i<after.size();i++) {
            var a=after.get(i);var b=before.get(i);
            if(!a.center().equals(b.center()) || !a.half().equals(b.half())
                || !a.x().equals(b.x()) || !a.y().equals(b.y()) || !a.z().equals(b.z()))changed.add(a);
        }
        if(changed.isEmpty())return true;
        var obstacles=WorldObstacles.query(player,WorldObstacles.regions(changed,Vec3.ZERO,0),false);
        if(!obstacles.complete())return false;
        for(AABB obstacle:obstacles.boxes()) {
            if(state.physicsPairs+after.size()*2>LocalProxy.PAIRS_PER_TICK || !PhysicsWork.pairs(after.size()*2))return false;
            state.physicsPairs+=after.size()*2;
            for(int i=0;i<after.size();i++)if(after.get(i).intersects(obstacle) && !before.get(i).intersects(obstacle))return false;
        }
        return true;
    }
    public static boolean permitted(Player player,Vec3 wanted) {
        if(!Double.isFinite(wanted.lengthSqr())) {fallback(player);return false;}
        if(wanted.lengthSqr()==0)return true;
        var parts=PlayerBody.parts(player,player.position());
        if(!WorldObstacles.loaded(player,WorldObstacles.regions(parts,wanted,0))) {
            EntityState.of(player).contacts.diagnostics.failure="query envelope budget or unloaded chunks";
            fallback(player);return false;
        }
        return true;
    }
    public static boolean newCollision(Player player,Vec3 oldRoot,Vec3 target) {
        // An unchanged position cannot add penetration. Accepting teleport acknowledgements
        // lets vanilla update chunk tracking before any actual movement into unknown space.
        if(Double.isFinite(oldRoot.lengthSqr())&&oldRoot.equals(target))return false;
        var before=PlayerBody.parts(player,oldRoot);var after=PlayerBody.parts(player,target);
        var obstacles=WorldObstacles.query(player,WorldObstacles.regions(after,Vec3.ZERO,0),false);
        if(!obstacles.complete()) {fallback(player);return true;}
        for(AABB obstacle:obstacles.boxes()) {
            var state=EntityState.of(player);
            if(state.physicsPairs+after.size()*2>LocalProxy.PAIRS_PER_TICK || !PhysicsWork.pairs(after.size()*2)) {fallback(player);return true;}
            state.physicsPairs+=after.size()*2;
            boolean was=false,now=false;
            for(BodyBox box:before)was|=box.intersects(obstacle);
            for(BodyBox box:after)now|=box.intersects(obstacle);
            if(now && !was){state.contacts.diagnostics.failure="new body penetration";state.contacts.diagnostics.rejected();return true;}
        }
        return false;
    }
    public record Result(Vec3 movement, boolean obstacle, boolean denied) {}
    public static Vec3 move(Player player, Vec3 wanted) {
        return MotionContacts.move(player, wanted);
    }
    public static Result solve(Player player, Vec3 wanted) {
        Vec3 movement = solveMovement(player, wanted);
        boolean denied = EntityState.of(player).movementDenied;
        return new Result(movement, !denied && player.onGround()
            && (Math.abs(movement.x-wanted.x)>1e-8 || Math.abs(movement.z-wanted.z)>1e-8), denied);
    }
    private static Vec3 solveMovement(Player player, Vec3 wanted) {
        var state=EntityState.of(player);
        state.movementDenied=false;
        long now=player.level().getGameTime();
        if(state.physicsTick!=now) { state.physicsTick=now;state.physicsCells=0;state.physicsPairs=0;state.proxyFallback=false; }
        if(!Double.isFinite(wanted.lengthSqr()))return fallback(player,wanted);
        if(wanted.lengthSqr()==0)return Vec3.ZERO;
        List<BodyBox> parts=PlayerBody.parts(player,player.position());
        double step=wanted.horizontalDistanceSqr()>0?StepPolicy.height(player):0;
        var regions=WorldObstacles.regions(parts,wanted,0);
        var query=WorldObstacles.query(player,regions,true);
        if(!query.complete())return fallback(player,wanted);
        List<AABB> obstacles=query.boxes();
        if(obstacles.isEmpty())return wanted;
        long preliminaryBroad=(long)parts.size()*obstacles.size();
        if(state.physicsPairs+preliminaryBroad>LocalProxy.PAIRS_PER_TICK||!PhysicsWork.pairs(preliminaryBroad))return fallback(player,wanted);
        state.physicsPairs+=(int)preliminaryBroad;
        var directCandidates=candidates(parts,obstacles,wanted,0);
        long preliminary=0;for(var nearby:directCandidates)preliminary+=(long)nearby.size()*3;
        if(state.physicsPairs+preliminary>LocalProxy.PAIRS_PER_TICK||!PhysicsWork.pairs(preliminary))return fallback(player,wanted);
        state.physicsPairs+=(int)preliminary;
        var ordinary=clip(parts,directCandidates,wanted);
        if(step>0&&(player.onGround()||wanted.y<0&&ordinary.y!=wanted.y)&&(ordinary.x!=wanted.x||ordinary.z!=wanted.z)){
            step=StepPolicy.height(player,parts,wanted);
            if(step==0)return ordinary;
            var extra=new ArrayList<AABB>();
            for(var region:regions)extra.add(new AABB(region.minX,region.maxY,region.minZ,region.maxX,region.maxY+step,region.maxZ));
            var above=WorldObstacles.query(player,extra,true);
            if(!above.complete())return ordinary;
            var combined=new java.util.LinkedHashSet<AABB>(obstacles);combined.addAll(above.boxes());
            if(combined.size()>1024){state.contacts.diagnostics.failure="combined obstacle count budget";return ordinary;}
            obstacles=new ArrayList<>(combined);
        }else return ordinary;

        // Charge the bounded broad phase, then SAT only for reachable part/obstacle pairs.
        long broad=(long)obstacles.size()*parts.size();
        if(state.physicsPairs+broad>LocalProxy.PAIRS_PER_TICK||!PhysicsWork.pairs(broad))return ordinary;
        state.physicsPairs+=(int)broad;
        var candidates=candidates(parts,obstacles,wanted,step);
        long pairs=0;for(var nearby:candidates)pairs+=(long)nearby.size()*12;
        if(state.physicsPairs+pairs>LocalProxy.PAIRS_PER_TICK || !PhysicsWork.pairs(pairs)) {
            state.contacts.diagnostics.failure="collision pair budget";return ordinary;
        }
        state.physicsPairs+=(int)pairs;
        Vec3 result=clip(parts,candidates,wanted);
        if(step>0 && (player.onGround() || wanted.y<0 && result.y!=wanted.y)
            && (result.x!=wanted.x || result.z!=wanted.z)) {
            Vec3 up=clip(parts,candidates,new Vec3(0,step,0));
            List<BodyBox> raised=shift(parts,up);
            Vec3 across=clip(raised,candidates,new Vec3(wanted.x,0,wanted.z));
            Vec3 down=clip(shift(raised,across),candidates,new Vec3(0,wanted.y-up.y,0));
            Vec3 candidate=up.add(across).add(down);
            if(candidate.horizontalDistanceSqr()>result.horizontalDistanceSqr())result=candidate;
        }
        return result;
    }
    private static List<List<AABB>> candidates(List<BodyBox> parts,List<AABB> obstacles,Vec3 wanted,double step){
        var candidates=new ArrayList<List<AABB>>(parts.size());
        for(var part:parts){
            var envelope=part.bounds().expandTowards(wanted).expandTowards(0,step,0).inflate(1e-7);
            var nearby=new ArrayList<AABB>();
            for(var obstacle:obstacles)if(envelope.intersects(obstacle))nearby.add(obstacle);
            candidates.add(nearby);
        }
        return candidates;
    }
    private static Vec3 fallback(Player player, Vec3 wanted) {
        var state=EntityState.of(player);state.proxyFallback=true;
        // Unknown space is never treated as air. Upward escape is handled by the
        // normal collision solver once the destination has been verified.
        state.contacts.diagnostics.rejected();state.movementDenied=true;return Vec3.ZERO;
    }
    private static void fallback(Player player) { var state=EntityState.of(player);state.proxyFallback=true;state.contacts.diagnostics.rejected();state.movementDenied=true; }
    private static List<BodyBox> shift(List<BodyBox> parts,Vec3 delta) {
        if(delta.lengthSqr()==0)return parts;
        List<BodyBox> result=new ArrayList<>(parts.size());
        for(BodyBox part:parts)result.add(part.move(delta));
        return result;
    }
    private static Vec3 clip(List<BodyBox> parts,List<List<AABB>> obstacles,Vec3 wanted) {
        double y=axis(parts,obstacles,new Vec3(0,wanted.y,0));
        Vec3 result=new Vec3(0,wanted.y*y,0);
        boolean zFirst=Math.abs(wanted.x)<Math.abs(wanted.z);
        Vec3 first=zFirst ? new Vec3(0,0,wanted.z) : new Vec3(wanted.x,0,0);
        result=result.add(first.scale(axis(shift(parts,result),obstacles,first)));
        Vec3 second=zFirst ? new Vec3(wanted.x,0,0) : new Vec3(0,0,wanted.z);
        return result.add(second.scale(axis(shift(parts,result),obstacles,second)));
    }
    private static double axis(List<BodyBox> parts,List<List<AABB>> obstacles,Vec3 movement) {
        if(movement.lengthSqr()==0)return 1;
        double fraction=1;
        for(int i=0;i<parts.size();i++)for(AABB obstacle:obstacles.get(i)) {
            fraction=Math.min(fraction,parts.get(i).sweep(obstacle,movement));
            if(fraction==0)return 0;
        }
        return fraction;
    }
}
