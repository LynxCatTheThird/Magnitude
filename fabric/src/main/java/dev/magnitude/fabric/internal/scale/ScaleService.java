package dev.magnitude.fabric.internal.scale;

import dev.magnitude.api.scale.ScaleLimits;
import dev.magnitude.api.scale.ShapeFactors;
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
 * Unified bounded base and shape service. Shape geometry derives from Rebuilt; no external Pehkui writer.
 * External scale modifiers and post-admission external base writes are unsupported.
 * No entity references survive in the revision map; the whole map ends with the server.
 */
public final class ScaleService implements ScaleAccess {
    private static final Map<MinecraftServer, ScaleService> SERVERS = new ConcurrentHashMap<>();
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
    private record Entry(long revision, double base, ShapeFactors shape) {}

    private ScaleService(MinecraftServer server) { this.server = server; }
    public static void start(MinecraftServer server) { SERVERS.put(server, new ScaleService(server)); }
    public static void stop(MinecraftServer server) { SERVERS.remove(server); }
    public static ScaleAccess forServer(MinecraftServer server) {
        ScaleService service = SERVERS.get(Objects.requireNonNull(server));
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
    @Override public ShapeFactors shape(LivingEntity entity) {
        observe(entity); // Identical server/thread/type admission to the main snapshot.
        return ShapeState.confirmed(entity);
    }
    @Override public ScaleChange setScale(CommandSourceStack source, LivingEntity target,
                                           double scale, long expectedRevision) {
        return change(source, target, scale, null, null, expectedRevision);
    }
    @Override public ScaleChange setShape(CommandSourceStack source, LivingEntity target,
                                          double width, double height, long expectedRevision) {
        return change(source, target, null, width, height, expectedRevision);
    }
    private ScaleChange change(CommandSourceStack source, LivingEntity target, Double requestedBase,
                                Double requestedWidth, Double requestedHeight, long expectedRevision) {
        requireThread();
        Objects.requireNonNull(source); Objects.requireNonNull(target);
        if (SERVERS.get(server) != this || server.isStopped()) return reject(ScaleChange.Code.NOT_READY, "Server session ended");
        if (source.getServer() != server || !source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return reject(ScaleChange.Code.DENIED, "Administrator permission required");
        }
        if (!(target.level() instanceof ServerLevel level) || level.getServer() != server || !supported(target)) {
            return reject(ScaleChange.Code.UNSUPPORTED_ENTITY, "This build supports players and pigs only");
        }
        if (dev.magnitude.fabric.internal.carry.CarryRuntime.isInvolved(target) || !target.isAlive()
                || target.isPassenger() || target.isVehicle() || target.getPose() == Pose.SLEEPING) {
            return reject(ScaleChange.Code.UNSUPPORTED_STATE, "Entity state cannot be resized");
        }
        var attribute = target.getAttribute(Attributes.SCALE);
        if (attribute == null || !attribute.getModifiers().isEmpty()) {
            return reject(ScaleChange.Code.UNSUPPORTED_STATE, "External native-scale modifiers are not supported");
        }
        double scale = requestedBase == null ? attribute.getBaseValue() : requestedBase;
        ShapeFactors oldShape = ShapeState.confirmed(target), nextShape;
        try { nextShape = requestedWidth == null ? oldShape : new ShapeFactors(requestedWidth, requestedHeight); }
        catch (IllegalArgumentException invalid) { return reject(ScaleChange.Code.INVALID_SCALE, invalid.getMessage()); }
        if (!LIMITS.contains(scale) || !LIMITS.contains(scale * nextShape.width()) || !LIMITS.contains(scale * nextShape.height())) {
            return reject(ScaleChange.Code.INVALID_SCALE, "Base and combined width/height must remain in [0.25,4]");
        }
        Entry entry = writes.get(target.getUUID());
        long revision = entry == null ? 0 : entry.revision();
        if (expectedRevision != revision || (entry != null && (Double.compare(entry.base(), attribute.getBaseValue()) != 0
                || !entry.shape().equals(oldShape)))) {
            return reject(ScaleChange.Code.CONFLICT, "Scale revision or confirmed state changed");
        }
        if (entry == null && writes.size() >= MAX_TRACKED) return reject(ScaleChange.Code.CAPACITY, "Scale session capacity reached");
        if (Double.compare(scale, attribute.getBaseValue()) == 0 && oldShape.equals(nextShape)) {
            return new ScaleChange(ScaleChange.Code.UNCHANGED, "Scale and shape unchanged", Optional.of(observe(target)));
        }
        double oldEffective = target.getScale();
        if (!Double.isFinite(oldEffective) || oldEffective <= 0) return reject(ScaleChange.Code.UNSUPPORTED_STATE, "Invalid native base");
        double ratio = scale / oldEffective;
        double width = target.getBbWidth() * ratio * nextShape.width() / oldShape.width();
        double height = target.getBbHeight() * ratio * nextShape.height() / oldShape.height();
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) {
            return reject(ScaleChange.Code.UNSUPPORTED_STATE, "Invalid geometry");
        }
        if (width > 16 || height > 16) return reject(ScaleChange.Code.BUDGET, "Candidate exceeds geometry query budget");
        Vec3 position = target.position();
        AABB candidate = new AABB(position.x - width / 2, position.y, position.z - width / 2,
                position.x + width / 2, position.y + height, position.z + width / 2);
        if (budgetTick != server.getTickCount()) {
            budgetTick = server.getTickCount(); remainingAttempts = 16; remainingCells = 8192; actorAttempts.clear();
        }
        UUID actor = source.getEntity() == null ? CONSOLE : source.getEntity().getUUID();
        int used = actorAttempts.getOrDefault(actor, 0);
        if (remainingAttempts <= 0 || used >= 8) return reject(ScaleChange.Code.BUDGET, "Resize tick budget exhausted");
        remainingAttempts--; actorAttempts.put(actor, used + 1);
        int entities = 0;
        for (Entity ignored : level.getAllEntities()) if (++entities > MAX_LEVEL_ENTITIES) {
            return reject(ScaleChange.Code.BUDGET, "Prototype level entity limit exceeded");
        }
        ScaleChange.Code preflight = validateSpace(level, target, candidate);
        if (preflight != null) return reject(preflight, "Candidate space cannot be confirmed");
        double previous = attribute.getBaseValue();
        boolean committed = false;
        try {
            attribute.setBaseValue(scale);
            ScaleChange.Code actual = ShapeState.preview(target, nextShape, () -> {
                ShapeState.refreshAtRoot(target, position);
                ScaleChange.Code failure = validateSpace(level, target, target.getBoundingBox());
                if (failure == null) ShapeState.commit(target, nextShape);
                return failure;
            });
            if (actual != null) return reject(actual, "Actual geometry rejected; previous state restored");
            writes.put(target.getUUID(), new Entry(Math.incrementExact(revision), scale, nextShape));
            committed = true;
            return new ScaleChange(ScaleChange.Code.APPLIED, "Scale and shape applied", Optional.of(observe(target)));
        } finally {
            if (!committed) {
                attribute.setBaseValue(previous);
                if (!ShapeState.confirmed(target).equals(oldShape)) ShapeState.commit(target, oldShape);
                ShapeState.refreshAtRoot(target, position);
            }
        }
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
    private static ScaleChange reject(ScaleChange.Code code, String detail) {
        return new ScaleChange(code, detail, Optional.empty());
    }
}
