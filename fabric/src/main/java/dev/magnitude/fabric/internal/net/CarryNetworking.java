package dev.magnitude.fabric.internal.net;

import dev.magnitude.fabric.api.carry.CarryView;
import dev.magnitude.fabric.api.carry.MagnitudeCarry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Bounded full snapshots with per-recipient acknowledgement bookkeeping, not world authority. */
public final class CarryNetworking {
    private static final Map<MinecraftServer, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final int MAX_RECIPIENTS_PER_TICK = 16;
    private static final class Session {
        final UUID epoch = UUID.randomUUID();
        List<CarryView> views = List.of();
        final Map<UUID, Long> sent = new HashMap<>();
        long sequence;
        int cursor;
    }
    private CarryNetworking() {}
    public static void register() { PayloadTypeRegistry.clientboundPlay().register(CarryStatePayload.TYPE, CarryStatePayload.CODEC); }
    public static boolean supports(ServerPlayer player) { return ServerPlayNetworking.canSend(player, CarryStatePayload.TYPE); }
    public static void start(MinecraftServer server) { SESSIONS.put(server, new Session()); }
    public static void stop(MinecraftServer server) { SESSIONS.remove(server); }
    public static void joined(ServerPlayer player) {
        if (!supports(player)) {
            player.connection.disconnect(net.minecraft.network.chat.Component.literal(
                    "This server requires Magnitude 0.0.3 or a client supporting carry_state_v2 and shape_v1."));
            return;
        }
        Session session = SESSIONS.get(player.level().getServer());
        if (session != null) session.sent.remove(player.getUUID());
    }
    public static void tick(MinecraftServer server) {
        Session session = SESSIONS.get(server);
        if (session == null) return;
        List<CarryView> views;
        try { views = MagnitudeCarry.forServer(server).views(); }
        catch (IllegalStateException unavailable) { views = List.of(); }
        if (!views.equals(session.views)) { session.views = List.copyOf(views); session.sequence++; }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        session.sent.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        if (players.isEmpty()) { session.cursor = 0; return; }
        int count = Math.min(MAX_RECIPIENTS_PER_TICK, players.size());
        for (int i = 0; i < count; i++) {
            ServerPlayer player = players.get(Math.floorMod(session.cursor++, players.size()));
            if (supports(player) && session.sent.getOrDefault(player.getUUID(), -1L) != session.sequence) {
                ServerPlayNetworking.send(player, new CarryStatePayload(session.epoch, session.sequence, session.views));
                session.sent.put(player.getUUID(), session.sequence);
            }
        }
        session.cursor = Math.floorMod(session.cursor, players.size());
    }
}
