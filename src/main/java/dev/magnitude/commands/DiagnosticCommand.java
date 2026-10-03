package dev.magnitude.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.magnitude.Magnitude;
import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import dev.magnitude.core.Settings;
import dev.magnitude.interaction.Impact;
import dev.magnitude.physics.PhysicsWork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import static net.minecraft.commands.Commands.literal;

/** Read-only diagnostics, isolated from configuration commands. */
public final class DiagnosticCommand {
    private DiagnosticCommand() {}
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(literal("diagnostics").then(literal("physics").executes(c -> {
            var player = c.getSource().getPlayerOrException();
            var state = EntityState.of(player);
            var scale = Dimensions.snapshot(player);
            var event = state.contacts.last;
            var metrics=state.contacts.diagnostics;
            var tickTimings=dev.magnitude.physics.ServerMetrics.TICKS.summary();
            var moveTimings=metrics.timings.summary();
            String status = "terrain server=" + Magnitude.settings.terrainDamage + " player=" + state.terrainEnabled
                + "; pressure server=" + Magnitude.settings.standingPressure + " player=" + state.pressureEnabled
                + "; scale=" + scale.base() + " revision=" + scale.revision() + " proxy=" + scale.proxyLimit()
                + "; step=" + dev.magnitude.physics.StepPolicy.height(player)
                + "; pose=" + state.pose.action() + " feet=" + state.pose.support()
                + "; support=" + state.contacts.support
                + "; event=" + (event == null ? "none" : event.type() + "#" + event.sequence())
                + "; pendingFootprints="+state.contacts.footprints.size()
                + "; reason=" + state.contacts.reason + " writes=" + state.contacts.changedBlocks
                + "; blocks=" + Impact.remaining() + " checks=" + Impact.checksRemaining()
                + " cells=" + PhysicsWork.cellsRemaining() + " pairs=" + PhysicsWork.pairsRemaining()
                + "; serverTickMs="+c.getSource().getServer().getAverageTickTimeNanos()/1_000_000.0
                + "; moveAvgMs="+metrics.averageMillis()+" moveMaxMs="+metrics.maximumNanos/1_000_000.0
                + "; moveDenied="+metrics.denied+"/"+metrics.samples+" lastFailure="+metrics.failure
                + "; tickP50P95P99="+tickTimings.p50()+"/"+tickTimings.p95()+"/"+tickTimings.p99()
                + "; moveP50P95P99="+moveTimings.p50()+"/"+moveTimings.p95()+"/"+moveTimings.p99()
                + "; fallback=" + state.proxyFallback + "; scaleWarning=" + state.scaleWarning;
            c.getSource().sendSuccess(() -> Component.literal(status), false);
            return 1;
        })));
    }
}
