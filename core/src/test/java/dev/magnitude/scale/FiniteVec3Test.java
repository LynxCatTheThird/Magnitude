package dev.magnitude.scale;

import dev.magnitude.api.math.FiniteVec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FiniteVec3Test {
    @Test
    void rejectsEveryNonFiniteComponent() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new FiniteVec3(invalid, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new FiniteVec3(0, invalid, 0));
            assertThrows(IllegalArgumentException.class, () -> new FiniteVec3(0, 0, invalid));
        }
    }

    @Test
    void arithmeticRejectsOverflowAndNonFiniteMultipliers() {
        FiniteVec3 largest = new FiniteVec3(Double.MAX_VALUE, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> largest.add(largest));
        assertThrows(IllegalArgumentException.class, () -> largest.subtract(largest.multiply(-1)));
        assertThrows(IllegalArgumentException.class, () -> largest.multiply(2));
        assertThrows(IllegalArgumentException.class, () -> FiniteVec3.ZERO.multiply(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> FiniteVec3.ZERO.multiply(Double.POSITIVE_INFINITY));
    }

    @Test
    void directionsAndOffsetsCanBeNegativeOrZero() {
        FiniteVec3 offset = new FiniteVec3(2, -3, 4);
        assertEquals(new FiniteVec3(-1, 1.5, -2), offset.multiply(-0.5));
        assertEquals(FiniteVec3.ZERO, offset.add(offset.multiply(-1)));
        assertEquals(FiniteVec3.ZERO, offset.multiply(0));
        assertEquals(new FiniteVec3(1, -1, 0), offset.subtract(new FiniteVec3(1, -2, 4)));
    }

    @Test
    void signedZeroHasOneValueAndHash() {
        FiniteVec3 signedZero = new FiniteVec3(-0.0, 0.0, -0.0);
        assertEquals(FiniteVec3.ZERO, signedZero);
        assertEquals(FiniteVec3.ZERO.hashCode(), signedZero.hashCode());
    }
}
