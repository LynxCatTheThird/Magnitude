package dev.magnitude.api.relation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Untrusted command data. The server must independently construct an authorization.
 * requestId is unique per actor and epoch. sequence starts at 1 and strictly
 * increases for that actor in the epoch (gaps are allowed). expectedRevision is
 * the target revision. Neither field is supplied by a trusted authorization.
 */
public record RelationRequest(UUID epoch, UUID requestId, long sequence, UUID actor, UUID target,
                              Optional<UUID> host, long expectedRevision,
                              RelationOperation operation, PlacementStatus placement) {
    public RelationRequest {
        Objects.requireNonNull(epoch, "epoch");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(placement, "placement");
        if (sequence < 1) throw new IllegalArgumentException("Sequence must be positive");
        if (expectedRevision < 0) throw new IllegalArgumentException("Negative expectedRevision");
        boolean valid = switch (operation) {
            case HOLD -> host.isPresent() && placement == PlacementStatus.NOT_APPLICABLE;
            case RELEASE -> host.isPresent() && placement != PlacementStatus.NOT_APPLICABLE;
            case COMPLETE_RECOVERY -> host.isEmpty() && placement == PlacementStatus.SAFE;
        };
        if (!valid) throw new IllegalArgumentException("Invalid host or placement for " + operation);
    }

    public static RelationRequest hold(UUID epoch, UUID id, long sequence, UUID actor, UUID host, UUID target, long revision) {
        return new RelationRequest(epoch, id, sequence, actor, target, Optional.of(host), revision,
                RelationOperation.HOLD, PlacementStatus.NOT_APPLICABLE);
    }

    public static RelationRequest release(UUID epoch, UUID id, long sequence, UUID actor, UUID host, UUID target,
                                          long revision, PlacementStatus placement) {
        return new RelationRequest(epoch, id, sequence, actor, target, Optional.of(host), revision,
                RelationOperation.RELEASE, placement);
    }

    public static RelationRequest completeRecovery(UUID epoch, UUID id, long sequence, UUID actor, UUID target, long revision) {
        return new RelationRequest(epoch, id, sequence, actor, target, Optional.empty(), revision,
                RelationOperation.COMPLETE_RECOVERY, PlacementStatus.SAFE);
    }
}
