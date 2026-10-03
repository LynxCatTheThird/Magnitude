package dev.magnitude.network;

import dev.magnitude.Magnitude;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.Map;
import java.util.LinkedHashMap;

/** Bounded allowlisted patch. Scope 0 reads, 1 edits personal permission, 2 edits server rules. */
public record ConfigRequest(long requestId,int scope,long revision,Map<String,Double> changes) implements CustomPacketPayload {
    public ConfigRequest { changes=Map.copyOf(changes);if(changes.size()>16)throw new IllegalArgumentException("Too many settings"); }
    public static final Type<ConfigRequest> TYPE=new Type<>(Magnitude.id("config_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ConfigRequest> CODEC=StreamCodec.of((b,p)->{
        b.writeVarLong(p.requestId);b.writeVarInt(p.scope);b.writeVarLong(p.revision);b.writeVarInt(p.changes.size());
        p.changes.forEach((key,value)->{b.writeUtf(key,64);b.writeDouble(value);});
    },b->{
        long request=b.readVarLong();int scope=b.readVarInt();long revision=b.readVarLong();int count=b.readVarInt();
        if(count<0||count>16)throw new IllegalArgumentException("Too many settings");
        var changes=new LinkedHashMap<String,Double>();
        for(int i=0;i<count;i++)if(changes.put(b.readUtf(64),b.readDouble())!=null)throw new IllegalArgumentException("Duplicate setting");
        return new ConfigRequest(request,scope,revision,changes);
    });
    @Override public Type<ConfigRequest> type(){return TYPE;}
}
