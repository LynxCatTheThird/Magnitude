package dev.magnitude.physics;

/** Bounded transient event history. Cleared on unload, transfer and respawn. */
public final class ContactState {
    public final java.util.ArrayDeque<dev.magnitude.interaction.FootprintWork> footprints = new java.util.ArrayDeque<>(4);
    public long sequence, handled;
    public long sampleTick = Long.MIN_VALUE, takeoffTick = Long.MIN_VALUE;
    public long obstacleTick = Long.MIN_VALUE;
    public ContactEvent last;
    public int changedBlocks;
    public String reason = "no event";
    public final long[] counts = new long[ContactEvent.Type.values().length];
    public SupportSnapshot support;
    public double loadSize = -1, loadX = Double.NaN, loadZ = Double.NaN, peakY = Double.NaN;
    public boolean walking;
    public boolean pressureActive;
    public boolean terrainActive;
}
