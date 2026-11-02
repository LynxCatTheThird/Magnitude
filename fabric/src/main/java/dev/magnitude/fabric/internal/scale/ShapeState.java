package dev.magnitude.fabric.internal.scale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.magnitude.api.scale.ShapeFactors;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** One confirmed shape record; a server-local candidate never enters storage or the network. */
public final class ShapeState {
    private static final Codec<Double> FACTOR = Codec.DOUBLE.flatXmap(ShapeState::factor, ShapeState::factor);
    private static DataResult<Double> factor(double value) {
        return Double.isFinite(value) && value >= .25 && value <= 4 ? DataResult.success(value)
                : DataResult.error(() -> "Shape factor must be finite and in [0.25,4]");
    }
    public static final Codec<ShapeFactors> CODEC = RecordCodecBuilder.<ShapeFactors>create(instance -> instance.group(
            Codec.intRange(1, 1).fieldOf("schema").forGetter(value -> 1),
            FACTOR.fieldOf("width").forGetter(ShapeFactors::width),
            FACTOR.fieldOf("height").forGetter(ShapeFactors::height)
    ).apply(instance, (schema, width, height) -> new ShapeFactors(width, height)));
    private static final StreamCodec<RegistryFriendlyByteBuf, ShapeFactors> STREAM_CODEC = new StreamCodec<>() {
        @Override public ShapeFactors decode(RegistryFriendlyByteBuf buffer) {
            return new ShapeFactors(buffer.readDouble(), buffer.readDouble());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, ShapeFactors value) {
            buffer.writeDouble(value.width()); buffer.writeDouble(value.height());
        }
    };
    private static final AttachmentType<ShapeFactors> TYPE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath("magnitude", "shape_v1"), builder -> builder
                    .persistent(CODEC).copyOnDeath().syncWith(STREAM_CODEC, AttachmentSyncPredicate.all(), 32));
    private record Candidate(Entity entity, ShapeFactors shape) {}
    private static final ThreadLocal<Candidate> CANDIDATE = new ThreadLocal<>();
    private ShapeState() {}
    public static void initialize() { /* Initializes the attachment before entity loading. */ }
    public static ShapeFactors confirmed(Entity entity) {
        return ((AttachmentTarget)entity).getAttachedOrElse(TYPE, ShapeFactors.IDENTITY);
    }
    public static ShapeFactors geometry(Entity entity) {
        Candidate candidate = CANDIDATE.get();
        return candidate != null && candidate.entity() == entity ? candidate.shape() : confirmed(entity);
    }
    public static <T> T preview(Entity entity, ShapeFactors shape, Supplier<T> action) {
        if (CANDIDATE.get() != null) throw new IllegalStateException("Recursive shape preview");
        CANDIDATE.set(new Candidate(entity, shape));
        try { return action.get(); } finally { CANDIDATE.remove(); }
    }
    public static void commit(LivingEntity entity, ShapeFactors shape) { ((AttachmentTarget)entity).setAttached(TYPE, shape); }
    public static void watch(Entity entity) {
        ((AttachmentTarget)entity).onAttachedSet(TYPE).register((oldValue, newValue) -> {
            Candidate candidate = CANDIDATE.get();
            if (candidate != null && candidate.entity() == entity) return;
            if (entity instanceof LivingEntity living) refreshAtRoot(living, living.position());
        });
    }
    public static void refreshAtRoot(LivingEntity entity, Vec3 position) {
        boolean noPhysics = entity.noPhysics;
        try { entity.noPhysics = true; entity.refreshDimensions(); }
        finally { entity.noPhysics = noPhysics; }
        entity.setPos(position);
    }
}
