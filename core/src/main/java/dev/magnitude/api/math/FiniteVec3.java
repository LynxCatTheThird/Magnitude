package dev.magnitude.api.math;

import java.util.Objects;

/**
 * 实验性三维向量。分量及运算结果必须有限；溢出不会静默传给世界或网络。
 *
 * <p>坐标单位由调用契约决定。用于 {@link YawTransform} 时单位为世界格。
 * 本类型只保证有限性，不保证某个世界位置的精度足以表示任意微小偏移。
 */
public record FiniteVec3(double x, double y, double z) {
    public static final FiniteVec3 ZERO = new FiniteVec3(0.0, 0.0, 0.0);

    public FiniteVec3 {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        // 消除符号零造成的 equals/hashCode 差异。
        x = canonicalZero(x);
        y = canonicalZero(y);
        z = canonicalZero(z);
    }

    public FiniteVec3 add(FiniteVec3 other) {
        Objects.requireNonNull(other, "other");
        return new FiniteVec3(x + other.x, y + other.y, z + other.z);
    }

    public FiniteVec3 subtract(FiniteVec3 other) {
        Objects.requireNonNull(other, "other");
        return new FiniteVec3(x - other.x, y - other.y, z - other.z);
    }

    /** 倍率可以为零或负数，但必须有限；这不是尺寸属性的合法性校验。 */
    public FiniteVec3 multiply(double factor) {
        requireFinite(factor, "factor");
        return new FiniteVec3(x * factor, y * factor, z * factor);
    }

    private static double canonicalZero(double value) {
        return value == 0.0 ? 0.0 : value;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }
}
