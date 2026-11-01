package dev.magnitude.api.relation;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Trusted server-side grant, constructed after resolving connection identity,
 * consent, permissions, and placement (if applicable). Never deserialize this
 * object from a client message. Grants bind the exact target, optional host and
 * allowed placement outcome; do not populate allowedPlacements from unverified
 * request data. SAFE is a trusted world decision, not a client assertion.
 * Trusted in-process extensions can construct grants; this is not a sandbox.
 */
public record RelationAuthorization(UUID authenticatedActor, UUID target, Optional<UUID> host,
                                    Set<RelationOperation> allowedOperations, Set<PlacementStatus> allowedPlacements) {
    /** Does not authorize safe placement. SAFE must be independently verified and explicitly granted. */
    public RelationAuthorization(UUID authenticatedActor, UUID target, Optional<UUID> host,
                                 Set<RelationOperation> allowedOperations) {
        this(authenticatedActor, target, host, allowedOperations,
                Set.of(PlacementStatus.NOT_APPLICABLE, PlacementStatus.PENDING));
    }
    public RelationAuthorization {
        Objects.requireNonNull(authenticatedActor, "authenticatedActor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(host, "host");
        allowedOperations = Set.copyOf(allowedOperations);
        allowedPlacements = Set.copyOf(allowedPlacements);
    }

    public boolean permits(RelationRequest request) {
        return authenticatedActor.equals(request.actor()) && target.equals(request.target())
                && host.equals(request.host()) && allowedOperations.contains(request.operation())
                && allowedPlacements.contains(request.placement());
    }
}
