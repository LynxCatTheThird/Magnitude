package dev.magnitude.mixin;
import dev.magnitude.Magnitude;
import dev.magnitude.core.Dimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class FoodMixin {
    @Shadow public abstract ItemStack getUseItem();
    @Inject(method="completeUsingItem",at=@At("HEAD"))
    private void magnitude$food(CallbackInfo info) {
        LivingEntity entity=(LivingEntity)(Object)this;
        if (!(entity.level() instanceof ServerLevel) || Magnitude.settings==null) return;
        ItemStack stack=getUseItem();
        if (!stack.has(net.minecraft.core.component.DataComponents.FOOD)) return;
        Double factor=Magnitude.settings.foodFactors.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (factor!=null) Dimensions.set(entity,Dimensions.target(entity)*factor,20);
    }
}
