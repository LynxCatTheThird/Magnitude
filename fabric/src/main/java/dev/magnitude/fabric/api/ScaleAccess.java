package dev.magnitude.fabric.api;

import dev.magnitude.api.scale.ScaleLimits;
import dev.magnitude.api.scale.ShapeFactors;
import dev.magnitude.api.scale.ScaleSnapshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.LivingEntity;

/** Experimental unified base and shape service. Calls must run on the server thread. */
public interface ScaleAccess {
    ScaleLimits limits();
    ShapeFactors shape(LivingEntity entity);
    ScaleChange setShape(CommandSourceStack source, LivingEntity target, double width, double height, long expectedRevision);
    /** Read-only observation. Revision tracks this service's writes, not arbitrary external mods. */
    ScaleSnapshot observe(LivingEntity entity);
    /** Same authorization and validation as the command; never accepts a client-supplied identity. */
    ScaleChange setScale(CommandSourceStack source, LivingEntity target, double scale, long expectedRevision);
}
