package dev.magnitude.api.relation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable authoritative state. Host exists exactly when state is HELD. */
public record RelationSnapshot(UUID target, RelationState state, long revision, Optional<UUID> host) {
    public RelationSnapshot {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(host, "host");
        if (revision < 0 || (state == RelationState.HELD) != host.isPresent()) {
            throw new IllegalArgumentException("Invalid revision or host/state combination");
        }
        if (host.filter(target::equals).isPresent()) {
            throw new IllegalArgumentException("An entity cannot hold itself");
        }
    }

    public static RelationSnapshot free(UUID target) {
        return new RelationSnapshot(target, RelationState.FREE, 0, Optional.empty());
    }
}
