package dev.magnitude.verification.mixin;

import dev.magnitude.verification.WorldQueryTrace;
import dev.magnitude.physics.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BodyCollision.class)
public abstract class MovementTraceMixin {
    @Inject(method="permitted",at=@At("HEAD"))
    private static void magnitude$start(Player p,Vec3 wanted,CallbackInfoReturnable<Boolean> info){if(!Boolean.getBoolean("magnitude.queryTrace"))return;WorldQueryTrace.begin(p,WorldObstacles.regions(PlayerBody.parts(p,p.position()),wanted,StepPolicy.height(p)));WorldQueryTrace.wanted(wanted);}
    @Inject(method="permitted",at=@At("RETURN"))
    private static void magnitude$end(Player p,Vec3 wanted,CallbackInfoReturnable<Boolean> info){WorldQueryTrace.end(p,new WorldObstacles.Result(java.util.List.of(),info.getReturnValue()));}
    @Inject(method="solve",at=@At("RETURN"))
    private static void magnitude$solved(Player p,Vec3 wanted,CallbackInfoReturnable<BodyCollision.Result> info){WorldQueryTrace.solved(p,wanted,info.getReturnValue());}
}
