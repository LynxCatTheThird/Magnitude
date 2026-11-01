package dev.magnitude.api.relation;

/**
 * Trusted world-adapter outcome, never a client claim. For normal release, submit
 * RELEASE(PENDING) first to revoke host control, then place the entity, then send
 * COMPLETE_RECOVERY only after placement actually succeeds. SAFE on RELEASE is
 * reserved for an already-completed placement with no remaining fallible effect;
 * a validated candidate location alone is not SAFE.
 */
public enum PlacementStatus { NOT_APPLICABLE, SAFE, PENDING }
