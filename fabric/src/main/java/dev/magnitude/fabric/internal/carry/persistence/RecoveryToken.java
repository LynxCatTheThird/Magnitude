package dev.magnitude.fabric.internal.carry.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/** Immutable restore-only metadata. A token never grants consent or restores HELD.
 * originalInvulnerable is the permanent flag, never transient damage immunity.
 * Schema 1 tokens are prepared only while fire/frozen ticks are non-positive/zero;
 * carry-induced thermal state can therefore be cleared after verified safe placement. */
public record RecoveryToken(UUID id, long generation, UUID host, UUID target,
                            String dimension, Vec3 safePosition,
                            boolean originalNoGravity, boolean originalNoAi,
                            boolean originalInvulnerable) {
    private static final Codec<String> DIMENSION = Codec.STRING.validate(value ->
            validDimension(value) ? DataResult.success(value) : DataResult.error(() -> "Invalid recovery dimension"));
    private static final Codec<Long> GENERATION = Codec.LONG.validate(value ->
            value > 0 ? DataResult.success(value) : DataResult.error(() -> "Invalid recovery generation"));
    private static final Codec<Vec3> POSITION = Vec3.CODEC.validate(value ->
            finite(value) ? DataResult.success(value) : DataResult.error(() -> "Non-finite recovery position"));

    public static final Codec<RecoveryToken> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(RecoveryToken::id),
            GENERATION.fieldOf("generation").forGetter(RecoveryToken::generation),
            UUIDUtil.CODEC.fieldOf("host").forGetter(RecoveryToken::host),
            UUIDUtil.CODEC.fieldOf("target").forGetter(RecoveryToken::target),
            DIMENSION.fieldOf("dimension").forGetter(RecoveryToken::dimension),
            POSITION.fieldOf("safePosition").forGetter(RecoveryToken::safePosition),
            Codec.BOOL.fieldOf("originalNoGravity").forGetter(RecoveryToken::originalNoGravity),
            Codec.BOOL.fieldOf("originalNoAi").forGetter(RecoveryToken::originalNoAi),
            Codec.BOOL.fieldOf("originalInvulnerable").forGetter(RecoveryToken::originalInvulnerable)
    ).apply(instance, RecoveryToken::new));

    public RecoveryToken {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(safePosition, "safePosition");
        if (generation < 1) throw new IllegalArgumentException("generation must be positive");
        if (!validDimension(dimension)) throw new IllegalArgumentException("Invalid recovery dimension");
        if (!finite(safePosition)) throw new IllegalArgumentException("Non-finite recovery position");
    }

    private static boolean validDimension(String value) {
        return value != null && value.length() <= 256 && Identifier.tryParse(value) != null;
    }
    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
