package dev.magnitude.fabric.internal;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.magnitude.fabric.api.carry.CarryAccess;
import dev.magnitude.fabric.api.carry.CarryResult;
import dev.magnitude.fabric.api.carry.MagnitudeCarry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

/** The CLI is one consumer of the same server-owned public carry service. */
public final class CarryCommands {
    private CarryCommands() {}
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                Commands.literal("magnitude").then(Commands.literal("carry")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.translatable("magnitude.carry.help"), false);
                            return 1;
                        })
                        .then(Commands.literal("request")
                                .executes(CarryCommands::requestLookedAt)
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(context -> request(context, EntityArgument.getEntity(context, "target")))))
                        .then(Commands.literal("accept").executes(context -> act(context, CarryAccess::accept)))
                        .then(Commands.literal("decline").executes(context -> act(context, CarryAccess::decline)))
                        .then(Commands.literal("drop").executes(context -> act(context, CarryAccess::release)))
                        .then(Commands.literal("escape").executes(context -> act(context, CarryAccess::release)))
                        .then(Commands.literal("recover").executes(context -> act(context, CarryAccess::recover))
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                        .executes(context -> {
                                            Entity target = EntityArgument.getEntity(context, "target");
                                            if (!(target instanceof LivingEntity living)) return failure(context.getSource(), "invalid_target");
                                            try { return report(context.getSource(), MagnitudeCarry.forServer(context.getSource().getServer()).recover(context.getSource(), living)); }
                                            catch (IllegalStateException unavailable) { return failure(context.getSource(), "not_ready"); }
                                        }))))));
    }
    private static int act(CommandContext<CommandSourceStack> context,
                           BiFunction<CarryAccess, ServerPlayer, CarryResult> action) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try { return report(context.getSource(), action.apply(MagnitudeCarry.forServer(player.level().getServer()), player)); }
        catch (IllegalStateException unavailable) { return failure(context.getSource(), "not_ready"); }
    }
    private static int request(CommandContext<CommandSourceStack> context, Entity target) throws CommandSyntaxException {
        ServerPlayer host = context.getSource().getPlayerOrException();
        if (!(target instanceof LivingEntity living)) return failure(context.getSource(), "invalid_target");
        try { return report(context.getSource(), MagnitudeCarry.forServer(host.level().getServer()).request(host, living)); }
        catch (IllegalStateException unavailable) { return failure(context.getSource(), "not_ready"); }
    }
    private static int requestLookedAt(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer host = context.getSource().getPlayerOrException();
        if (!Float.isFinite(host.getScale()) || host.getScale() < 0.25 || host.getScale() > 4
                || host.getBbWidth() > 16 || host.getBbHeight() > 16) return failure(context.getSource(), "size_mismatch");
        int admitted = 0;
        for (Entity ignored : host.level().getAllEntities()) {
            if (++admitted > 512) return failure(context.getSource(), "budget");
        }
        Vec3 eye = host.getEyePosition(), end = eye.add(host.getLookAngle().scale(6));
        var candidates = new ArrayList<LivingEntity>();
        host.level().getEntities(EntityTypeTest.forClass(LivingEntity.class),
                host.getBoundingBox().expandTowards(host.getLookAngle().scale(6)).inflate(1),
                entity -> true, candidates, 129);
        if (candidates.size() > 128) return failure(context.getSource(), "budget");
        return candidates.stream().filter(entity -> entity != host)
                .filter(entity -> entity.getBoundingBox().clip(eye, end).isPresent())
                .min(Comparator.comparingDouble(entity -> entity.getBoundingBox().clip(eye, end).orElseThrow().distanceToSqr(eye)))
                .map(entity -> {
                    try { return request(context, entity); }
                    catch (CommandSyntaxException e) { return failure(context.getSource(), "invalid_target"); }
                }).orElseGet(() -> failure(context.getSource(), "invalid_target"));
    }
    private static int report(CommandSourceStack source, CarryResult result) {
        Component message = Component.translatable("magnitude.carry." + result.code().name().toLowerCase(Locale.ROOT));
        if (result.succeeded()) source.sendSuccess(() -> message, false); else source.sendFailure(message);
        return result.succeeded() ? 1 : 0;
    }
    private static int failure(CommandSourceStack source, String key) {
        source.sendFailure(Component.translatable("magnitude.carry." + key)); return 0;
    }
}
