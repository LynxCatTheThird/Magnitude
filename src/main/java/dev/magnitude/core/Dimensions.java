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
    public static double target(Entity entity) { return ScaleTypes.BASE.getScaleData(entity).getTargetScale(); }
    public static boolean operator(ServerPlayer player) { return player.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER); }
    public static boolean canChange(ServerPlayer actor, LivingEntity target) {
        if (!target.isAlive() || target.isSpectator() || actor.isSpectator() || actor.level() != target.level()) return false;
        if (actor == target) return Magnitude.settings.allowSelfChange || operator(actor);
        double reach = Math.min(64, Math.max(32, size(actor) * 3));
        if (target.getBoundingBox().distanceToSqr(actor.getEyePosition()) > reach * reach || !actor.hasLineOfSight(target)) return false;
        if (target instanceof Player) return EntityState.of(target).acceptResize;
        return actor.getAbilities().mayBuild && !actor.level().getServer().isUnderSpawnProtection((ServerLevel)actor.level(), target.blockPosition(), actor);
    }
    public static boolean change(ServerPlayer actor, LivingEntity entity, double value, int ticks) {
        if (!canChange(actor, entity)) return false;
        return set(entity, value, ticks);
    }
    public static boolean set(Entity entity, double value, int ticks) {
        if (!(entity.level() instanceof ServerLevel) || !Double.isFinite(value)) return false;
        double bounded = Rules.bounded(value, Magnitude.settings.minimum, Magnitude.settings.maximum);
        ScaleData data = ScaleTypes.BASE.getScaleData(entity);
        data.setPersistence(true);
        data.setScaleTickDelay(Math.clamp(ticks, 0, 1200));
        if (ticks == 0) data.setScale((float)bounded); else data.setTargetScale((float)bounded);
        return true;
    }
    public static boolean settled(Entity entity) { return Math.abs(size(entity) - target(entity)) < 0.000001; }
    public static void reset(Entity entity) {
        if (!(entity.level() instanceof ServerLevel)) return;
        for (var type : virtuoel.pehkui.api.ScaleRegistries.SCALE_TYPES.values()) type.getScaleData(entity).resetScale();
        set(entity, 1, 0);
        EntityState state = EntityState.of(entity);
        state.randomPeriod = 0;
        state.carrying = false;
        entity.ejectPassengers();
        entity.stopRiding();
    }
}
