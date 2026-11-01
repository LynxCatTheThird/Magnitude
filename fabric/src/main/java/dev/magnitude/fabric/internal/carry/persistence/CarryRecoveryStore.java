package dev.magnitude.fabric.internal.carry.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Small, world-local write-ahead recovery store. All operations belong to its owner
 * server thread. It never teleports, spawns, deletes, or restores a captured relation.
 *
 * <p>The entity attachment and journal deliberately overlap: removing a journal row
 * cannot prove that the entity's ordinary save has reached disk. An old persisted
 * attachment therefore remains sufficient to request safe restoration after a crash.
 * The world adapter must first prefer the entity's currently valid saved position;
 * safePosition is a fallback to validate, never an unconditional teleport destination.
 */
public final class CarryRecoveryStore {
    private static final int SCHEMA = 1;
    private static final int MAX_BYTES = 1_048_576;
    private static final AttachmentType<RecoveryToken> TOKEN = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath("magnitude", "carry_recovery"), RecoveryToken.CODEC);
    private final Thread owner = Thread.currentThread();
    private final Path file;
    private final int maxEntries;
    private Map<UUID, JournalEntry> records;
    private record JournalEntry(RecoveryToken token, boolean released) {}
    private long generation;
    private boolean writeFailed;

    /** Call during mod initialization, before entity loading. No copyOnDeath or client sync. */
    public static void initAttachments() { Objects.requireNonNull(TOKEN); }

    private CarryRecoveryStore(Path file, int maxEntries, long generation, Map<UUID, JournalEntry> records) {
        this.file = file;
        this.maxEntries = maxEntries;
        this.generation = generation;
        this.records = records;
    }

    /**
     * Recovery-only fallback after open fails. Does not read, create, replace or delete
     * any world file. Existing entity markers can still be validated for a bounded
     * safe exit, but capture and durable completion remain disabled. The runtime must
     * not reapply held flags after placement merely because complete cannot write.
     */
    public static CarryRecoveryStore openRecoveryOnly(Path worldRoot, int maxEntries) {
        Objects.requireNonNull(worldRoot, "worldRoot");
        if (maxEntries < 1 || maxEntries > 1024) throw new IllegalArgumentException("maxEntries must be in [1,1024]");
        Path file = worldRoot.resolve("data").resolve("magnitude").resolve("carry-recovery.json").toAbsolutePath().normalize();
        CarryRecoveryStore store = new CarryRecoveryStore(file, maxEntries, 0, new LinkedHashMap<>());
        store.writeFailed = true;
        return store;
    }

    public static CarryRecoveryStore open(Path worldRoot, int maxEntries) throws IOException {
        Objects.requireNonNull(worldRoot, "worldRoot");
        if (maxEntries < 1 || maxEntries > 1024) throw new IllegalArgumentException("maxEntries must be in [1,1024]");
        Path file = worldRoot.resolve("data").resolve("magnitude").resolve("carry-recovery.json").toAbsolutePath().normalize();
        Files.createDirectories(file.getParent());
        Map<UUID, JournalEntry> loaded = new LinkedHashMap<>();
        long generation = 0;
        if (Files.exists(file)) {
            if (Files.size(file) > MAX_BYTES) throw new IOException("Recovery journal exceeds byte limit");
            try (JsonReader reader = new JsonReader(new StringReader(Files.readString(file, StandardCharsets.UTF_8)))) {
                reader.setStrictness(Strictness.STRICT);
                reader.setNestingLimit(16);
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing recovery journal content");
                if (!root.getAsJsonPrimitive("schemaVersion").isNumber()
                        || root.get("schemaVersion").getAsBigDecimal().intValueExact() != SCHEMA) {
                    throw new IOException("Unsupported recovery journal schema");
                }
                if (!root.getAsJsonPrimitive("generation").isNumber()) throw new IOException("Invalid recovery journal generation");
                generation = root.get("generation").getAsBigDecimal().longValueExact();
                if (generation < 0) throw new IOException("Invalid recovery journal generation");
                JsonArray rows = root.getAsJsonArray("records");
                if (rows.size() > maxEntries) throw new IOException("Recovery journal exceeds entry limit");
                var ids = new HashSet<UUID>();
                for (JsonElement row : rows) {
                    RecoveryToken token = RecoveryToken.CODEC.parse(JsonOps.INSTANCE, row).getOrThrow();
                    long serializedGeneration = row.getAsJsonObject().get("generation").getAsBigDecimal().longValueExact();
                    if (serializedGeneration != token.generation()) throw new IOException("Invalid recovery record generation");
                    JsonElement releasedField = row.getAsJsonObject().get("released");
                    boolean released = false;
                    if (releasedField != null) {
                        if (!releasedField.isJsonPrimitive() || !releasedField.getAsJsonPrimitive().isBoolean()) {
                            throw new IOException("Invalid recovery completion state");
                        }
                        released = releasedField.getAsBoolean();
                    }
                    if (token.generation() > generation || !ids.add(token.id())
                            || loaded.putIfAbsent(token.target(), new JournalEntry(token, released)) != null) {
                        throw new IOException("Conflicting recovery journal records");
                    }
                }
            } catch (RuntimeException exception) {
                throw new IOException("Malformed recovery journal; capture remains disabled", exception);
            }
        }
        return new CarryRecoveryStore(file, maxEntries, generation, loaded);
    }

    /** Raw marker lookup; use recoverableToken before restoring any entity state. */
    public static Optional<RecoveryToken> token(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        return Optional.ofNullable(((AttachmentTarget) entity).getAttached(TOKEN));
    }

    /**
     * Selects restore-only metadata. A later durable intent/completion supersedes an
     * older entity save. Equal generations with different IDs, or a journal older
     * than the marker, are quarantined rather than guessed. Never restores HELD.
     */
    public Optional<RecoveryToken> recoverableToken(Entity entity) throws IOException {
        requireThread();
        Optional<RecoveryToken> marker = token(entity);
        if (marker.isEmpty()) return marker;
        RecoveryToken token = marker.orElseThrow();
        if (!token.target().equals(entity.getUUID())) throw new IOException("Recovery marker target mismatch");
        JournalEntry row = records.get(entity.getUUID());
        if (row == null) return marker;
        RecoveryToken durable = row.token();
        if (durable.generation() < token.generation()
                || (durable.generation() == token.generation() && !durable.id().equals(token.id()))) {
            throw new IOException("Recovery marker generation conflicts with journal");
        }
        return Optional.of(durable);
    }

    /**
     * Call only after permission/geometry checks, before passenger attachment or flag writes.
     * Persistence failure throws and capture must not proceed. The supplied position must
     * already have been verified by the world adapter; no world query is performed here.
     * Schema 1 admits only targets with no remaining fire/frozen ticks. Consequently
     * the runtime may clear carry-induced thermal state after safe placement without
     * erasing a pre-capture effect; existing heat must be rejected, never silently cleared.
     */
    public RecoveryToken prepare(UUID host, LivingEntity target, String dimension, Vec3 safePosition) throws IOException {
        requireThread();
        Objects.requireNonNull(host, "host"); Objects.requireNonNull(target, "target");
        if (writeFailed) throw new IOException("Recovery store is read-only; capture disabled");
        if (target.getRemainingFireTicks() > 0 || target.getTicksFrozen() > 0) {
            throw new IOException("Burning or frozen targets cannot enter this carry schema");
        }
        if (host.equals(target.getUUID())) throw new IllegalArgumentException("Cannot capture self");
        JournalEntry existing = records.get(target.getUUID());
        if (token(target).isPresent() || (existing != null && !existing.released())) {
            throw new IOException("Target has pending recovery metadata");
        }
        if (existing == null && records.size() >= maxEntries) throw new IOException("Recovery journal capacity reached");
        long next = nextGeneration();
        RecoveryToken token = new RecoveryToken(UUID.randomUUID(), next, host, target.getUUID(), dimension,
                safePosition, target.isNoGravity(), target instanceof Mob mob && mob.isNoAi(), target.isPermanentlyInvulnerable());
        Map<UUID, JournalEntry> candidate = new LinkedHashMap<>(records);
        candidate.put(token.target(), new JournalEntry(token, false));
        persist(candidate, next);
        records = candidate;
        generation = next;
        ((AttachmentTarget) target).setAttached(TOKEN, token);
        return token;
    }

    /**
     * Call only after actual placement and original flag restoration have succeeded.
     * Persist a RELEASED tombstone with the actual destination before clearing the
     * marker. This preserves the latest original flags even if an older marker is
     * still on disk when the same entity is captured/released again. Tombstones are
     * bounded by maxEntries and cleared only after a marker-free entity load.
     */
    public void complete(Entity target) throws IOException {
        requireThread();
        Optional<RecoveryToken> marker = recoverableToken(target);
        if (marker.isEmpty()) return;
        RecoveryToken original = marker.orElseThrow();
        RecoveryToken restored = new RecoveryToken(original.id(), original.generation(), original.host(),
                original.target(), target.level().dimension().identifier().toString(), target.position(),
                original.originalNoGravity(), original.originalNoAi(), original.originalInvulnerable());
        Map<UUID, JournalEntry> candidate = new LinkedHashMap<>(records);
        if (!candidate.containsKey(target.getUUID()) && candidate.size() >= maxEntries) {
            // There is no later intent for this target. Its old persisted marker
            // already contains the original flags, and a full journal prevents a
            // new capture of this unrecorded target. Do not block an existing exit
            // solely to allocate a completion tombstone for an orphan marker.
            if (writeFailed) throw new IOException("Recovery journal requires storage repair");
            ((AttachmentTarget) target).removeAttached(TOKEN);
            return;
        }
        candidate.put(target.getUUID(), new JournalEntry(restored, true));
        long next = nextGeneration();
        persist(candidate, next);
        records = candidate;
        generation = next;
        ((AttachmentTarget) target).removeAttached(TOKEN);
    }

    /**
     * Startup/entity-load cleanup only, when no current-session carry exists. A loaded
     * entity without a marker is already in its ordinary saved state; a lone old
     * journal intent does not justify changing its position, flags, or ownership.
     */
    public void reconcileUnmarked(Entity target) throws IOException {
        requireThread();
        if (token(target).isPresent()) throw new IOException("Cannot discard a marked recovery intent");
        if (!records.containsKey(target.getUUID())) return;
        Map<UUID, JournalEntry> candidate = new LinkedHashMap<>(records);
        candidate.remove(target.getUUID());
        long next = nextGeneration();
        persist(candidate, next);
        records = candidate;
        generation = next;
    }

    public Map<UUID, RecoveryToken> records() {
        requireThread();
        Map<UUID, RecoveryToken> snapshot = new LinkedHashMap<>();
        records.forEach((id, entry) -> snapshot.put(id, entry.token()));
        return Map.copyOf(snapshot);
    }
    public boolean isReleased(UUID target) {
        requireThread();
        JournalEntry entry = records.get(Objects.requireNonNull(target));
        return entry != null && entry.released();
    }
    public long generation() { requireThread(); return generation; }
    public boolean writable() { requireThread(); return !writeFailed; }
    public Path path() { return file; }

    private long nextGeneration() throws IOException {
        if (generation == Long.MAX_VALUE) throw new IOException("Recovery journal generation exhausted");
        return generation + 1;
    }

    private void persist(Map<UUID, JournalEntry> candidate, long nextGeneration) throws IOException {
        if (writeFailed) throw new IOException("Recovery journal failed earlier; restart after repairing storage");
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA);
        root.addProperty("generation", nextGeneration);
        JsonArray rows = new JsonArray();
        candidate.values().stream().sorted(Comparator.comparing(entry -> entry.token().target().toString()))
                .forEach(entry -> {
                    JsonObject row = RecoveryToken.CODEC.encodeStart(JsonOps.INSTANCE, entry.token()).getOrThrow().getAsJsonObject();
                    row.addProperty("released", entry.released());
                    rows.add(row);
                });
        root.add("records", rows);
        byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Recovery journal exceeds byte limit");
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            // Do not silently downgrade to non-atomic replacement on an unsupported filesystem.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel directory = FileChannel.open(file.getParent(), StandardOpenOption.READ)) {
                directory.force(true);
            }
        } catch (IOException exception) {
            // A rename might already have happened. Reject later writes rather than overwrite
            // that durable candidate using stale in-memory state. Existing exits remain allowed
            // at the runtime level and keep their marker until storage can be reconciled.
            writeFailed = true;
            throw exception;
        }
    }

    private void requireThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Recovery store requires its owner server thread");
    }
}
