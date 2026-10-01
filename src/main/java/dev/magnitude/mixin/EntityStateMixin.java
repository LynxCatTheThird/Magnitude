package dev.magnitude.mixin;

import dev.magnitude.core.EntityState;
import dev.magnitude.core.StateAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityStateMixin implements StateAccess {
    @Unique private final EntityState magnitude$state = new EntityState();
    @Override public EntityState magnitudeState() { return magnitude$state; }
    @Inject(method = "load", at = @At("RETURN"))
    private void magnitude$load(ValueInput input, CallbackInfo info) { magnitude$state.load(input); }
    @Inject(method = "saveWithoutId", at = @At("HEAD"))
    private void magnitude$save(ValueOutput output, CallbackInfo info) { magnitude$state.save(output); }
}
