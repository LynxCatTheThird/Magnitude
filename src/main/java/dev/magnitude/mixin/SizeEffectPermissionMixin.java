package dev.magnitude.mixin;

import dev.magnitude.content.SizeEffect;
import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class SizeEffectPermissionMixin {
    @Inject(method="addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",at=@At("HEAD"),cancellable=true)
    private void magnitude$allowEffect(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> result) {
        LivingEntity target=(LivingEntity)(Object)this;
        if (!target.level().isClientSide() && effect.getEffect().value() instanceof SizeEffect && !Dimensions.canApplyEffect(target,source)) result.setReturnValue(false);
    }
    @Inject(method="forceAddEffect",at=@At("HEAD"),cancellable=true)
    private void magnitude$allowForcedEffect(MobEffectInstance effect, Entity source, CallbackInfo result) {
        LivingEntity target=(LivingEntity)(Object)this;
        if (!target.level().isClientSide() && effect.getEffect().value() instanceof SizeEffect && !Dimensions.canApplyEffect(target,source)) result.cancel();
    }
    @Inject(method="onEffectAdded",at=@At("HEAD"))
    private void magnitude$effectSource(MobEffectInstance effect, Entity source, CallbackInfo result) {
        remember(effect,source,true);
    }
    @Inject(method="onEffectUpdated",at=@At("HEAD"))
    private void magnitude$updatedEffectSource(MobEffectInstance effect, boolean reapply, Entity source, CallbackInfo result) {
        remember(effect,source,false);
    }
    private void remember(MobEffectInstance effect, Entity source, boolean newlyAdded) {
        LivingEntity target=(LivingEntity)(Object)this;
        if (target.level().isClientSide() || !(effect.getEffect().value() instanceof SizeEffect sizeEffect)) return;
        boolean external=source!=null && source!=target;
        if (source instanceof net.minecraft.world.entity.TraceableEntity traceable && traceable.getOwner()==target) external=false;
        EntityState state=EntityState.of(target);
        // A vanilla effect may hide and later restore a weaker instance. Mixed chains
        // keep the external restriction until the entire effect has been removed.
        if (sizeEffect.ascending()) state.ascentExternal=external || !newlyAdded && state.ascentExternal;
        else state.descentExternal=external || !newlyAdded && state.descentExternal;
    }
}
