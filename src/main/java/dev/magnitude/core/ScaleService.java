package dev.magnitude.core;

import dev.magnitude.Magnitude;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import virtuoel.pehkui.api.ScaleTypes;

/** Runtime boundary. Detects same-tick BASE/geometry changes and caches one immutable view. */
public final class ScaleService {
    private ScaleService() {}

    public static ScaleSnapshot snapshot(Entity entity) {
        var state = EntityState.of(entity);
        double base = Dimensions.size(entity);
        long tick = entity.level().getGameTime();
        var old = state.scaleSnapshot;
        if (old != null && state.scaleTick == tick && state.scaleSettings == Magnitude.settings
            && old.base() == base && old.width() == entity.getBbWidth() && old.height() == entity.getBbHeight()
            && old.proxyLimit() == state.proxyLimit) return old;
        state.scaleTick = tick;
        state.scaleSettings = Magnitude.settings;
        state.scaleWarning = !Double.isFinite(base) || base <= 0 ? "invalid runtime size" : "none";
        base = finite(base);
        // Runtime coefficients already include inheritance and safety modifiers.
        double vanilla = entity instanceof LivingEntity living
            && !virtuoel.pehkui.api.PehkuiConfig.COMMON.applyVanillaScale.get() ? living.getScale() : 1;
        double footprint = base;
        ScaleSnapshot next = new ScaleSnapshot(state.scaleRevision, base, entity.getBbWidth(), entity.getBbHeight(),
            entity.getEyeHeight(), value(ScaleTypes.MODEL_WIDTH, entity) * vanilla,
            value(ScaleTypes.MODEL_HEIGHT, entity) * vanilla, value(ScaleTypes.MOTION, entity),
            value(ScaleTypes.JUMP_HEIGHT, entity), value(ScaleTypes.ATTACK, entity),
            value(ScaleTypes.REACH, entity), state.proxyLimit, footprint,
            Math.min(4.2, 0.42 * Math.sqrt(Math.clamp(base, ScaleSafety.MINIMUM, 100))) / Math.max(1,value(ScaleTypes.MOTION, entity)));
        if (old == null || !old.equals(next)) {
            next = new ScaleSnapshot(++state.scaleRevision, next.base(), next.width(), next.height(), next.eyeHeight(),
                next.modelWidth(), next.modelHeight(), next.motionFactor(), next.jumpFactor(), next.attackFactor(),
                next.reachFactor(), next.proxyLimit(), next.footprintScale(), next.jumpVelocityLimit());
        }
        state.scaleSnapshot = next;
        return next;
    }

    /** Call after a direct dependent-type write by a runtime adapter. */
    public static void invalidate(Entity entity) { EntityState.of(entity).scaleTick = Long.MIN_VALUE; }
    private static double value(virtuoel.pehkui.api.ScaleType type, Entity entity) {
        return finite(type.getScaleData(entity).getScale());
    }
    private static double finite(double value) { return Double.isFinite(value) && value > 0 ? value : 1; }
}
