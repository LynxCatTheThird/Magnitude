package dev.magnitude.fabric.internal;

import dev.magnitude.api.scale.ScaleLimits;
import dev.magnitude.api.scale.ScaleSnapshot;
import dev.magnitude.fabric.api.ScaleAccess;
import dev.magnitude.fabric.api.ScaleChange;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import java.util.ArrayList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded native-scale prototype, not a full body-geometry backend.
 * External scale modifiers and post-admission external base writes are unsupported.
 * No entity references survive in the revision map; the whole map ends with the server.
 */
public final class NativeScaleService implements ScaleAccess {
    private static final Map<MinecraftServer, NativeScaleService> SERVERS = new ConcurrentHashMap<>();
    private static final ScaleLimits LIMITS = new ScaleLimits(0.25, 4.0);
    private static final int MAX_TRACKED = 512;
    private static final long MAX_CANDIDATE_CELLS = 4096;
    private static final int MAX_LEVEL_ENTITIES = 512;
    private static final int MAX_NEARBY_ENTITIES = 128;
    private final MinecraftServer server;
    private final Map<UUID, Entry> writes = new HashMap<>();
    private final Map<UUID, Integer> actorAttempts = new HashMap<>();
    private int budgetTick = Integer.MIN_VALUE;
    private int remainingAttempts;
    private long remainingCells;
    private static final UUID CONSOLE = new UUID(0, 0);
    private record Entry(long revision, double base) {}

    private NativeScaleService(MinecraftServer server) { this.server = server; }
    public static void start(MinecraftServer server) { SERVERS.put(server, new NativeScaleService(server)); }
    public static void stop(MinecraftServer server) { SERVERS.remove(server); }
    public static ScaleAccess forServer(MinecraftServer server) {
        NativeScaleService service = SERVERS.get(Objects.requireNonNull(server));
        if (service == null) throw new IllegalStateException("Magnitude server session is not running");
        return service;
    }
    @Override public ScaleLimits limits() { return LIMITS; }

