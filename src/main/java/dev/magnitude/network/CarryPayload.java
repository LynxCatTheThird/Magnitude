package dev.magnitude.network;
import dev.magnitude.Magnitude;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record CarryPayload(int carrier, boolean active, int position, double forward, double side, double up) implements CustomPacketPayload {
    public static final Type<CarryPayload> TYPE = new Type<>(Magnitude.id("carry_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CarryPayload> CODEC = StreamCodec.of((buf,p) -> {
        buf.writeVarInt(p.carrier); buf.writeBoolean(p.active); buf.writeVarInt(p.position); buf.writeDouble(p.forward); buf.writeDouble(p.side); buf.writeDouble(p.up);
    }, buf -> new CarryPayload(buf.readVarInt(),buf.readBoolean(),buf.readVarInt(),buf.readDouble(),buf.readDouble(),buf.readDouble()));
    @Override public Type<CarryPayload> type() { return TYPE; }
}
