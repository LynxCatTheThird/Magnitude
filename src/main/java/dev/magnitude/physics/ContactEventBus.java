package dev.magnitude.physics;

import dev.magnitude.core.EntityState;
import dev.magnitude.interaction.ContactEffects;

/** Synchronous bounded dispatch. Claim the sequence before effects or callbacks can re-enter. */
public final class ContactEventBus {
    private ContactEventBus() {}
    public static boolean publish(ContactEvent event) {
        var state = EntityState.of(event.player()).contacts;
        if (event.sequence() <= state.handled) return false;
        state.handled = event.sequence();
        state.last = event;
        state.counts[event.type().ordinal()]++;
        state.reason = "observed";
        state.changedBlocks = 0;
        ContactEffects.apply(event);
        return true;
    }
}
