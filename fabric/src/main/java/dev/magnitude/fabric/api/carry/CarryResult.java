package dev.magnitude.fabric.api.carry;

import java.util.Objects;

/** Result of a server-owned carry operation; a request for consent is not an attachment. */
public record CarryResult(Code code, String detail, boolean changed) {
    public enum Code { HELD, REQUESTED, RELEASED, RECOVERY, DECLINED, NO_REQUEST, NO_RELATION,
        DENIED, INVALID_TARGET, OUT_OF_RANGE, SIZE_MISMATCH, BUSY, BLOCKED, BUDGET, IO_FAILURE, NOT_READY }
    public CarryResult { Objects.requireNonNull(code); Objects.requireNonNull(detail); }
    public boolean succeeded() {
        return code == Code.HELD || code == Code.REQUESTED || code == Code.RELEASED
                || code == Code.RECOVERY || code == Code.DECLINED;
    }
}
