package dev.magnitude.physics;

import dev.magnitude.core.EntityState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Orchestrates confirmed leg contacts -> effects -> fresh movement solve; the solver never mutates terrain. */
public final class MotionContacts {
    private MotionContacts() {}
    public static Vec3 move(Player player, Vec3 wanted) {
        var contacts = EntityState.of(player).contacts;
        if ((player.onGround() || wanted.y<0) && wanted.lengthSqr()>1e-8
            && player instanceof ServerPlayer server
            && contacts.obstacleTick != player.level().getGameTime()) {
            // A loaded leg sweep confirms candidates before the full solver consumes its budget.
            // Writes do not grant movement: the solver below queries the resulting world afresh.
            var hit = ObstacleContacts.capture(server,wanted);
            if (!hit.blocks().isEmpty()) {
                contacts.obstacleTick = player.level().getGameTime();
                ContactEvents.emit(server, ContactEvent.Type.OBSTACLE, wanted, 0, null, hit.blocks());
            }
        }
        return BodyCollision.solve(player,wanted).movement();
    }
}
