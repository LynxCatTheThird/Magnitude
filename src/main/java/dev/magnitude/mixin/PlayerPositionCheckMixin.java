package dev.magnitude.mixin;

import dev.magnitude.physics.BodyCollision;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerPositionCheckMixin {
    @Shadow public net.minecraft.server.level.ServerPlayer player;
    @Shadow private boolean noBlocksAround(Entity entity){throw new AssertionError();}
    @Shadow private static double clampHorizontal(double value){throw new AssertionError();}
    @Shadow private static double clampVertical(double value){throw new AssertionError();}
    @WrapMethod(method="handlePlayerPositionChange")
    private void magnitude$stepInterval(double x,double y,double z,float yaw,float pitch,boolean grounded,boolean collision,Operation<Void> original){
        try{
            dev.magnitude.physics.PacketSteps.prepare(player,new Vec3(x,y,z));
            original.call(x,y,z,yaw,pitch,grounded,collision);
        }finally{dev.magnitude.physics.PacketSteps.clear();}
    }
    @Redirect(method="handlePlayerPositionChange",at=@At(value="INVOKE",target="Lnet/minecraft/world/phys/Vec3;lengthSqr()D"))
    private double magnitude$verifiedRise(Vec3 velocity){return velocity.lengthSqr()+dev.magnitude.physics.PacketSteps.velocityAllowance(player);}
    @Redirect(method="handlePlayerPositionChange",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerPlayer;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private void magnitude$moveStep(net.minecraft.server.level.ServerPlayer player,net.minecraft.world.entity.MoverType type,Vec3 movement,
                                   double x,double y,double z,float yaw,float pitch,boolean grounded,boolean collision){
        Vec3 step=dev.magnitude.physics.PacketSteps.movement(player);
        if(step!=null)player.move(net.minecraft.world.entity.MoverType.SELF,step.scale(1/dev.magnitude.core.Dimensions.snapshot(player).motionFactor()));
        else if(BodyCollision.active(player)){
            // Support settlement may have moved the root since the last packet baseline.
            // Reconcile the packet from the current root; otherwise vanilla reapplies it.
            Vec3 delta=new Vec3(clampHorizontal(x),clampVertical(y),clampHorizontal(z)).subtract(player.position());
            player.move(net.minecraft.world.entity.MoverType.SELF,delta.scale(1/dev.magnitude.core.Dimensions.snapshot(player).motionFactor()));
        }
        else player.move(type,movement);
    }
    @Redirect(method="handlePlayerPositionChange",at=@At(value="INVOKE",target="Lnet/minecraft/server/network/ServerGamePacketListenerImpl;noBlocksAround(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean magnitude$stepGround(ServerGamePacketListenerImpl handler,Entity entity){
        return !dev.magnitude.physics.PacketSteps.grounded(player)&&noBlocksAround(entity);
    }
    /** Upward stepping is movement, not a jump intent. Direct server/API jumps stay intact. */
    @Redirect(method="handlePlayerPositionChange",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerPlayer;jumpFromGround()V"))
    private void magnitude$jumpIntent(net.minecraft.server.level.ServerPlayer player){
        if(!BodyCollision.active(player)||player.getLastClientInput().jump())player.jumpFromGround();
    }
    @Inject(method="isEntityCollidingWithAnythingNew",at=@At("HEAD"),cancellable=true)
    private void magnitude$checkBody(LevelReader level,Entity entity,AABB oldBox,double x,double y,double z,CallbackInfoReturnable<Boolean> result) {
        if(entity instanceof Player player && BodyCollision.active(player))result.setReturnValue(BodyCollision.newCollision(player,oldBox.getBottomCenter(),new Vec3(x,y,z)));
    }
}
