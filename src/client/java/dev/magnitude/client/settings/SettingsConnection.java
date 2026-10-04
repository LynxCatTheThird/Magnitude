package dev.magnitude.client.settings;

import com.google.gson.Gson;
import dev.magnitude.config.ConfigView;
import dev.magnitude.network.ConfigRequest;
import dev.magnitude.network.ConfigSnapshot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import java.util.Map;

/** One outstanding request, connection-scoped state, monotonically increasing IDs across reconnects. */
public final class SettingsConnection {
    private static final Gson JSON=new Gson();
    private static long sequence,pending,sentAt,receivedAt;
    public static long epoch;
    public static ConfigView view;
    public static String result="ready";
    private SettingsConnection(){}
    public static void register(){
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->reset());
        ClientPlayNetworking.registerGlobalReceiver(ConfigSnapshot.TYPE,(payload,context)->receive(payload,context.client()));
    }
    private static void reset(){epoch++;pending=0;receivedAt=0;view=null;result="ready";}
    public static long snapshotAgeMillis(){return view==null||receivedAt==0?-1:Math.max(0,(System.nanoTime()-receivedAt)/1_000_000);}
    public static boolean supported(){return Minecraft.getInstance().getConnection()!=null&&ClientPlayNetworking.canSend(ConfigRequest.TYPE);}
    public static boolean busy(){
        if(pending!=0&&System.nanoTime()-sentAt>5_000_000_000L){pending=0;result="timeout";}
        return pending!=0;
    }
    public static boolean request(int scope,Map<String,Double> patch){
        if(!supported()||busy()||scope!=0&&view==null)return false;
        if(System.nanoTime()-sentAt<300_000_000L){result="rateLimited";return false;}
        long revision=scope==1?view.personalRevision():scope==2?view.revision():0;
        pending=++sequence;sentAt=System.nanoTime();result="waiting";
        ClientPlayNetworking.send(new ConfigRequest(pending,scope,revision,patch));return true;
    }
    private static void receive(ConfigSnapshot payload,Minecraft client){
        if(!payload.open()&&payload.requestId()!=pending)return;
        if(client.player==null)return;
        try {
            ConfigView next=JSON.fromJson(payload.json(),ConfigView.class);
            if(next==null||next.server()==null||next.personal()==null)return;
            view=next;receivedAt=System.nanoTime();result=next.result();pending=0;
            if(client.gui.screen() instanceof SettingsScreen screen)screen.received();
            else if(payload.open())client.gui.setScreen(new SettingsScreen(client.gui.screen()));
        }catch(RuntimeException error){pending=0;result="invalid";}
    }
}
