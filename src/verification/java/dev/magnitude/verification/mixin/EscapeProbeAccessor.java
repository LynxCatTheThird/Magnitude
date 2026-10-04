package dev.magnitude.verification.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Verification-only invocation of the real client escape path. */
@Mixin(LocalPlayer.class)
public interface EscapeProbeAccessor {
    @Invoker("moveTowardsClosestSpace") void magnitude$escape(double x,double z);
}
