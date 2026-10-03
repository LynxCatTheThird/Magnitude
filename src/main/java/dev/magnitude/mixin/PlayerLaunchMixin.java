package dev.magnitude.mixin;

import dev.magnitude.interaction.Interactions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Apply the final envelope on both logical sides, after the runtime's jump multiplier. */
@Mixin(value=LivingEntity.class,priority=500)
public abstract class PlayerLaunchMixin {
    @Inject(method="jumpFromGround",at=@At("RETURN"))
    private void magnitude$launchEnvelope(CallbackInfo info) {
        if((Object)this instanceof Player player)Interactions.limitJumpVelocity(player);
    }
}
