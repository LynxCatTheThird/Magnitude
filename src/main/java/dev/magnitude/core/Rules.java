package dev.magnitude.core;

/** Pure rules shared by commands, tools and server interactions. */
public final class Rules {
    private Rules() {}
    public static double bounded(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("A finite size is required");
        return Math.clamp(value, minimum, maximum);
    }
    public static boolean ratio(double larger, double smaller, double required) {
        return Double.isFinite(larger) && Double.isFinite(smaller) && smaller > 0 && larger / smaller >= required;
    }
    public static double transfer(double available, double capacity, double fraction, double minimum) {
        if (!Double.isFinite(available) || !Double.isFinite(capacity) || !Double.isFinite(fraction)) return 0;
        return Math.max(0, Math.min(Math.max(0, available - minimum) * Math.clamp(fraction, 0, 1), capacity));
    }
    public static boolean insideEllipsoid(double x, double y, double z, double horizontal, double vertical) {
        return horizontal > 0 && vertical > 0 && (x*x + z*z)/(horizontal*horizontal) + y*y/(vertical*vertical) <= 1;
    }
    public static boolean insideFootprint(double x, double z, double yaw, double halfWidth, double halfLength) {
        double side = x * Math.cos(yaw) + z * Math.sin(yaw);
        double forward = -x * Math.sin(yaw) + z * Math.cos(yaw);
        return halfWidth > 0 && halfLength > 0 && side*side/(halfWidth*halfWidth) + forward*forward/(halfLength*halfLength) <= 1;
    }
    public static final class Budget {
        private int remaining;
        public void reset(int amount) { remaining = Math.max(0, amount); }
        public boolean take() { if (remaining <= 0) return false; remaining--; return true; }
        public int remaining() { return remaining; }
    }
}
