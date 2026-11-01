package dev.magnitude.scale;

import dev.magnitude.api.math.FiniteVec3;
import dev.magnitude.api.math.YawTransform;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class YawTransformTest {
    @Test
    void cardinalDirectionsFollowTheDocumentedRightHandedFrame() {
        FiniteVec3 forward = new FiniteVec3(0, 0, -1);
        assertEquals(forward, new YawTransform(FiniteVec3.ZERO, 0).rotate(forward));
        assertEquals(new FiniteVec3(-1, 0, 0), new YawTransform(FiniteVec3.ZERO, 90).rotate(forward));
        assertEquals(new FiniteVec3(1, 0, 0), new YawTransform(FiniteVec3.ZERO, -90).rotate(forward));
        assertEquals(new FiniteVec3(0, 0, 1), new YawTransform(FiniteVec3.ZERO, 180).rotate(forward));
        assertEquals(new FiniteVec3(0, 3, 0), new YawTransform(FiniteVec3.ZERO, 73).rotate(new FiniteVec3(0, 3, 0)));
    }

    @Test
    void offsetIsRotatedBeforeWorldTranslationWithoutHiddenScale() {
        YawTransform frame = new YawTransform(new FiniteVec3(10, 64, 20), 90);
        FiniteVec3 anchorOffset = new FiniteVec3(2, 3, -4);
        assertEquals(new FiniteVec3(6, 67, 18), frame.localToWorld(anchorOffset));
        assertEquals(anchorOffset, frame.worldToLocal(new FiniteVec3(6, 67, 18)));
    }

    @Test
    void fullTurnsAndNegativeTurnsHaveTheSameOrientation() {
        YawTransform frame = new YawTransform(FiniteVec3.ZERO, -270);
        assertEquals(90.0, frame.yawDegrees());
        assertEquals(frame, new YawTransform(FiniteVec3.ZERO, 450));
        assertEquals(new YawTransform(FiniteVec3.ZERO, -180), new YawTransform(FiniteVec3.ZERO, 540));
        assertEquals(new YawTransform(FiniteVec3.ZERO, 0), new YawTransform(FiniteVec3.ZERO, -360));
    }

    @Test
    void repeatedTransformsPreserveOffsetsWithinTheDeclaredTestPrecision() {
        for (long seed : new long[]{1, 19, 20261101}) {
            Random random = new Random(seed);
            for (int i = 0; i < 1000; i++) {
                FiniteVec3 origin = randomVector(random, 10_000);
                FiniteVec3 local = randomVector(random, 16);
                YawTransform frame = new YawTransform(origin, random.nextDouble() * 720 - 360);
                assertVectorClose(local, frame.worldToLocal(frame.localToWorld(local)), 1.0e-10);
                assertVectorClose(local, frame.inverseRotate(frame.rotate(local)), 1.0e-13);
            }
        }
    }

    @Test
    void relativeRotationPreservesTinyOffsetsEvenWhenAbsoluteCoordinatesCannot() {
        double worldX = 30_000_000;
        double tinyOffset = Math.ulp(worldX) / 4;
        YawTransform frame = new YawTransform(new FiniteVec3(worldX, 0, 0), 0);
        FiniteVec3 local = new FiniteVec3(tinyOffset, 0, 0);
        assertEquals(local, frame.inverseRotate(frame.rotate(local)));
        assertEquals(frame.origin(), frame.localToWorld(local));
        assertEquals(FiniteVec3.ZERO, frame.worldToLocal(frame.localToWorld(local)));
        assertEquals(tinyOffset, local.x());
    }

    @Test
    void rejectsInvalidAnglesAndOverflowingWorldResults() {
        for (double angle : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new YawTransform(FiniteVec3.ZERO, angle));
        }
        YawTransform distant = new YawTransform(new FiniteVec3(Double.MAX_VALUE, 0, 0), 0);
        assertThrows(IllegalArgumentException.class,
                () -> distant.localToWorld(new FiniteVec3(Double.MAX_VALUE, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> distant.worldToLocal(new FiniteVec3(-Double.MAX_VALUE, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new YawTransform(FiniteVec3.ZERO, 45).rotate(new FiniteVec3(Double.MAX_VALUE, 0, Double.MAX_VALUE)));
    }

    private static FiniteVec3 randomVector(Random random, double magnitude) {
        return new FiniteVec3((random.nextDouble() * 2 - 1) * magnitude,
                (random.nextDouble() * 2 - 1) * magnitude,
                (random.nextDouble() * 2 - 1) * magnitude);
    }

    private static void assertVectorClose(FiniteVec3 expected, FiniteVec3 actual, double epsilon) {
        assertEquals(expected.x(), actual.x(), epsilon);
        assertEquals(expected.y(), actual.y(), epsilon);
        assertEquals(expected.z(), actual.z(), epsilon);
    }
}
