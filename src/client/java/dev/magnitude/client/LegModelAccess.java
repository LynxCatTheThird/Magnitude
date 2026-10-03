package dev.magnitude.client;

/** Per-model transient articulation, refreshed on each setupAnim call. */
public interface LegModelAccess {
    void magnitude$leg(boolean enabled,double hip,double knee);
}
