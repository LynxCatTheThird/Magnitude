package dev.magnitude.physics;

import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Ten internal body parts, no additional entities. Geometry stays in local double coordinates. */
public final class PlayerBody {
    private PlayerBody() {}
    public static List<BodyBox> parts(Player player, Vec3 root) {
        var scale = Dimensions.snapshot(player);
        // Model coefficients already include vanilla scale; only apply the stance ratio.
        return parts(player,root,scale.modelWidth(),stanceHeight(player));
    }
    public static double stanceHeight(Player player){
        return Dimensions.snapshot(player).modelHeight()*(player.getPose()==net.minecraft.world.entity.Pose.CROUCHING?1.5/1.8:1);
    }
    /** The implicit full body is available without visiting its world-sized bounding volume. */
    public static List<BodyBox> actualParts(Player player) {
        var scale = Dimensions.snapshot(player);
        return parts(player,player.position(),scale.modelWidth(),scale.modelHeight());
    }
    private static List<BodyBox> parts(Player player,Vec3 root,double width,double height) {
        BodyPose pose=EntityState.of(player).pose;
        return List.of(box(player,root,0,1.57,0,.21,.23,.21,width,height,pose.head()),
            box(player,root,0,1.05,0,.21,.29,.12,width,height,0),
            limb(player,root,-.15,LegKinematics.HIP,LegKinematics.THIGH/2,.09,width,height,pose.leftLeg()),
            limb(player,root,.15,LegKinematics.HIP,LegKinematics.THIGH/2,.09,width,height,pose.rightLeg()),
            shin(player,root,-1,width,height,pose.leftLeg(),pose.leftKnee()),
            shin(player,root,1,width,height,pose.rightLeg(),pose.rightKnee()),
            boot(player,root,-1,width,height,pose.leftLeg(),pose.leftKnee()),
            boot(player,root,1,width,height,pose.rightLeg(),pose.rightKnee()),
            limb(player,root,-.24,1.32,.24,.055,width,height,pose.leftArm()),
            limb(player,root,.24,1.32,.24,.055,width,height,pose.rightArm()));
    }
    private static BodyBox shin(Player p,Vec3 root,int side,double w,double h,double hip,double knee){
        double lower=hip+knee;
        return box(p,root,side*.15,LegKinematics.HIP-LegKinematics.THIGH*Math.cos(hip)-LegKinematics.SHIN/2*Math.cos(lower),
            -(LegKinematics.THIGH*Math.sin(hip)+LegKinematics.SHIN/2*Math.sin(lower))*h/w,.09,LegKinematics.SHIN/2,.09,w,h,lower);
    }
    private static BodyBox boot(Player p,Vec3 root,int side,double w,double h,double hip,double knee){
        return box(p,root,side*.15,LegKinematics.lift(hip,knee,1)+LegKinematics.BOOT/2,
            -LegKinematics.forward(hip,knee,h)/w,.09,LegKinematics.BOOT/2,.09,w,h,0);
    }
    public static List<BodyBox> legs(Player p,Vec3 root){return parts(p,root).subList(2,8);}
    private static BodyBox limb(Player p,Vec3 root,double side,double joint,double halfLength,double radius,double w,double h,double pitch) {
        return box(p,root,side,joint-Math.cos(pitch)*halfLength,-Math.sin(pitch)*halfLength*h/w,radius,halfLength,radius,w,h,pitch);
    }
    private static BodyBox box(Player p,Vec3 root,double cx,double cy,double cz,double hx,double hy,double hz,double w,double h,double pitch) {
        double yaw=Math.toRadians(p.getYRot()),c=Math.cos(yaw),s=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);
        // Apply nonuniform model dimensions before rotation; the local limbs remain orthogonal.
        return new BodyBox(root.add((cx*c-cz*s)*w,cy*h,(cx*s+cz*c)*w),new Vec3(hx*w,hy*h,hz*w),
            new Vec3(c,0,s),new Vec3(-s*sp,cp,c*sp),new Vec3(-s*cp,-sp,c*cp));
    }
    public static Vec3 foot(Player player,int side) {
        var snapshot=Dimensions.snapshot(player);
        double scale=snapshot.modelWidth(), height=stanceHeight(player);
        double yaw=Math.toRadians(player.getYRot());
        var pose=EntityState.of(player).pose;
        double pitch=side<0 ? pose.leftLeg() : pose.rightLeg();
        double knee=side<0 ? pose.leftKnee() : pose.rightKnee();
        double forward=LegKinematics.forward(pitch,knee,height);
        return player.position().add(Math.cos(yaw)*side*scale*.15 + Math.sin(yaw)*forward,
            LegKinematics.lift(pitch,knee,height)-0.01, Math.sin(yaw)*side*scale*.15 - Math.cos(yaw)*forward);
    }
    public static int support(Player player) { return EntityState.of(player).pose.support(); }
}
