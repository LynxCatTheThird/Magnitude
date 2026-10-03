package dev.magnitude.client.settings;

import dev.magnitude.physics.TimingWindow;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Intervals between consecutive world extraction frames; not GPU render duration. */
public final class ClientMetrics {
    public static final TimingWindow FRAMES=new TimingWindow(256);
    private static long previous;
    private ClientMetrics(){}
    public static void register(){
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear());
        LevelExtractionEvents.END_EXTRACTION.register(context->{long now=System.nanoTime();if(previous!=0)FRAMES.add(now-previous);previous=now;});
    }
    private static void clear(){previous=0;FRAMES.clear();}
}
