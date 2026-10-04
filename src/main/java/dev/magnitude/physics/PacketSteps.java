package dev.magnitude.physics;

import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A synchronous, single-packet proof of a grounded step, never a client-provided allowance. */
public final class PacketSteps {
    private static final ThreadLocal<Proof> CURRENT=new ThreadLocal<>();
    private static final class Proof {
        final ServerPlayer player;
        final Level level;
        final Vec3 root,delta;
        final BodyPose pose;
        final float yaw;
        final double width,height;
        final long tick;
        boolean verified,consumed;
        Proof(ServerPlayer player,Vec3 delta){
            this.player=player;this.level=player.level();this.root=player.position();this.delta=delta;
            pose=EntityState.of(player).pose;yaw=player.getYRot();
            width=Dimensions.snapshot(player).modelWidth();height=PlayerBody.stanceHeight(player);
            tick=player.level().getGameTime();
        }
        boolean geometry(){return level==player.level()&&pose.equals(EntityState.of(player).pose)&&yaw==player.getYRot()
            &&width==Dimensions.snapshot(player).modelWidth()&&height==PlayerBody.stanceHeight(player)
            &&tick==player.level().getGameTime();}
        boolean atSource(){return geometry()&&root.equals(player.position());}
    }
    private PacketSteps(){}
    public static void clear(){CURRENT.remove();}
    /** Block changes anywhere in the same world invalidate an in-flight proof. */
    public static void changed(Level level){var proof=CURRENT.get();if(proof!=null&&proof.level==level)clear();}
    public static void prepare(ServerPlayer player,Vec3 target){
        clear();
        var input=player.getLastClientInput();Vec3 delta=target.subtract(player.position());
        if(!BodyCollision.active(player)||!player.onGround()||player.noPhysics||player.getAbilities().flying
            ||player.isNoGravity()||input.jump()||!(input.forward()||input.backward()||input.left()||input.right())
            ||!Double.isFinite(delta.lengthSqr())||delta.y<=.6||delta.y>StepPolicy.height(player)+1e-6
            ||delta.horizontalDistanceSqr()<=1e-8||delta.horizontalDistanceSqr()>100)return;
        var proof=new Proof(player,delta);CURRENT.set(proof);
        if(!touching(player,Vec3.ZERO)){clear();return;}
        var result=BodyCollision.solve(player,new Vec3(delta.x,0,delta.z));
        if(result.denied()||result.movement().subtract(delta).lengthSqr()>1e-10
            ||!touching(player,delta)||CURRENT.get()!=proof||!proof.atSource()){clear();return;}
        proof.verified=true;
    }
    private static boolean touching(ServerPlayer player,Vec3 movement){
        return FootContacts.supportedAt(player,movement);
    }
    /** Only the independently verified rise contributes to expected movement. */
    public static double velocityAllowance(ServerPlayer player){
        var proof=CURRENT.get();return proof!=null&&proof.player==player&&proof.verified&&proof.atSource()?proof.delta.y*proof.delta.y:0;
    }
    public static Vec3 movement(ServerPlayer player){
        var proof=CURRENT.get();return proof!=null&&proof.player==player&&proof.verified&&!proof.consumed&&proof.atSource()?proof.delta:null;
    }
    /** Called before effects; the packet interval permits no intervening world mutation. */
    public static Vec3 consume(net.minecraft.world.entity.player.Player player,Vec3 wanted){
        var proof=CURRENT.get();
        if(proof==null||proof.player!=player||!proof.verified||proof.consumed||!proof.atSource()
            ||proof.delta.subtract(wanted).lengthSqr()>1e-12)return null;
        proof.consumed=true;EntityState.of(player).movementDenied=false;return proof.delta;
    }
    public static boolean grounded(ServerPlayer player){
        var proof=CURRENT.get();return proof!=null&&proof.player==player&&proof.verified&&proof.consumed
            &&proof.geometry()&&player.position().subtract(proof.root.add(proof.delta)).lengthSqr()<1e-10;
    }
}