    private void requireThread() {
        if (!server.isSameThread()) throw new IllegalStateException("Scale operations require the server thread");
    }
    private boolean supported(LivingEntity entity) {
        return entity instanceof ServerPlayer || entity.getType() == EntityTypes.PIG;
    }
    @Override public ScaleSnapshot observe(LivingEntity entity) {
        requireThread();
        if (!(entity.level() instanceof ServerLevel level) || level.getServer() != server || !supported(entity)) {
            throw new IllegalArgumentException("S0 supports server players and pigs in this server only");
        }
        var attribute = entity.getAttribute(Attributes.SCALE);
        if (attribute == null) throw new IllegalArgumentException("Entity has no native scale attribute");
        Entry entry = writes.get(entity.getUUID());
        return new ScaleSnapshot(entity.getUUID(), entry == null ? 0 : entry.revision(),
                attribute.getBaseValue(), entity.getScale(), entity.getBbWidth(), entity.getBbHeight(), entity.getEyeHeight());
    }
    @Override public ScaleChange setScale(CommandSourceStack source, LivingEntity target,
                                           double scale, long expectedRevision) {
        requireThread();
        Objects.requireNonNull(source); Objects.requireNonNull(target);
        if (SERVERS.get(server) != this || server.isStopped()) return reject(ScaleChange.Code.NOT_READY, "Server session ended");
        if (source.getServer() != server || !source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return reject(ScaleChange.Code.DENIED, "Administrator permission required");
        }
        if (!LIMITS.contains(scale)) return reject(ScaleChange.Code.INVALID_SCALE, "S0 accepts finite scale in [0.25, 4.0]");
        if (!(target.level() instanceof ServerLevel level) || level.getServer() != server || !supported(target)) {
            return reject(ScaleChange.Code.UNSUPPORTED_ENTITY, "S0 supports players and pigs only");
        }
        if (dev.magnitude.fabric.internal.carry.CarryRuntime.isInvolved(target) || !target.isAlive() || target.isPassenger() || target.isVehicle() || target.getPose() == Pose.SLEEPING) {
            return reject(ScaleChange.Code.UNSUPPORTED_STATE, "Entity state cannot be resized by this prototype");
        }
        var attribute = target.getAttribute(Attributes.SCALE);
        if (attribute == null || !attribute.getModifiers().isEmpty()) {
            return reject(ScaleChange.Code.UNSUPPORTED_STATE, "External native-scale modifiers are not supported by S0");
        }
        Entry entry = writes.get(target.getUUID());
        long revision = entry == null ? 0 : entry.revision();
        if (expectedRevision != revision || (entry != null && Double.compare(entry.base(), attribute.getBaseValue()) != 0)) {
            return reject(ScaleChange.Code.CONFLICT, "Scale revision or native base changed");
        }
        if (entry == null && writes.size() >= MAX_TRACKED) return reject(ScaleChange.Code.CAPACITY, "Server scale-session capacity reached");
        if (Double.compare(scale, attribute.getBaseValue()) == 0) {
            return new ScaleChange(ScaleChange.Code.UNCHANGED, "Scale unchanged", Optional.of(observe(target)));
        }
        double oldEffective = target.getScale();
        if (!Double.isFinite(oldEffective) || oldEffective <= 0) return reject(ScaleChange.Code.UNSUPPORTED_STATE, "Invalid native scale");
        double ratio = scale / oldEffective;
        double width = target.getBbWidth() * ratio, height = target.getBbHeight() * ratio;
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) {
            return reject(ScaleChange.Code.UNSUPPORTED_STATE, "Invalid geometry");
        }
        if (width > 16 || height > 16) return reject(ScaleChange.Code.BUDGET, "Candidate geometry exceeds S0 query budget");
        Vec3 position = target.position();
        AABB candidate = new AABB(position.x - width / 2, position.y, position.z - width / 2,
                position.x + width / 2, position.y + height, position.z + width / 2);
        if (budgetTick != server.getTickCount()) {
            budgetTick = server.getTickCount(); remainingAttempts = 16; remainingCells = 8192; actorAttempts.clear();
        }
        UUID actor = source.getEntity() == null ? CONSOLE : source.getEntity().getUUID();
        int used = actorAttempts.getOrDefault(actor, 0);
        if (remainingAttempts <= 0 || used >= 8) return reject(ScaleChange.Code.BUDGET, "Native resize tick budget exhausted");
        remainingAttempts--; actorAttempts.put(actor, used + 1);
        // Temporary S0 admission bound: even the engine's internal section scan cannot
        // visit an arbitrarily populated level. This is not the future spatial index.
        int entities = 0;
        for (Entity ignored : level.getAllEntities()) {
            if (++entities > MAX_LEVEL_ENTITIES) return reject(ScaleChange.Code.BUDGET, "S0 level entity limit exceeded");
        }
        ScaleChange.Code preflight = validateSpace(level, target, candidate);
        if (preflight != null) return reject(preflight, "Candidate space cannot be confirmed");
        double previous = attribute.getBaseValue();
        attribute.setBaseValue(scale);
        refreshAtRoot(target, position);
        ScaleChange.Code actual = validateSpace(level, target, target.getBoundingBox());
        if (actual != null) {
            attribute.setBaseValue(previous);
            refreshAtRoot(target, position);
            return reject(actual, "Actual native geometry rejected; previous state restored");
        }
        writes.put(target.getUUID(), new Entry(Math.incrementExact(revision), scale));
        return new ScaleChange(ScaleChange.Code.APPLIED, "Native scale applied", Optional.of(observe(target)));
    }
    private ScaleChange.Code validateSpace(ServerLevel level, LivingEntity target, AABB box) {
        if (box.minY < level.getMinY() || box.maxY > level.getMaxY() || !level.getWorldBorder().isWithinBounds(box)) {
            return ScaleChange.Code.BLOCKED;
        }
        // Match the pinned 26.3 BlockCollisions traversal, including inclusive padding.
        int minX = (int) Math.floor(box.minX - 1.0E-7) - 1;
        int minY = (int) Math.floor(box.minY - 1.0E-7) - 1;
        int minZ = (int) Math.floor(box.minZ - 1.0E-7) - 1;
        int maxX = (int) Math.floor(box.maxX + 1.0E-7) + 1;
        int maxY = (int) Math.floor(box.maxY + 1.0E-7) + 1;
        int maxZ = (int) Math.floor(box.maxZ + 1.0E-7) + 1;
        long cells = ((long) maxX - minX + 1) * ((long) maxY - minY + 1) * ((long) maxZ - minZ + 1);
        if (cells <= 0 || cells > MAX_CANDIDATE_CELLS || cells > remainingCells) return ScaleChange.Code.BUDGET;
        remainingCells -= cells;
        for (int x = minX >> 4; x <= maxX >> 4; x++) for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
            if (!level.hasChunk(x, z)) return ScaleChange.Code.NOT_LOADED;
        }
        if (level.getBlockCollisions(target, box).iterator().hasNext()) return ScaleChange.Code.BLOCKED;
        var nearby = new ArrayList<Entity>();
        level.getEntities(EntityTypeTest.forClass(Entity.class), box.inflate(1.0E-7), entity -> true, nearby, MAX_NEARBY_ENTITIES + 1);
        if (nearby.size() > MAX_NEARBY_ENTITIES) return ScaleChange.Code.BUDGET;
        for (Entity other : nearby) {
            if (other != target && !other.isSpectator() && target.canCollideWith(other)) return ScaleChange.Code.BLOCKED;
        }
        return null;
    }
    private static void refreshAtRoot(LivingEntity target, Vec3 position) {
        // Avoid vanilla's extra automatic free-position searches. Collision is validated
        // explicitly before/after; noPhysics is restored even when refresh throws.
        boolean noPhysics = target.noPhysics;
        try {
            target.noPhysics = true;
            target.refreshDimensions();
        } finally {
            target.noPhysics = noPhysics;
        }
        target.setPos(position);
    }
    private static ScaleChange reject(ScaleChange.Code code, String detail) {
        return new ScaleChange(code, detail, Optional.empty());
    }
}
