package dev.magnitude.core;

import dev.magnitude.Magnitude;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;

public final class Dimensions {
    private Dimensions() {}
    public static double size(Entity entity) { return ScaleTypes.BASE.getScaleData(entity).getScale(); }
    public static double target(Entity entity) { return ScaleTypes.BASE.getScaleData(entity).getTargetScale() * (float)vanillaFactor(entity); }
    private static double vanillaFactor(Entity entity) {
        return entity instanceof LivingEntity living && virtuoel.pehkui.api.PehkuiConfig.COMMON.applyVanillaScale.get() && !virtuoel.pehkui.api.PehkuiConfig.COMMON.vanillaScaleSyncBack.get() ? living.getScale() : 1;
    }
    /** Effective target after the float storage used by the scaling runtime. */
    public static double representable(Entity entity, double value) {
        return (float)(Rules.bounded(value,Magnitude.settings.minimum,Magnitude.settings.maximum)/vanillaFactor(entity))*(float)vanillaFactor(entity);
    }
    public static boolean operator(ServerPlayer player) { return player.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER); }
    public static boolean canChange(ServerPlayer actor, LivingEntity target) {
        if (!target.isAlive() || target.isSpectator() || actor.isSpectator() || actor.level() != target.level()) return false;
        if (actor == target) return Magnitude.settings.allowSelfChange || operator(actor);
        double reach = Math.min(64, Math.max(32, size(actor) * 3));
        if (target.getBoundingBox().distanceToSqr(actor.getEyePosition()) > reach * reach || !lineOfSight(actor,target)) return false;
        if (target instanceof Player) return EntityState.of(target).acceptResize;
        return actor.getAbilities().mayBuild && !actor.level().getServer().isUnderSpawnProtection((ServerLevel)actor.level(), target.blockPosition(), actor);
    }
    public static boolean lineOfSight(ServerPlayer actor, Entity target) {
        // The visual eye of an extreme target may be billions of blocks away;
        // interactions address its nearby bounded physical proxy.
        return actor.level().clip(new net.minecraft.world.level.ClipContext(actor.getEyePosition(),target.getBoundingBox().getCenter(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,actor)).getType()==net.minecraft.world.phys.HitResult.Type.MISS;
    }
    public static boolean change(ServerPlayer actor, LivingEntity entity, double value, int ticks) {
        if (!canChange(actor, entity)) return false;
        return set(entity, value, ticks);
    }
    public static boolean canApplyEffect(LivingEntity target, Entity source) {
        if (!(target.level() instanceof ServerLevel) || !target.isAlive() || target.isSpectator()) return false;
        source=effectSource(source);
        if (source == null || source == target) {
            return !(target instanceof ServerPlayer player) || Magnitude.settings.allowSelfChange || operator(player);
        }
        if (source.isSpectator() || source.level() != target.level()) return false;
        if (source instanceof ServerPlayer player) return canChange(player, target);
        return !(target instanceof Player) || EntityState.of(target).acceptResize;
    }
    public static Entity effectSource(Entity source) {
        for (int i = 0; i < 4 && source instanceof net.minecraft.world.entity.TraceableEntity traceable; i++) {
            Entity owner = traceable.getOwner();
            if (owner == null || owner == source) break;
            source = owner;
        }
        return source;
    }
    public static boolean set(Entity entity, double value, int ticks) {
        if (!(entity.level() instanceof ServerLevel) || !Double.isFinite(value)) return false;
        double bounded = Rules.bounded(value, Magnitude.settings.minimum, Magnitude.settings.maximum)/vanillaFactor(entity);
        ScaleData data = ScaleTypes.BASE.getScaleData(entity);
        data.setPersistence(true);
        data.setScaleTickDelay(Math.clamp(ticks, 0, 1200));
        if (ticks == 0) data.setScale((float)bounded); else data.setTargetScale((float)bounded);
        return true;
    }
    public static boolean settled(Entity entity) { var data=ScaleTypes.BASE.getScaleData(entity);return data.getBaseScale()==data.getTargetScale(); }
    public static boolean withinLimits(Entity entity) {
        double current=size(entity);
        return Double.isFinite(current) && current>=(float)Magnitude.settings.minimum && current<=(float)Magnitude.settings.maximum;
    }
    public static void reset(Entity entity) {
        if (!(entity.level() instanceof ServerLevel)) return;
        dev.magnitude.interaction.Interactions.detach(entity);
        for (var type : virtuoel.pehkui.api.ScaleRegistries.SCALE_TYPES.values()) type.getScaleData(entity).resetScale();
        set(entity, 1, 0);
        EntityState state = EntityState.of(entity);
        state.randomPeriod = 0;
        state.carrying = false;
        entity.ejectPassengers();
        entity.stopRiding();
    }
}
