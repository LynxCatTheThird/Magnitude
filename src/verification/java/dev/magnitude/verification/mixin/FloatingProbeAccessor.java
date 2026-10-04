package dev.magnitude.verification.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerGamePacketListenerImpl.class)
public interface FloatingProbeAccessor {
    @Accessor("clientIsFloating") boolean magnitude$isFloating();
    @Accessor("tickCount") void magnitude$setConnectionTick(int tick);
    @Accessor("lastGoodX") double magnitude$lastGoodX();
}
