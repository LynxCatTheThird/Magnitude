package dev.magnitude.client.mixin;

import dev.magnitude.physics.BodyCollision;
import dev.magnitude.physics.BodyEscape;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocalPlayer.class)
public abstract class BodyEscapeMixin {
    @Shadow private boolean suffocatesAt(BlockPos column){throw new AssertionError();}
    @Redirect(method="moveTowardsClosestSpace",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;suffocatesAt(Lnet/minecraft/core/BlockPos;)Z",ordinal=0))
    private boolean magnitude$verifiedPenetration(LocalPlayer player,BlockPos column){
        // Unknown geometry cannot justify an unsolicited escape impulse.
        return BodyCollision.active(player)?BodyEscape.probe(player,column)==BodyEscape.Occupancy.BLOCKED:suffocatesAt(column);
    }
    @Redirect(method="moveTowardsClosestSpace",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;suffocatesAt(Lnet/minecraft/core/BlockPos;)Z",ordinal=1))
    private boolean magnitude$verifiedExit(LocalPlayer player,BlockPos column){
        // Unknown destination columns cannot be selected as free escape space.
        return BodyCollision.active(player)?BodyEscape.probe(player,column)!=BodyEscape.Occupancy.CLEAR:suffocatesAt(column);
    }
}
