package dev.magnitude.mixin;

import dev.magnitude.physics.BodyCollision;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerPositionCheckMixin {
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
