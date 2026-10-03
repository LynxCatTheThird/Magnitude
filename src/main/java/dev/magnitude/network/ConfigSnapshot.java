package dev.magnitude.network;

import dev.magnitude.Magnitude;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Finite snapshot encoded separately from client edits; open is an explicit server menu request. */
public record ConfigSnapshot(long requestId,boolean open,String json) implements CustomPacketPayload {
    public static final Type<ConfigSnapshot> TYPE=new Type<>(Magnitude.id("config_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ConfigSnapshot> CODEC=StreamCodec.of((b,p)->{
        b.writeVarLong(p.requestId);b.writeBoolean(p.open);b.writeUtf(p.json,16384);
    },b->new ConfigSnapshot(b.readVarLong(),b.readBoolean(),b.readUtf(16384)));
    @Override public Type<ConfigSnapshot> type(){return TYPE;}
}
