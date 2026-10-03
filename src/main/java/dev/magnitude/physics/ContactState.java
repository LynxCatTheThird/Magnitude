package dev.magnitude.physics;

/** Bounded transient event history. Cleared on unload, transfer and respawn. */
public final class ContactState {
    public final FootSupportState feet=new FootSupportState();
    public final java.util.ArrayDeque<dev.magnitude.interaction.FootprintWork> footprints = new java.util.ArrayDeque<>(4);
    public final PhysicsDiagnostics diagnostics=new PhysicsDiagnostics();
    public long sequence, handled;
    public long sampleTick = Long.MIN_VALUE, takeoffTick = Long.MIN_VALUE;
    public long obstacleTick = Long.MIN_VALUE;
    public ContactEvent last;
    public int changedBlocks;
    public String reason = "no event";
    public final long[] counts = new long[ContactEvent.Type.values().length];
    public SupportSnapshot support;
    public double loadSize = -1, loadX = Double.NaN, loadZ = Double.NaN, peakY = Double.NaN;
    /** Self-created support loss cannot bootstrap another landing excavation. */
    public boolean selfTerrainFall;
    public double excavationY=Double.NaN;
    public net.minecraft.world.phys.Vec3 excavationRoot;
    public boolean walking;
    public boolean pressureActive;
    public boolean terrainActive;
}
