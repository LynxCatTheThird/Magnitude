package dev.magnitude.fabric.api.carry;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.LivingEntity;

/** Experimental server-thread-only carry API. Player identity is an actual server player object. */
public interface CarryAccess {
    CarryResult request(ServerPlayer host, LivingEntity target);
    CarryResult accept(ServerPlayer target);
    CarryResult decline(ServerPlayer target);
    CarryResult release(ServerPlayer actor);
    CarryResult recover(ServerPlayer actor);
    /** Administrator recovery for a specific entity, including a pig with a restore marker. */
    CarryResult recover(CommandSourceStack source, LivingEntity target);
    List<CarryView> views();
}
