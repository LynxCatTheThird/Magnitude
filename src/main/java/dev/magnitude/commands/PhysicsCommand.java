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

/** Grouped control and diagnosis; global writes require server administration permission. */
public final class PhysicsCommand {
    private PhysicsCommand() {}
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> physics) {
        physics.then(literal("status").executes(c -> {
            var player = c.getSource().getPlayerOrException();
            var state = EntityState.of(player);
            var scale = Dimensions.snapshot(player);
            var event = state.contacts.last;
            var metrics=state.contacts.diagnostics;
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
                + "; fallback=" + state.proxyFallback + "; scaleWarning=" + state.scaleWarning;
            c.getSource().sendSuccess(() -> Component.literal(status), false);
            return 1;
        }));
        for (boolean enabled : new boolean[]{true,false}) {
            physics.then(literal(enabled ? "enable" : "disable").executes(c -> {
                var actor=c.getSource().getPlayerOrException();
                if(dev.magnitude.config.ConfigService.administrator(c.getSource())) {
                    var result=dev.magnitude.config.ConfigService.INSTANCE.server(c.getSource(),-1,java.util.Map.of("terrainDamage",enabled?1d:0d,"standingPressure",enabled?1d:0d));
                    if(!result.success())return ConfigCommand.reply(c,result);
                }
                var result=dev.magnitude.config.ConfigService.INSTANCE.personal(actor,-1,java.util.Map.of("terrain",enabled?1d:0d,"pressure",enabled?1d:0d));
                if(!result.success())return ConfigCommand.reply(c,result);
                return ConfigCommand.show(c);
            }));
        }
        var server=literal("server").requires(dev.magnitude.config.ConfigService::administrator);
        var player=literal("player");
        for(String field:new String[]{"terrain","pressure"}) {
            var global=literal(field);var personal=literal(field);
            for(boolean enabled:new boolean[]{true,false}) {
                global.then(literal(enabled?"on":"off").executes(c->ConfigCommand.server(c,field.equals("terrain")?"terrainDamage":"standingPressure",enabled?1:0)));
                personal.then(literal(enabled?"on":"off").executes(c->ConfigCommand.personal(c,field,enabled)));
            }
            server.then(global);player.then(personal);
        }
        physics.then(server);physics.then(player);
        physics.then(literal("reload").requires(dev.magnitude.config.ConfigService::administrator).executes(c->ConfigCommand.reply(c,dev.magnitude.config.ConfigService.INSTANCE.reload(c.getSource()))));
    }
}
