package dev.magnitude.api.scale;

/** Experimental relative width/height factors. The vanilla base is applied separately, exactly once. */
public record ShapeFactors(double width, double height) {
    public static final ShapeFactors IDENTITY = new ShapeFactors(1, 1);
    public ShapeFactors {
        if (!valid(width) || !valid(height)) {
            throw new IllegalArgumentException("Shape factors must be finite and within [0.25,4]");
        }
    }
    private static boolean valid(double value) {
        return Double.isFinite(value) && value >= .25 && value <= 4;
    }
}
