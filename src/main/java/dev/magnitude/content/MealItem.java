package dev.magnitude.content;

import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;

public final class MealItem extends Item {
    private final double factor;
    public MealItem(Properties properties, double factor) { super(properties); this.factor = factor; }
    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level instanceof ServerLevel) {
            if (factor == 0) { EntityState.of(entity).randomPeriod = 0; Dimensions.set(entity, 1, 20); }
            else if (Dimensions.canApplyEffect(entity,entity)) Dimensions.set(entity, Dimensions.target(entity) * factor, 20);
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
