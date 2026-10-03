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
            String status = "terrain server=" + Magnitude.settings.terrainDamage + " player=" + state.terrainEnabled
                + "; pressure server=" + Magnitude.settings.standingPressure + " player=" + state.pressureEnabled
                + "; scale=" + scale.base() + " revision=" + scale.revision() + " proxy=" + scale.proxyLimit()
                + "; pose=" + state.pose.action() + " feet=" + state.pose.support()
                + "; support=" + state.contacts.support
                + "; event=" + (event == null ? "none" : event.type() + "#" + event.sequence())
                + "; reason=" + state.contacts.reason + " writes=" + state.contacts.changedBlocks
                + "; blocks=" + Impact.remaining() + " checks=" + Impact.checksRemaining()
                + " cells=" + PhysicsWork.cellsRemaining() + " pairs=" + PhysicsWork.pairsRemaining()
                + "; fallback=" + state.proxyFallback + "; scaleWarning=" + state.scaleWarning;
            c.getSource().sendSuccess(() -> Component.literal(status), false);
            return 1;
        }));
        var server = literal("server").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
        var player = literal("player");
        for (String field : new String[]{"terrain", "pressure"}) {
            var global = literal(field);
            var personal = literal(field);
            for (boolean enabled : new boolean[]{true, false}) {
                String toggle = enabled ? "on" : "off";
                global.then(literal(toggle).executes(c -> {
                    if (field.equals("terrain")) Magnitude.settings.terrainDamage = enabled;
                    else Magnitude.settings.standingPressure = enabled;
                    Magnitude.settings.save();
                    return reply(c, "server " + field + "=" + enabled);
                }));
                personal.then(literal(toggle).executes(c -> {
                    var state = EntityState.of(c.getSource().getPlayerOrException());
                    if (field.equals("terrain")) state.terrainEnabled = enabled;
                    else state.pressureEnabled = enabled;
                    return reply(c, "player " + field + "=" + enabled + "; server="
                        + (field.equals("terrain") ? Magnitude.settings.terrainDamage : Magnitude.settings.standingPressure));
                }));
            }
            server.then(global); player.then(personal);
        }
        physics.then(server); physics.then(player);
        physics.then(literal("reload").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)).executes(c -> {
            try { Magnitude.settings = Settings.load(); return reply(c, "physics configuration reloaded"); }
            catch (RuntimeException error) { c.getSource().sendFailure(Component.literal(error.getMessage())); return 0; }
        }));
    }
    private static int reply(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
