package dev.magnitude.network;

import dev.magnitude.Magnitude;
import dev.magnitude.physics.BodyPose;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** No serverbound pose channel: clients cannot forge contacts or proxy bounds. */
public record PhysicsPayload(int entity, long revision, double limit, boolean fallback, float yaw, BodyPose pose) implements CustomPacketPayload {
    public static final Type<PhysicsPayload> TYPE=new Type<>(Magnitude.id("physics_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PhysicsPayload> CODEC=StreamCodec.of((buf,p)->{
        buf.writeVarInt(p.entity);buf.writeVarLong(p.revision);buf.writeDouble(p.limit);buf.writeBoolean(p.fallback);buf.writeFloat(p.yaw);
        BodyPose pose=p.pose;buf.writeVarInt(pose.action());buf.writeLong(pose.startTick());buf.writeDouble(pose.phase());buf.writeVarInt(pose.support());
        buf.writeDouble(pose.leftLeg());buf.writeDouble(pose.rightLeg());buf.writeDouble(pose.leftArm());buf.writeDouble(pose.rightArm());buf.writeDouble(pose.head());
    },buf->new PhysicsPayload(buf.readVarInt(),buf.readVarLong(),buf.readDouble(),buf.readBoolean(),buf.readFloat(),
        new BodyPose(buf.readVarInt(),buf.readLong(),buf.readDouble(),buf.readVarInt(),buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readDouble())));
    public boolean newerThan(long revision) { return this.revision > revision; }
    @Override public Type<PhysicsPayload> type(){return TYPE;}
}
