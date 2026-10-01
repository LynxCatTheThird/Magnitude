package dev.magnitude.network;
import dev.magnitude.Magnitude;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record ActionPayload(int action) implements CustomPacketPayload {
    public static final Type<ActionPayload> TYPE = new Type<>(Magnitude.id("action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ActionPayload> CODEC = StreamCodec.of((buf,payload) -> buf.writeVarInt(payload.action), buf -> new ActionPayload(buf.readVarInt()));
    @Override public Type<ActionPayload> type() { return TYPE; }
}
