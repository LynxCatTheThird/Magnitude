package dev.magnitude.api.relation;

import java.util.Map;
import java.util.UUID;

/** Experimental, single-owner-thread relation contract; no Minecraft or disk types. */
public interface RelationService {
    UUID epoch();
    RelationResult submit(RelationRequest request, RelationAuthorization authorization);
    RelationSnapshot snapshot(UUID target);
    Map<UUID, RelationSnapshot> snapshotAll();
    int heldCount();
    int occupiedSlots(UUID host);
}
