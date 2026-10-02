package dev.magnitude.physics;

import dev.magnitude.core.EntityState;
import dev.magnitude.interaction.EntityQueries;
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
        AABB region=after.getFirst().bounds();for(var box:after)region=region.minmax(box.bounds());
        if(!LocalProxy.loaded(player.level(),region) || !PhysicsWork.cells(LocalProxy.cells(region)))return false;
        int count=0;
        for(var shape:player.level().getBlockCollisions(player,region))for(AABB obstacle:shape.toAabbs()) {
            if(++count>1024 || !PhysicsWork.pairs(12))return false;
            for(int i=0;i<after.size();i++)if(after.get(i).intersects(obstacle) && !before.get(i).intersects(obstacle))return false;
        }
        return true;
    }
    public static boolean permitted(Player player,Vec3 wanted) {
        if(!Double.isFinite(wanted.lengthSqr())) {fallback(player);return false;}
        var parts=PlayerBody.parts(player,player.position());AABB region=parts.getFirst().bounds();
        for(var part:parts)region=region.minmax(part.bounds());
        region=region.expandTowards(wanted).expandTowards(0,Math.min(4.8,player.maxUpStep()),0).inflate(1e-7);
        if(!LocalProxy.loaded(player.level(),region)) {fallback(player);return false;}
        return true;
    }
    public static boolean newCollision(Player player,Vec3 oldRoot,Vec3 target) {
        var before=PlayerBody.parts(player,oldRoot);var after=PlayerBody.parts(player,target);
        AABB region=after.getFirst().bounds();for(BodyBox box:after)region=region.minmax(box.bounds());
        long cells=LocalProxy.cells(region);
        if(!LocalProxy.loaded(player.level(),region) || !PhysicsWork.cells(cells)) {fallback(player);return true;}
        int pairs=0;
        for(var shape:player.level().getBlockCollisions(player,region))for(AABB obstacle:shape.toAabbs()) {
            if(++pairs>1024 || !PhysicsWork.pairs(12)) {fallback(player);return true;}
            boolean was=false,now=false;
            for(BodyBox box:before)was|=box.intersects(obstacle);
            for(BodyBox box:after)now|=box.intersects(obstacle);
            if(now && !was)return true;
        }
        return false;
    }
    public static Vec3 move(Player player, Vec3 wanted) {
        var state=EntityState.of(player);
        state.movementDenied=false;
        long now=player.level().getGameTime();
        if(state.physicsTick!=now) { state.physicsTick=now;state.physicsCells=0;state.physicsPairs=0;state.proxyFallback=false; }
        if(!Double.isFinite(wanted.lengthSqr()))return fallback(player);
        List<BodyBox> parts=PlayerBody.parts(player,player.position());
        AABB bounds=parts.getFirst().bounds();
        for(BodyBox part:parts)bounds=bounds.minmax(part.bounds());
        double step=Math.min(4.8,player.maxUpStep());
        AABB swept=bounds.expandTowards(wanted).expandTowards(0,step,0).inflate(1e-7);
        long cells=LocalProxy.cells(swept);
        if(cells>LocalProxy.CELLS_PER_MOVE || state.physicsCells+cells>LocalProxy.CELLS_PER_TICK || !LocalProxy.loaded(player.level(),swept) || !PhysicsWork.cells(cells))return fallback(player);
        state.physicsCells+=(int)cells;
        List<AABB> obstacles=new ArrayList<>();
        for(var shape:player.level().getBlockCollisions(player,swept)) {
            for(AABB box:shape.toAabbs()) { if(obstacles.size()>=1024)return fallback(player);obstacles.add(box); }
        }
        var entities=EntityQueries.query(player.level(),swept,player,256);
        if(!entities.complete())return fallback(player);
        for(var entity:entities.entities())if(player.canCollideWith(entity))obstacles.add(entity.getBoundingBox());
        var border=player.level().getWorldBorder();
        if(border.isInsideCloseToBorder(player,swept))obstacles.addAll(border.getCollisionShape().toAabbs());
        // Charge all SAT pairs including empty/failed contacts, before doing any work.
        long pairs=(long)obstacles.size()*parts.size()*12;
        if(state.physicsPairs+pairs>LocalProxy.PAIRS_PER_TICK || !PhysicsWork.pairs(pairs))return fallback(player);
        state.physicsPairs+=(int)pairs;
        Vec3 result=clip(parts,obstacles,wanted);
        if(step>0 && (player.onGround() || wanted.y<0 && result.y!=wanted.y)
            && (result.x!=wanted.x || result.z!=wanted.z)) {
            Vec3 up=clip(parts,obstacles,new Vec3(0,step,0));
            List<BodyBox> raised=shift(parts,up);
            Vec3 across=clip(raised,obstacles,new Vec3(wanted.x,0,wanted.z));
            Vec3 down=clip(shift(raised,across),obstacles,new Vec3(0,wanted.y-up.y,0));
            Vec3 candidate=up.add(across).add(down);
            if(candidate.horizontalDistanceSqr()>result.horizontalDistanceSqr())result=candidate;
        }
        return result;
    }
    private static Vec3 fallback(Player player) { var state=EntityState.of(player);state.proxyFallback=true;state.movementDenied=true;return Vec3.ZERO; }
    private static List<BodyBox> shift(List<BodyBox> parts,Vec3 delta) { return parts.stream().map(p->p.move(delta)).toList(); }
    private static Vec3 clip(List<BodyBox> parts,List<AABB> obstacles,Vec3 wanted) {
        double y=axis(parts,obstacles,new Vec3(0,wanted.y,0));
        Vec3 result=new Vec3(0,wanted.y*y,0);
        boolean zFirst=Math.abs(wanted.x)<Math.abs(wanted.z);
        Vec3 first=zFirst ? new Vec3(0,0,wanted.z) : new Vec3(wanted.x,0,0);
        result=result.add(first.scale(axis(shift(parts,result),obstacles,first)));
        Vec3 second=zFirst ? new Vec3(wanted.x,0,0) : new Vec3(0,0,wanted.z);
        return result.add(second.scale(axis(shift(parts,result),obstacles,second)));
    }
    private static double axis(List<BodyBox> parts,List<AABB> obstacles,Vec3 movement) {
        if(movement.lengthSqr()==0)return 1;
        double fraction=1;
        for(BodyBox part:parts)for(AABB obstacle:obstacles)fraction=Math.min(fraction,part.sweep(obstacle,movement));
        return fraction;
    }
}
