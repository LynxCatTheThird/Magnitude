package dev.magnitude.interaction;

import dev.magnitude.Magnitude;
import dev.magnitude.core.EntityState;
import dev.magnitude.physics.ContactEvent;

/** Executes accepted contact facts; event detection never repeats terrain or damage decisions. */
public final class ContactEffects {
    private ContactEffects() {}
    public static void apply(ContactEvent event) {
        var player = event.player();
        var state = EntityState.of(player);
        switch (event.type()) {
            case TAKEOFF -> state.contacts.changedBlocks = Impact.feet(player, 0, false);
            case WALKING_STRIDE -> {
                state.contacts.changedBlocks = Impact.feet(player, 0, false);
                if (Magnitude.settings.bodyDamage)
                    Interactions.damageSmall(player, player.getBoundingBox().inflate(0.1, 0.25, 0.1),
                        Interactions.scaledImpactDamage(player, Magnitude.settings.walkDamageFactor));
            }
            case SUPPORT_CHANGED -> {
                if (Magnitude.settings.standingPressure && state.pressureEnabled) state.contacts.changedBlocks = Impact.feet(player, 0, true);
                else state.contacts.reason = "pressure disabled";
            }
            case SIZE_CHANGED -> {
                if (event.scale().base() > state.previousSize && event.scale().base() >= 4)
                    state.contacts.changedBlocks = Impact.breakAround(player, event.position().add(0, Math.min(3, event.scale().height()/2), 0),
                        Math.min(6, event.scale().width()/2), Math.min(6, event.scale().height()/2));
            }
            case LANDING -> {
                // The contact transition, captured descent and actual fall establish an impact.
                // A remembered jump-key flag is neither required nor sufficient.
                if (event.scale().base() >= 4 && (event.velocity().y < -0.1 || event.fallHeight() > 0.05))
                    Interactions.shock(player, Math.min(Magnitude.settings.impactRadius,
                        event.scale().base() * Magnitude.settings.impactScaleFactor), true);
                else state.contacts.reason = "no falling impact";
            }
            case OBSTACLE -> state.contacts.changedBlocks = Impact.kick(player, event.movement());
            default -> { }
        }
    }
}
