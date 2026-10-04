package dev.magnitude.physics;

import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** Bounded two-leg solve. Revalidation precedes every use of an anchor; terrain is never written. */
public final class SupportResolver {
    private SupportResolver(){}
    record Resolution(BodyPose pose,boolean verified){}
    public static BodyPose resolve(ServerPlayer player,BodyPose desired,boolean takeoff){
        return resolveChecked(player,desired,takeoff).pose();
    }
    /** The proof is consumed immediately, before another world or geometry change. */
    static Resolution resolveChecked(ServerPlayer player,BodyPose desired,boolean takeoff){
        var state=EntityState.of(player);var feet=state.contacts.feet;var scale=Dimensions.snapshot(player);
        if(takeoff) {
            var old=state.pose;
            desired=new BodyPose(desired.action(),desired.startTick(),desired.phase(),desired.support(),
                (desired.support()&1)!=0?old.leftLeg():desired.leftLeg(),(desired.support()&2)!=0?old.rightLeg():desired.rightLeg(),
                desired.leftArm(),desired.rightArm(),desired.head(),
                (desired.support()&1)!=0?old.leftKnee():desired.leftKnee(),(desired.support()&2)!=0?old.rightKnee():desired.rightKnee());
            feet.clear("takeoff");return new Resolution(desired,false);
        }
        if(!player.onGround()||player.isPassenger()||player.getAbilities().flying||player.isNoGravity()) {
            feet.clear("not grounded");return new Resolution(desired,false);
        }
        double yaw=Math.toRadians(player.getYRot()),width=scale.modelWidth(),height=PlayerBody.stanceHeight(player);
        var dimension=player.level().dimension().identifier();
        if(!dimension.equals(feet.dimension)||feet.width!=width||feet.height!=height||feet.root==null
            ||feet.root.distanceToSqr(player.position())>16*16||Math.abs(Math.IEEEremainder(yaw-feet.yaw,Math.PI*2))>.05)feet.clear("geometry changed");
        feet.dimension=dimension;feet.width=width;feet.height=height;feet.yaw=yaw;
        double reach=Math.min(1.5,height*.3);
        var left=find(player,feet.left,-1,width,height,yaw,reach);
        var right=find(player,feet.right,1,width,height,yaw,reach);
        if(!left.complete()||!right.complete()) {feet.clear("unknown surface");feet.root=player.position();return new Resolution(desired,false);}
        feet.left=left.supported()?new FootSupportState.Anchor(new Vec3(left.sole().x,left.height(),left.sole().z),left.revision()):null;
        feet.right=right.supported()?new FootSupportState.Anchor(new Vec3(right.sole().x,right.height(),right.sole().z),right.revision()):null;
        double rootY=player.getY();
        if(desired.action()==0) {
            double low=Math.min(left.supported()?left.height():Double.POSITIVE_INFINITY,right.supported()?right.height():Double.POSITIVE_INFINITY);
            if(Double.isFinite(low)&&low<rootY-1e-6&&rootY-low<=reach)rootY=low;
        }
        var proposal=pose(player,desired,feet,rootY,height,yaw);
        if(!proposal.valid()||!BodyCollision.poseAllowed(player,proposal)) {feet.clear("pose obstructed");feet.root=player.position();return new Resolution(desired,false);}
        if(rootY<player.getY()-1e-6) {
            BodyPose old=state.pose;state.pose=proposal;
            try {player.move(MoverType.SELF,new Vec3(0,rootY-player.getY(),0));}
            finally {state.pose=old;}
            proposal=pose(player,desired,feet,player.getY(),height,yaw);
            if(!proposal.valid()||!BodyCollision.poseAllowed(player,proposal)) {feet.clear("settlement denied");return new Resolution(desired,false);}
        }
        feet.root=player.position();feet.reason=proposal.support()==0?"no reachable support":"verified";
        return new Resolution(proposal,true);
    }
    private static FootContact find(ServerPlayer player,FootSupportState.Anchor old,int side,double w,double h,double yaw,double reach){
        Vec3 center=player.position().add(Math.cos(yaw)*side*w*.15,0,Math.sin(yaw)*side*w*.15);
        if(old!=null) {
            Vec3 offset=old.point().subtract(center);double lateral=offset.x*Math.cos(yaw)+offset.z*Math.sin(yaw);
            if(Math.abs(lateral)<w*.025&&offset.horizontalDistance()<h*.2)center=new Vec3(old.point().x,player.getY(),old.point().z);
        }
        return FootContacts.query(player,center,w*.09,w*.09,yaw,reach,reach);
    }
    private static BodyPose pose(ServerPlayer player,BodyPose desired,FootSupportState feet,double rootY,double height,double yaw){
        int support=desired.support();double left=desired.leftLeg(),right=desired.rightLeg(),lk=desired.leftKnee(),rk=desired.rightKnee();
        for(int side:new int[]{-1,1}) {
            int bit=side<0?1:2;var anchor=side<0?feet.left:feet.right;
            if((support&bit)==0){if(side<0)feet.left=null;else feet.right=null;continue;}
            if(anchor==null){support&=~bit;continue;}
            Vec3 delta=anchor.point().subtract(player.position());double forward=delta.x*Math.sin(yaw)-delta.z*Math.cos(yaw);
            double drop=LegKinematics.HIP*height+rootY-anchor.point().y-LegKinematics.BOOT*height;
            var joint=LegKinematics.solve(drop,forward,height);
            if(!joint.reachable()){support&=~bit;if(side<0)feet.left=null;else feet.right=null;continue;}
            if(side<0){left=joint.hip();lk=joint.knee();}else{right=joint.hip();rk=joint.knee();}
        }
        return new BodyPose(desired.action(),desired.startTick(),desired.phase(),support,left,right,desired.leftArm(),desired.rightArm(),desired.head(),lk,rk);
    }
}
