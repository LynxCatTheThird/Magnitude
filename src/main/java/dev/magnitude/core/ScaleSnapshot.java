package dev.magnitude.core;

/** Effective runtime coefficients and world-unit geometry. Never multiply these by BASE again. */
public record ScaleSnapshot(long revision, double base, double width, double height, double eyeHeight,
                            double modelWidth, double modelHeight, double motionFactor,
                            double jumpFactor, double attackFactor, double reachFactor,
                            double proxyLimit, double footprintScale, double jumpVelocityLimit) {
    public double stride() { return Math.max(0.4, modelHeight * 0.72 * Math.sin(0.65)); }
    public double bootHalfWidth() { return modelWidth * 0.09; }
    public double bootHalfLength() { return modelWidth * 0.09; }
}
