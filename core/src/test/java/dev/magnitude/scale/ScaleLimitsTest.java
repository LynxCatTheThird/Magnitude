package dev.magnitude.scale;

import dev.magnitude.api.scale.ScaleLimits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScaleLimitsTest {
    @Test
    void endpointsAreInclusiveAndAdjacentOutsideValuesAreRejected() {
        ScaleLimits limits = new ScaleLimits(0.25, 4);
        assertEquals(0.25, limits.requireValid(0.25));
        assertEquals(4, limits.requireValid(4));
        assertFalse(limits.contains(Math.nextDown(0.25)));
        assertFalse(limits.contains(Math.nextUp(4.0)));
        assertThrows(IllegalArgumentException.class, () -> limits.requireValid(Math.nextDown(0.25)));
        assertThrows(IllegalArgumentException.class, () -> limits.requireValid(Math.nextUp(4.0)));
    }

    @Test
    void rejectsNonFiniteAndNonPositiveBounds() {
        for (double bad : new double[]{0, -0.0, -1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new ScaleLimits(bad, 4));
            assertThrows(IllegalArgumentException.class, () -> new ScaleLimits(1, bad));
        }
        assertThrows(IllegalArgumentException.class, () -> new ScaleLimits(2, 1));
    }

    @Test
    void numericBoundsAreProviderSuppliedRatherThanAProductScaleLimit() {
        ScaleLimits fixed = new ScaleLimits(1, 1);
        assertTrue(fixed.contains(1));
        assertFalse(fixed.contains(Math.nextUp(1.0)));
        ScaleLimits wide = new ScaleLimits(Double.MIN_VALUE, Double.MAX_VALUE);
        assertTrue(wide.contains(Double.MIN_VALUE));
        assertTrue(wide.contains(Math.scalb(1.0, -96)));
        assertTrue(wide.contains(Math.scalb(1.0, 32)));
        assertTrue(wide.contains(Double.MAX_VALUE));
        for (double bad : new double[]{0, -1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertFalse(wide.contains(bad));
            assertThrows(IllegalArgumentException.class, () -> wide.requireValid(bad));
        }
    }

    @Test
    void validRequestsAreReturnedWithoutClampingOrRounding() {
        ScaleLimits limits = new ScaleLimits(0.1, 100);
        double request = 49.875;
        assertEquals(Double.doubleToLongBits(request), Double.doubleToLongBits(limits.requireValid(request)));
    }
}
