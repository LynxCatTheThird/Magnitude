package example.magnitude.fabric;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.magnitude.api.scale.ScaleSnapshot;
import dev.magnitude.fabric.api.MagnitudeScales;
import dev.magnitude.fabric.api.carry.CarryAccess;
import dev.magnitude.fabric.api.carry.CarryResult;
import dev.magnitude.fabric.api.carry.MagnitudeCarry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;
import java.util.function.BiFunction;

/**
 * An ordinary downstream mod. It resolves the real command sender, then calls public services.
 * There are no private-package imports, forged carry grants, direct teleports or attribute writes.
 */
public final class MagnitudeExample implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("magnitude-example")
                        .executes(context -> help(context.getSource()))
                        .then(Commands.literal("request")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(context -> request(context.getSource(), EntityArgument.getEntity(context, "target")))))
                        .then(Commands.literal("accept").executes(context ->
                                act(context.getSource(), CarryAccess::accept)))
                        .then(Commands.literal("decline").executes(context ->
                                act(context.getSource(), CarryAccess::decline)))
                        .then(Commands.literal("release").executes(context ->
                                act(context.getSource(), CarryAccess::release)))
                        .then(Commands.literal("recover").executes(context ->
                                act(context.getSource(), CarryAccess::recover)))
                        .then(Commands.literal("status").executes(context -> status(context.getSource())))
                        .then(Commands.literal("observe")
                                .executes(context -> observe(context.getSource(), context.getSource().getEntityOrException()))
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(context -> observe(context.getSource(), EntityArgument.getEntity(context, "target")))))
                        .then(Commands.literal("scale")
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                        .executes(context -> scale(context.getSource(), context.getSource().getEntityOrException(),
                                                DoubleArgumentType.getDouble(context, "value")))
                                        .then(Commands.argument("target", EntityArgument.entity())
                                                .executes(context -> scale(context.getSource(), EntityArgument.getEntity(context, "target"),
                                                        DoubleArgumentType.getDouble(context, "value"))))))));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Magnitude API example: request <target>, accept, decline, release, recover, status, observe [target]. Administrators: scale <value> [target]."), false);
        return 1;
    }

    private static int request(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        ServerPlayer sender = source.getPlayerOrException();
        if (!(target instanceof LivingEntity living)) return fail(source, "Choose a living target supported by Magnitude.");
        try {
            return report(source, MagnitudeCarry.forServer(source.getServer()).request(sender, living));
        } catch (IllegalStateException unavailable) {
            return fail(source, unavailable.getMessage());
        }
    }

    private static int act(CommandSourceStack source, BiFunction<CarryAccess, ServerPlayer, CarryResult> action)
            throws CommandSyntaxException {
        ServerPlayer sender = source.getPlayerOrException();
        try {
            return report(source, action.apply(MagnitudeCarry.forServer(source.getServer()), sender));
        } catch (IllegalStateException unavailable) {
            return fail(source, unavailable.getMessage());
        }
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer sender = source.getPlayerOrException();
        try {
            var own = MagnitudeCarry.forServer(source.getServer()).views().stream()
                    .filter(view -> view.hostId().equals(sender.getUUID()) || view.targetId().equals(sender.getUUID()))
                    .map(view -> view.phase().name()).toList();
            source.sendSuccess(() -> Component.literal(own.isEmpty() ? "No active carry relation." : "Your relations: " + own), false);
            return 1;
        } catch (IllegalStateException unavailable) {
            return fail(source, unavailable.getMessage());
        }
    }

    private static int observe(CommandSourceStack source, Entity target) {
        if (!(target instanceof LivingEntity living)) return fail(source, "This entity has no supported scale observation.");
        try {
            ScaleSnapshot snapshot = MagnitudeScales.forServer(source.getServer()).observe(living);
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "scale=%.4f width=%.4f height=%.4f eye=%.4f revision=%d",
                    snapshot.effectiveScale(), snapshot.observedWidth(), snapshot.observedHeight(),
                    snapshot.observedEyeHeight(), snapshot.revision())), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException unavailable) {
            return fail(source, unavailable.getMessage());
        }
    }

    private static int scale(CommandSourceStack source, Entity target, double value) {
        if (!(target instanceof LivingEntity living)) return fail(source, "This entity cannot be resized.");
        try {
            var scales = MagnitudeScales.forServer(source.getServer());
            ScaleSnapshot before = scales.observe(living);
            var result = scales.setScale(source, living, value, before.revision());
            if (!result.succeeded()) return fail(source, result.code() + ": " + result.detail());
            source.sendSuccess(() -> Component.literal(result.detail()), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException unavailable) {
            return fail(source, unavailable.getMessage());
        }
    }

    private static int report(CommandSourceStack source, CarryResult result) {
        // REQUESTED is a successful consent request, not a completed attachment.
        // RECOVERY means host control ended but placement is not complete.
        if (!result.succeeded()) return fail(source, result.code() + ": " + result.detail());
        source.sendSuccess(() -> Component.literal(result.code() + ": " + result.detail()), false);
        return 1;
    }

    private static int fail(CommandSourceStack source, String detail) {
        source.sendFailure(Component.literal(detail));
        return 0;
    }
}
