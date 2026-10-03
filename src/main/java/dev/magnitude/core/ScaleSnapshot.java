package dev.magnitude.core;

/** Effective runtime coefficients and world-unit geometry. Never multiply these by BASE again. */
public record ScaleSnapshot(long revision, double base, double width, double height, double eyeHeight,
                            double modelWidth, double modelHeight, double motionFactor,
                            double jumpFactor, double attackFactor, double reachFactor,
                            double proxyLimit, double footprintScale, double jumpVelocityLimit) {
    public double stride() { return Math.max(0.4, Math.min(8, footprintScale) * 0.4); }
    public double bootHalfWidth() { return footprintScale * 0.09 + 0.35; }
    public double bootHalfLength() { return footprintScale * 0.22 + 0.35; }
}
