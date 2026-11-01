package dev.magnitude.api.relation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * snapshot is the state at the original decision, not necessarily the live state
 * when an old response is replayed. Only changed() permits a new side effect.
 */
public record RelationResult(UUID requestId, Status status, RelationReason reason,
                             Optional<RelationSnapshot> snapshot, boolean replayed) {
    public enum Status { APPLIED, UNCHANGED, REJECTED }

    public RelationResult {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(snapshot, "snapshot");
    }

    public boolean changed() { return status == Status.APPLIED && !replayed; }

    public RelationResult asReplay() {
        return new RelationResult(requestId, status, reason, snapshot, true);
    }
}
