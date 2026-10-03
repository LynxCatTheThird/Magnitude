package dev.magnitude.physics;

import dev.magnitude.interaction.Impact;
import dev.magnitude.Magnitude;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Actual tick execution cost; excludes the scheduler's between-tick sleep. Server thread only. */
public final class ServerMetrics {
    public static final TimingWindow TICKS=new TimingWindow(256);
    private static long started;
    public static int cellsUsed,pairsUsed,materialChecks,blockWrites;
    private ServerMetrics(){}
    public static void register(){
        ServerLifecycleEvents.SERVER_STARTING.register(server->{TICKS.clear();cellsUsed=pairsUsed=materialChecks=blockWrites=0;});
        ServerTickEvents.START_SERVER_TICK.register(server->started=System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(started!=0)TICKS.add(System.nanoTime()-started);
            cellsUsed=524288-PhysicsWork.cellsRemaining();pairsUsed=524288-PhysicsWork.pairsRemaining();
            materialChecks=Math.max(0,Magnitude.settings.checksPerTick-Impact.checksRemaining());
            blockWrites=Math.max(0,Magnitude.settings.blocksPerTick-Impact.remaining());
        });
    }
}
