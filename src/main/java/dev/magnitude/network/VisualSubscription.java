package dev.magnitude.network;

import dev.magnitude.Magnitude;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VisualSubscription(boolean footprints) implements CustomPacketPayload {
    public static final Type<VisualSubscription> TYPE=new Type<>(Magnitude.id("visual_subscription"));
    public static final StreamCodec<RegistryFriendlyByteBuf,VisualSubscription> CODEC=StreamCodec.of((b,p)->b.writeBoolean(p.footprints),b->new VisualSubscription(b.readBoolean()));
    @Override public Type<VisualSubscription> type(){return TYPE;}
}
