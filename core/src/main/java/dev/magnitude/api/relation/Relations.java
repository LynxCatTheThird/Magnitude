package dev.magnitude.api.relation;

import dev.magnitude.core.relation.RelationLedger;
import java.util.UUID;

/** Public factory for the initial bounded, in-memory server relation service. */
public final class Relations {
    private Relations() {}

    /** The service must be used on the calling thread, which becomes its owner. */
    public static RelationService create(UUID epoch, RelationLimits limits) {
        return new RelationLedger(epoch, limits);
    }
}
