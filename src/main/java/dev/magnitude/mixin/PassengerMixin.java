package dev.magnitude.mixin;

import dev.magnitude.core.EntityState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class PassengerMixin {
    @Inject(method="getPassengerRidingPosition",at=@At("RETURN"),cancellable=true)
    private void magnitude$position(Entity passenger,CallbackInfoReturnable<Vec3> result) {
        Entity carrier=(Entity)(Object)this;
        if (!(carrier instanceof Player)) return;
        EntityState state=EntityState.of(carrier);
        if (!state.carrying) return;
        double forward=state.carryPosition==0?0.1:state.carryPosition==1?0.55:state.offsetForward;
        double side=state.carryPosition==0?0.25:state.carryPosition==1?0:state.offsetSide;
        double up=state.carryPosition==0?1.35:state.carryPosition==1?1:state.offsetUp;
        double angle=carrier.getYRot()*Math.PI/180;
        double width=Math.min(8,carrier.getBbWidth()/0.6);
        double height=Math.min(8,carrier.getBbHeight()/1.8);
        Vec3 offset=new Vec3((-Math.sin(angle)*forward+Math.cos(angle)*side)*width,up*height,(Math.cos(angle)*forward+Math.sin(angle)*side)*width);
        result.setReturnValue(carrier.position().add(offset));
    }
}
