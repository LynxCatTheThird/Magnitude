package dev.magnitude.scale;

import dev.magnitude.api.scale.ScaleSnapshot;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ScaleSnapshotTest {
    private static final UUID ENTITY = UUID.fromString("12345678-1234-5678-9abc-123456789abc");

    @Test
    void keepsRequestedAndEffectiveScaleDistinctAndDoesNotScaleObservedGeometryAgain() {
        ScaleSnapshot snapshot = new ScaleSnapshot(ENTITY, 7, 2, 3, 1.8, 5.4, 4.86);
        assertEquals(ENTITY, snapshot.entityId());
        assertEquals(7, snapshot.revision());
        assertEquals(2, snapshot.requestedScale());
        assertEquals(3, snapshot.effectiveScale());
        assertEquals(1.8, snapshot.observedWidth());
        assertEquals(5.4, snapshot.observedHeight());
        assertEquals(4.86, snapshot.observedEyeHeight());
    }

    @Test
    void permitsPoseSpecificAndIndependentEyeHeight() {
        ScaleSnapshot sleeping = new ScaleSnapshot(ENTITY, 0, 1, 1, 0.2, 0.2, 0);
        assertEquals(0, sleeping.observedEyeHeight());
        ScaleSnapshot extendedEye = new ScaleSnapshot(ENTITY, Long.MAX_VALUE, 1, 1, 0.4, 0.6, 2);
        assertEquals(2, extendedEye.observedEyeHeight());
        assertEquals(0.6, extendedEye.observedHeight());
    }

    @Test
    void invalidNumericObservationsCannotEnterASnapshot() {
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new ScaleSnapshot(ENTITY, 0, invalid, 1, 1, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> new ScaleSnapshot(ENTITY, 0, 1, invalid, 1, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> new ScaleSnapshot(ENTITY, 0, 1, 1, invalid, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> new ScaleSnapshot(ENTITY, 0, 1, 1, 1, invalid, 1));
        }
        for (double invalidEye : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new ScaleSnapshot(ENTITY, 0, 1, 1, 1, 1, invalidEye));
        }
        assertThrows(IllegalArgumentException.class, () -> new ScaleSnapshot(ENTITY, -1, 1, 1, 1, 1, 1));
        assertThrows(NullPointerException.class, () -> new ScaleSnapshot(null, 0, 1, 1, 1, 1, 1));
    }
}
