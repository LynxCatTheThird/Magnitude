package dev.magnitude.physics;

import dev.magnitude.core.EntityState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;

/** Explicit server adapter API. Visual resources alone never control world physics. */
public final class BodyPoses {
    @FunctionalInterface public interface Adapter { BodyPose sample(ServerPlayer player, BodyPose canonical); }
    private static final Map<Identifier, Adapter> ADAPTERS = new HashMap<>();
    private BodyPoses() {}
    public static void register(Identifier id, Adapter adapter) {
        if (id == null || adapter == null || ADAPTERS.putIfAbsent(id,adapter) != null) throw new IllegalArgumentException("Duplicate or invalid pose adapter");
    }
    /** Call on the server thread; selection is temporary and clears on load/respawn. */
    public static boolean select(ServerPlayer player, Identifier id) {
        if (id != null && !ADAPTERS.containsKey(id)) return false;
        EntityState.of(player).poseAdapter = id;
        return true;
    }
    public static BodyPose update(ServerPlayer player, double distance, boolean takeoff) {
        var state = EntityState.of(player);
        long now = player.level().getGameTime();
        boolean grounded = SupportResolver.maySettle(player) && !player.isPassenger() && !player.getAbilities().flying && !player.isNoGravity();
        takeoff &= grounded&&player.onGround();
        if (distance > 0.001 && distance <= 16) state.posePhase = (state.posePhase + distance / dev.magnitude.core.Dimensions.snapshot(player).stride() * Math.PI) % (Math.PI*2);
        int support = grounded ? distance > 0.001 ? state.posePhase < Math.PI ? 1 : 2 : 3 : 0;
        int action = grounded ? distance > 0.001 ? 1 : 0 : 3;
        if (takeoff) { action=2;support=(state.jumpSequence++ & 1)==0 ? 2 : 1;state.posePhase=support==1 ? 0 : Math.PI; }
        if(!grounded && state.pose.action()==2 && now-state.poseStart<4)action=2;
        if (state.pose.action()!=action || takeoff) state.poseStart=now;
        double swing = action==1 ? Math.sin(state.posePhase)*LegKinematics.WALK_SWING : action==2 ? 0.55 : 0;
        int leg=action==2 ? (state.jumpSequence&1)==1 ? 2 : 1 : support;
        double leftLeg=(leg&1)!=0 ? 0 : Math.abs(swing),rightLeg=(leg&2)!=0 ? 0 : Math.abs(swing);
        BodyPose canonical = new BodyPose(action,state.poseStart,state.posePhase,support,leftLeg,rightLeg,swing,-swing,Math.clamp(Math.toRadians(player.getXRot()),-1.2,1.2));
        BodyPose result=canonical;
        Adapter adapter=ADAPTERS.get(state.poseAdapter);
        if (adapter!=null) {
            try {
                BodyPose supplied=adapter.sample(player,canonical);
                if (supplied!=null && supplied.valid() && supplied.action()==canonical.action() && supplied.startTick()==canonical.startTick()
                    && (grounded ? takeoff ? supplied.support()==1 || supplied.support()==2 : supplied.support()!=0 : supplied.support()==0) && boundedChange(state.pose,supplied,takeoff)) result=supplied;
            } catch (RuntimeException ignored) { /* A broken adapter falls back to the server pose. */ }
        }
        var resolution=SupportResolver.resolveChecked(player,result,takeoff);
        result=resolution.pose();
        if(changed(state.pose,result) && !resolution.verified() && !BodyCollision.poseAllowed(player,result)) {
            BodyPose old=state.pose;
            int touching=0;boolean unknown=false;
            if(grounded)for(int side:new int[]{-1,1}){
                var contact=FootContacts.capture(player,side);unknown|=!contact.complete();
                if(contact.supported()&&Math.abs(contact.height()-contact.sole().y)<1e-6)touching|=side<0?1:2;
            }
            // Unknown contact cannot justify switching feet. Keep the previous native
            // grounded geometry; a known empty contact does not get this fallback.
            int retainedSupport=unknown&&player.onGround()?old.support():result.support() & touching;
            if(grounded && retainedSupport==0)retainedSupport=old.support() & touching;
            if(grounded && retainedSupport==0)retainedSupport=touching;
            result=new BodyPose(result.action(),result.startTick(),result.phase(),retainedSupport,old.leftLeg(),old.rightLeg(),old.leftArm(),old.rightArm(),old.head(),old.leftKnee(),old.rightKnee());
        }
        state.pose=result;
        return result;
    }
    private static boolean changed(BodyPose a,BodyPose b) {
        return Math.abs(a.leftLeg()-b.leftLeg())>1e-4 || Math.abs(a.rightLeg()-b.rightLeg())>1e-4
            || Math.abs(a.leftArm()-b.leftArm())>1e-4 || Math.abs(a.rightArm()-b.rightArm())>1e-4 || Math.abs(a.head()-b.head())>1e-4
            || Math.abs(a.leftKnee()-b.leftKnee())>1e-4 || Math.abs(a.rightKnee()-b.rightKnee())>1e-4;
    }
    private static boolean boundedChange(BodyPose old, BodyPose next, boolean takeoff) {
        double limit=takeoff ? 1.2 : 0.8;
        return Math.abs(old.leftLeg()-next.leftLeg())<=limit && Math.abs(old.rightLeg()-next.rightLeg())<=limit
            && Math.abs(old.leftArm()-next.leftArm())<=limit && Math.abs(old.rightArm()-next.rightArm())<=limit && Math.abs(old.head()-next.head())<=limit
            && Math.abs(old.leftKnee()-next.leftKnee())<=limit&&Math.abs(old.rightKnee()-next.rightKnee())<=limit;
    }
}
