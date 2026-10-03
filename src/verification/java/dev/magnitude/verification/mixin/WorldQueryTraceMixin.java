package dev.magnitude.verification.mixin;

import dev.magnitude.verification.WorldQueryTrace;

import dev.magnitude.physics.WorldObstacles;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldObstacles.class)
public abstract class WorldQueryTraceMixin {
    @Inject(method="query",at=@At("HEAD"))
    private static void magnitude$start(Player p,List<AABB> regions,boolean entities,CallbackInfoReturnable<WorldObstacles.Result> info){WorldQueryTrace.begin(p,regions);}
    @Inject(method="query",at=@At("RETURN"))
    private static void magnitude$end(Player p,List<AABB> regions,boolean entities,CallbackInfoReturnable<WorldObstacles.Result> info){WorldQueryTrace.end(p,info.getReturnValue());}
}
