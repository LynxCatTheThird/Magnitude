package dev.magnitude;
import dev.magnitude.content.Content;
import dev.magnitude.core.EntityState;
import dev.magnitude.core.Settings;
import dev.magnitude.interaction.Interactions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import java.util.HashMap;
import java.util.Map;

public final class Magnitude implements ModInitializer {
    public static final String ID = "magnitude";
    public static Settings settings;
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(ID, path); }
    public void onInitialize() {
        settings = Settings.load();
        dev.magnitude.core.ScaleSafety.register();
        Content.register();
        Interactions.register();
        ServerTickEvents.START_SERVER_TICK.register(server -> {dev.magnitude.interaction.Impact.beginTick();dev.magnitude.interaction.EntityQueries.beginTick();});
        ServerTickEvents.END_SERVER_TICK.register(Interactions::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> EntityState.of(entity).initialized = false);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> entity instanceof LivingEntity living && player instanceof ServerPlayer server && Interactions.toolUse(server, living, hand) ? InteractionResult.SUCCESS : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> entity instanceof LivingEntity living && player instanceof ServerPlayer server && Interactions.crush(server, living) ? InteractionResult.SUCCESS : InteractionResult.PASS);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> Commands.register(dispatcher));
    }
}
