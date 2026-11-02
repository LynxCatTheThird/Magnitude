package dev.magnitude.fabric.mixin;

import dev.magnitude.fabric.internal.scale.ShapeState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import dev.magnitude.api.scale.ShapeFactors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Refreshes geometry when a confirmed attachment loads or synchronizes, without per-tick scans. */
@Mixin(Entity.class)
public abstract class ShapeAttachmentMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void magnitude$watchShape(CallbackInfo info) { ShapeState.watch((Entity) (Object) this); }

    @Inject(method = "load", at = @At("RETURN"))
    private void magnitude$restoreShapeGeometry(CallbackInfo info) {
        Entity self = (Entity) (Object) this;
        // Fabric deserializes attachments directly; onAttachedSet only handles explicit changes/sync.
        if (self instanceof LivingEntity living && !ShapeState.confirmed(self).equals(ShapeFactors.IDENTITY)) {
            ShapeState.refreshAtRoot(living, living.position());
        }
    }
}
