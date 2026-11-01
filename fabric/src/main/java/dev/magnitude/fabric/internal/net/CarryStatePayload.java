package dev.magnitude.fabric.internal.net;

import dev.magnitude.fabric.api.carry.CarryView;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Version-one full snapshot. Only server -> client; no client-supplied world decisions. */
public record CarryStatePayload(UUID epoch, long sequence, List<CarryView> views) implements CustomPacketPayload {
    public static final int MAX_VIEWS = 80;
    public static final Type<CarryStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("magnitude", "carry_state_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CarryStatePayload> CODEC = new StreamCodec<>() {
        @Override public CarryStatePayload decode(RegistryFriendlyByteBuf buffer) {
            UUID epoch = buffer.readUUID();
            long sequence = buffer.readVarLong();
            int count = buffer.readVarInt();
            if (sequence < 0 || count < 0 || count > MAX_VIEWS) throw new DecoderException("Invalid carry snapshot header");
            var views = new ArrayList<CarryView>(count);
            var targets = new HashSet<UUID>();
            for (int i = 0; i < count; i++) {
                UUID host = buffer.readUUID(), target = buffer.readUUID();
                int hostEntity = buffer.readVarInt(), targetEntity = buffer.readVarInt();
                String dimension = buffer.readUtf(256);
                Vec3 anchor = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
                int phase = buffer.readUnsignedByte();
                long revision = buffer.readVarLong();
                if (phase >= CarryView.Phase.values().length || revision < 0 || targetEntity < 0
                        || !Double.isFinite(anchor.x) || !Double.isFinite(anchor.y) || !Double.isFinite(anchor.z)
                        || !targets.add(target)) throw new DecoderException("Invalid carry snapshot member");
                views.add(new CarryView(host, target, hostEntity, targetEntity, dimension, anchor, CarryView.Phase.values()[phase], revision));
            }
            return new CarryStatePayload(epoch, sequence, views);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, CarryStatePayload value) {
            buffer.writeUUID(value.epoch()); buffer.writeVarLong(value.sequence()); buffer.writeVarInt(value.views().size());
            for (CarryView view : value.views()) {
                buffer.writeUUID(view.hostId()); buffer.writeUUID(view.targetId());
                buffer.writeVarInt(view.hostEntityId()); buffer.writeVarInt(view.targetEntityId());
                buffer.writeUtf(view.dimension(), 256);
                buffer.writeDouble(view.anchor().x); buffer.writeDouble(view.anchor().y); buffer.writeDouble(view.anchor().z);
                buffer.writeByte(view.phase().ordinal()); buffer.writeVarLong(view.revision());
            }
        }
    };
    public CarryStatePayload {
        Objects.requireNonNull(epoch); views = List.copyOf(views);
        if (sequence < 0 || views.size() > MAX_VIEWS) throw new IllegalArgumentException("Invalid snapshot size/sequence");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
