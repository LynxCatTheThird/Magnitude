package dev.magnitude.content;

import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import dev.magnitude.Magnitude;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class SizeEffect extends MobEffect {
    private final int direction;
    private final boolean instant;
    public SizeEffect(int direction, boolean instant, int color) {
        super(MobEffectCategory.NEUTRAL, color);
        this.direction = direction;
        this.instant = instant;
    }
    @Override public boolean isInstantaneous() { return instant; }
    public boolean ascending() { return direction > 0; }
    @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return instant || duration % 10 == 0; }
    @Override public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            var holder=instant ? ascending()?Content.ENLARGE:Content.REDUCE : ascending()?Content.ASCENT:Content.DESCENT;
            if (!player.hasEffect(holder)) return false;
            boolean external=EntityState.of(player).externalEffect(instant,ascending());
            if (external ? !EntityState.of(player).acceptResize : !Magnitude.settings.allowSelfChange && !Dimensions.operator(player)) return false;
        }
        apply(entity, amplifier, 1);
        return true;
    }
    @Override public void applyInstantaneousEffect(ServerLevel level, Entity source, Entity owner, LivingEntity entity, int amplifier, double proximity) {
        if (!Dimensions.canApplyEffect(entity, owner != null ? owner : source)) return;
        apply(entity, amplifier, proximity);
    }
    private void apply(LivingEntity entity, int amplifier, double proximity) {
        int potency = Math.clamp(amplifier, 0, 4) + 1;
        double factor = instant ? Math.pow(2, direction * potency * Math.clamp(proximity, 0, 1)) : Math.exp(direction * 0.005 * potency);
        Dimensions.set(entity, Dimensions.target(entity) * factor, instant ? 20 : 10);
    }
}
