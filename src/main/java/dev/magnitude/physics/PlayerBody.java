package dev.magnitude.physics;

import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Six internal body parts, no additional entities. Geometry stays in local double coordinates. */
public final class PlayerBody {
    private PlayerBody() {}
    public static List<BodyBox> parts(Player player, Vec3 root) {
        return parts(player,root,player.getBbWidth()/0.6,player.getBbHeight()/1.8);
    }
    /** The implicit full body is available without visiting its world-sized bounding volume. */
    public static List<BodyBox> actualParts(Player player) {
        double vanilla=virtuoel.pehkui.api.PehkuiConfig.COMMON.applyVanillaScale.get() ? 1 : player.getScale();
        return parts(player,player.position(),virtuoel.pehkui.api.ScaleTypes.MODEL_WIDTH.getScaleData(player).getScale()*vanilla,
            virtuoel.pehkui.api.ScaleTypes.MODEL_HEIGHT.getScaleData(player).getScale()*vanilla);
    }
    private static List<BodyBox> parts(Player player,Vec3 root,double width,double height) {
        BodyPose pose=EntityState.of(player).pose;
        return List.of(box(player,root,0,1.57,0,.21,.23,.21,width,height,pose.head()),
            box(player,root,0,1.05,0,.21,.29,.12,width,height,0),
            limb(player,root,-.15,.76,.38,.09,width,height,pose.leftLeg()),
            limb(player,root,.15,.76,.38,.09,width,height,pose.rightLeg()),
            limb(player,root,-.24,1.32,.24,.055,width,height,pose.leftArm()),
            limb(player,root,.24,1.32,.24,.055,width,height,pose.rightArm()));
    }
    private static BodyBox limb(Player p,Vec3 root,double side,double joint,double halfLength,double radius,double w,double h,double pitch) {
        return box(p,root,side,joint-Math.cos(pitch)*halfLength,-Math.sin(pitch)*halfLength,radius,halfLength,radius,w,h,pitch);
    }
    private static BodyBox box(Player p,Vec3 root,double cx,double cy,double cz,double hx,double hy,double hz,double w,double h,double pitch) {
        double yaw=Math.toRadians(p.getYRot()),c=Math.cos(yaw),s=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);
        // Apply nonuniform proxy dimensions before rotation; the local limbs remain orthogonal.
        return new BodyBox(root.add((cx*c-cz*s)*w,cy*h,(cx*s+cz*c)*w),new Vec3(hx*w,hy*h,hz*w),
            new Vec3(c,0,s),new Vec3(-s*sp,cp,c*sp),new Vec3(-s*cp,-sp,c*cp));
    }
    public static Vec3 foot(Player player,int side) {
        double scale=Math.min(Dimensions.size(player),EntityState.of(player).proxyLimit);
        double yaw=Math.toRadians(player.getYRot());
        double pitch=side<0 ? EntityState.of(player).pose.leftLeg() : EntityState.of(player).pose.rightLeg();
        return player.position().add(Math.cos(yaw)*side*scale*.15 + Math.sin(yaw)*Math.sin(pitch)*scale*.72,
            -0.01, Math.sin(yaw)*side*scale*.15 - Math.cos(yaw)*Math.sin(pitch)*scale*.72);
    }
    public static int support(Player player) { return EntityState.of(player).pose.support(); }
}
