package dev.magnitude.physics;

import dev.magnitude.Magnitude;
import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Derives contact transitions once per server tick, independently of action-key flags. */
public final class ContactEvents {
    private ContactEvents() {}
    public static boolean supported(ServerPlayer player) {
        return player.onGround() && BodyCollision.active(player) && !player.getAbilities().flying && !player.isNoGravity();
    }
    public static void takeoff(ServerPlayer player) {
        if (!supported(player) || !player.isAlive() || player.isSpectator()) return;
        var state = EntityState.of(player);
        long tick = player.level().getGameTime();
        if (state.contacts.takeoffTick == tick || state.nextJumpImpact > tick) return;
        state.contacts.takeoffTick = tick;
        state.nextJumpImpact = tick + 5;
        state.jumpImpact = true; // Compatibility status only; landing never uses this flag.
        BodyPoses.update(player, 0, true);
        emit(player, ContactEvent.Type.TAKEOFF, Vec3.ZERO, 0, SupportSnapshot.capture(player));
    }
    public static void sample(ServerPlayer player) {
        var state = EntityState.of(player);
        var contact = state.contacts;
        long tick = player.level().getGameTime();
        if (contact.sampleTick == tick) return;
        contact.sampleTick = tick;
        Vec3 movement = state.initialized && state.previousPosition != null
            ? player.position().subtract(state.previousPosition) : Vec3.ZERO;
        double distance = movement.horizontalDistance();
        boolean teleport = !Double.isFinite(movement.lengthSqr()) || distance > 16 || Math.abs(movement.y) > 16;
        BodyPoses.update(player, teleport ? 0 : distance, false);
        boolean grounded = supported(player);
        if (state.initialized && Math.abs(Dimensions.snapshot(player).base() - state.previousSize) > 0.05)
            emit(player, ContactEvent.Type.SIZE_CHANGED, movement, 0, null);
        if (!grounded) {
            state.strideDistance = 0;
            contact.support = null;
            contact.loadSize = -1;
            contact.peakY = Double.isFinite(contact.peakY) ? Math.max(contact.peakY, player.getY()) : player.getY();
            emit(player, ContactEvent.Type.AIRBORNE, movement, 0, null);
        } else {
            double fall = Double.isFinite(contact.peakY) ? Math.max(0, contact.peakY - player.getY()) : 0;
            var support = SupportSnapshot.capture(player);
            if (state.initialized && !state.grounded && !teleport)
                emit(player, ContactEvent.Type.LANDING, movement, fall, support);
            if (!teleport && Math.abs(movement.y) <= 2 && distance > 0.001) {
                state.strideDistance += distance;
                double stride = Dimensions.snapshot(player).stride();
                if (state.strideDistance >= stride) {
                    state.strideDistance %= stride;
                    emit(player, ContactEvent.Type.WALKING_STRIDE, movement, 0, support);
                }
            } else if (teleport) state.strideDistance = 0;
            double size = Dimensions.snapshot(player).base();
            boolean pressure = Magnitude.settings.standingPressure && state.pressureEnabled;
            boolean terrain = Magnitude.settings.terrainDamage && state.terrainEnabled;
            boolean newLoad = contact.support == null || !support.equals(contact.support)
                || Math.abs(contact.loadSize - size) > 0.05
                || Math.hypot(player.getX() - contact.loadX, player.getZ() - contact.loadZ) > 0.5
                || pressure != contact.pressureActive || terrain != contact.terrainActive;
            if (support.complete() && newLoad) {
                emit(player, ContactEvent.Type.SUPPORT_CHANGED, movement, 0, support);
                // Remember post-effect support so our own write is not a new external load.
                contact.support = SupportSnapshot.capture(player);
                if (!contact.support.complete()) contact.support = support;
                contact.loadSize = size; contact.loadX = player.getX(); contact.loadZ = player.getZ();
                contact.pressureActive = pressure; contact.terrainActive = terrain;
            }
            contact.peakY = Double.NaN;
            state.jumpImpact = false;
        }
        state.previousPosition = player.position();
        state.initialized = true;
        state.grounded = grounded;
        state.downward = player.getDeltaMovement().y;
        state.previousSize = Dimensions.snapshot(player).base();
    }
    public static ContactEvent emit(ServerPlayer player, ContactEvent.Type type, Vec3 movement,
                                    double fall, SupportSnapshot support) {
        return emit(player,type,movement,fall,support,java.util.List.of());
    }
    public static ContactEvent emit(ServerPlayer player, ContactEvent.Type type, Vec3 movement,
                                    double fall, SupportSnapshot support, java.util.List<net.minecraft.core.BlockPos> obstacles) {
        var state = EntityState.of(player);
        Vec3 velocity = player.getDeltaMovement();
        if (type == ContactEvent.Type.LANDING) velocity = new Vec3(velocity.x, Math.min(movement.y, Math.min(velocity.y, state.downward) * Dimensions.snapshot(player).motionFactor()), velocity.z);
        var event = new ContactEvent(++state.contacts.sequence, type, player, player.level().getGameTime(),
            player.position(), velocity, movement, fall, Dimensions.snapshot(player), state.pose, support, java.util.List.copyOf(obstacles));
        ContactEventBus.publish(event);
        return event;
    }
}
