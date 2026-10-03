package dev.magnitude.network;

import dev.magnitude.Magnitude;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Contact intent for decoration only; it cannot move entities or write blocks. */
public record FootprintPayload(UUID actor,long sequence,int side,Identifier dimension,Vec3 center,double width,double length,double yaw) implements CustomPacketPayload {
    public static final Type<FootprintPayload> TYPE=new Type<>(Magnitude.id("footprint_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf,FootprintPayload> CODEC=StreamCodec.of((b,p)->{
        b.writeUUID(p.actor);b.writeVarLong(p.sequence);b.writeByte(p.side);b.writeIdentifier(p.dimension);
        b.writeDouble(p.center.x);b.writeDouble(p.center.y);b.writeDouble(p.center.z);b.writeDouble(p.width);b.writeDouble(p.length);b.writeDouble(p.yaw);
    },b->new FootprintPayload(b.readUUID(),b.readVarLong(),b.readByte(),b.readIdentifier(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readDouble(),b.readDouble(),b.readDouble()));
    public boolean valid(){return sequence>=0&&(side==-1||side==1)&&Double.isFinite(center.x)&&Double.isFinite(center.y)&&Double.isFinite(center.z)
        &&Math.abs(center.x)<=30_000_000&&Math.abs(center.z)<=30_000_000&&Math.abs(center.y)<=30_000_000
        &&Double.isFinite(width)&&Double.isFinite(length)&&width>0&&length>0&&width<=1e10&&length<=1e10&&Double.isFinite(yaw)&&Math.abs(yaw)<=Math.PI*2;}
    @Override public Type<FootprintPayload> type(){return TYPE;}
}
