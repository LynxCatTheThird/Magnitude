package dev.magnitude.fabric.internal;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.magnitude.fabric.api.MagnitudeScales;
import dev.magnitude.fabric.MagnitudeMod;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class ScaleCommands {
    private ScaleCommands() {}
    public static void register() {
        registerShape();
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                Commands.literal("magnitude")
                        .then(Commands.literal("status").executes(context -> {
                            String version = FabricLoader.getInstance().getModContainer(MagnitudeMod.ID).orElseThrow()
                                    .getMetadata().getVersion().getFriendlyString();
                            context.getSource().sendSuccess(() -> Component.literal("Magnitude " + version + ": base and combined shape scales [0.25,4]; /magnitude shape <width> <height>; consent-based hand carrying for players and pigs. Use /magnitude carry for help."), false);
                            return 1;
                        }))
                        .then(Commands.literal("scale").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)).then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                .executes(context -> apply(context, context.getSource().getEntityOrException()))
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(context -> apply(context, EntityArgument.getEntity(context, "target"))))))));
    }
    private static void registerShape() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                Commands.literal("magnitude").then(Commands.literal("shape")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.argument("width", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("height", DoubleArgumentType.doubleArg())
                                        .executes(context -> applyShape(context, context.getSource().getEntityOrException()))
                                        .then(Commands.argument("target", EntityArgument.entity())
                                                .executes(context -> applyShape(context, EntityArgument.getEntity(context, "target")))))))));
    }
    private static int applyShape(CommandContext<CommandSourceStack> context, Entity target) {
        var source = context.getSource();
        if (!(target instanceof LivingEntity living)) {
            source.sendFailure(Component.literal("This entity does not support shape changes.")); return 0;
        }
        var service = MagnitudeScales.forServer(source.getServer());
        try {
            var result = service.setShape(source, living, DoubleArgumentType.getDouble(context, "width"),
                    DoubleArgumentType.getDouble(context, "height"), service.observe(living).revision());
            if (result.succeeded()) source.sendSuccess(() -> Component.translatable("magnitude.shape.applied", service.shape(living).width(), service.shape(living).height()), false);
            else source.sendFailure(Component.literal(result.code() + ": " + result.detail()));
            return result.succeeded() ? 1 : 0;
        } catch (IllegalArgumentException invalid) {
            source.sendFailure(Component.literal(invalid.getMessage())); return 0;
        }
    }
    private static int apply(CommandContext<CommandSourceStack> context, Entity target) throws CommandSyntaxException {
        var source = context.getSource();
        if (!(target instanceof LivingEntity living)) {
            source.sendFailure(Component.literal("This entity does not support native uniform scale."));
            return 0;
        }
        var service = MagnitudeScales.forServer(source.getServer());
        long revision;
        try { revision = service.observe(living).revision(); }
        catch (IllegalArgumentException e) { source.sendFailure(Component.literal(e.getMessage())); return 0; }
        var result = service.setScale(source, living, DoubleArgumentType.getDouble(context, "value"), revision);
        if (result.succeeded()) source.sendSuccess(() -> Component.literal(result.detail() + " " + result.snapshot().orElseThrow().effectiveScale()), false);
        else source.sendFailure(Component.literal(result.code() + ": " + result.detail()));
        return result.succeeded() ? 1 : 0;
    }
}
