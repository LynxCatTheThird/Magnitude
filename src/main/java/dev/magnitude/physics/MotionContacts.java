package dev.magnitude.physics;

import dev.magnitude.core.EntityState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Orchestrates solve -> contact effect -> fresh solve; the solver never mutates terrain. */
public final class MotionContacts {
    private MotionContacts() {}
    public static Vec3 move(Player player, Vec3 wanted) {
        var result = BodyCollision.solve(player, wanted);
        var contacts = EntityState.of(player).contacts;
        if (result.obstacle() && player instanceof ServerPlayer server
            && contacts.obstacleTick != player.level().getGameTime()) {
            contacts.obstacleTick = player.level().getGameTime();
            var event = ContactEvents.emit(server, ContactEvent.Type.OBSTACLE, wanted, 0, null);
            if (EntityState.of(player).contacts.changedBlocks > 0) {
                // A fresh query is charged to the same per-player and global budgets.
                result = BodyCollision.solve(player, wanted);
                EntityState.of(player).contacts.last = event;
            }
        }
        return result.movement();
    }
}
