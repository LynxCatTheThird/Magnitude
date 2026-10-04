package dev.magnitude.verification;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Test-only readiness handshake; never included in the production mod. */
public final class NaturalTerrainProtocol {
    private NaturalTerrainProtocol(){}
    public record Phase(int index,double size,boolean running) implements CustomPacketPayload {
        public static final Type<Phase> TYPE=new Type<>(Identifier.fromNamespaceAndPath("magnitude-verification","natural_phase"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Phase> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.index);b.writeDouble(p.size);b.writeBoolean(p.running);},b->new Phase(b.readVarInt(),b.readDouble(),b.readBoolean()));
        public Type<Phase> type(){return TYPE;}
    }
    public record Ready(int index) implements CustomPacketPayload {
        public static final Type<Ready> TYPE=new Type<>(Identifier.fromNamespaceAndPath("magnitude-verification","natural_ready"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Ready> CODEC=StreamCodec.of((b,p)->b.writeVarInt(p.index),b->new Ready(b.readVarInt()));
        public Type<Ready> type(){return TYPE;}
    }
    public static void register(){
        PayloadTypeRegistry.clientboundPlay().register(Phase.TYPE,Phase.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Ready.TYPE,Ready.CODEC);
    }
}
