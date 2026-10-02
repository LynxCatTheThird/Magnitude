package dev.magnitude.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Level.class)
public interface LevelEntitiesMixin {
    @Invoker("getEntities") LevelEntityGetter<Entity> magnitude$entities();
}
