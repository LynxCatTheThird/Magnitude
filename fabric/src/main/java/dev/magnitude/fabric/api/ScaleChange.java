package dev.magnitude.fabric.api;

import dev.magnitude.api.scale.ScaleSnapshot;
import java.util.Objects;
import java.util.Optional;

/** Confirmed operation outcome; rejection does not mutate the requested entity. */
public record ScaleChange(Code code, String detail, Optional<ScaleSnapshot> snapshot) {
    public enum Code {
        APPLIED, UNCHANGED, DENIED, INVALID_SCALE, UNSUPPORTED_ENTITY,
        UNSUPPORTED_STATE, CONFLICT, NOT_LOADED, BLOCKED, CAPACITY, BUDGET, NOT_READY
    }
    public ScaleChange {
        Objects.requireNonNull(code); Objects.requireNonNull(detail); Objects.requireNonNull(snapshot);
    }
    public boolean succeeded() { return code == Code.APPLIED || code == Code.UNCHANGED; }
}
