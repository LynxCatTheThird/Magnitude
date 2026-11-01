package dev.magnitude.api.math;

import java.util.Objects;

/**
 * 实验性刚性变换：局部偏移先绕 +Y 旋转，再加上世界原点。
 *
 * <p>坐标系为右手系：+X 向右、+Y 向上、-Z 向前。yaw 使用角度，正方向遵循
 * +Y 的右手旋转；0 度时前方为 -Z，+90 度时前方为 -X。
 * 平台适配器必须显式转换自身 yaw 约定，不能直接假定与此定义相同。
 *
 * <p>输入局部偏移已经是世界格单位，不会在此隐式乘入宿主或目标的尺寸倍率。
 * 需要缩放模型锚点时，由调用者先换算一次，再调用此变换。
 *
 * <p>本类型仅支持 yaw 刚性变换，不承诺完整骨骼旋转、非等比例缩放或剪切。
 * 转成绝对世界坐标可能丢失微小偏移；必须保留原局部坐标，不能依赖逆变换恢复
 * 已经因浮点舍入丢失的信息。
 */
public record YawTransform(FiniteVec3 origin, double yawDegrees) {
    public YawTransform {
        Objects.requireNonNull(origin, "origin");
        if (!Double.isFinite(yawDegrees)) {
            throw new IllegalArgumentException("yawDegrees must be finite: " + yawDegrees);
        }
        yawDegrees %= 360.0;
        if (yawDegrees >= 180.0) {
            yawDegrees -= 360.0;
        } else if (yawDegrees < -180.0) {
            yawDegrees += 360.0;
        }
        if (yawDegrees == 0.0) {
            yawDegrees = 0.0;
        }
    }

    /** 只改变方向，不添加原点。输出与输入保持相同单位。 */
    public FiniteVec3 rotate(FiniteVec3 localOffset) {
        Objects.requireNonNull(localOffset, "localOffset");
        double sin = sinYaw();
        double cos = cosYaw();
        return new FiniteVec3(
                Math.fma(cos, localOffset.x(), sin * localOffset.z()),
                localOffset.y(),
                Math.fma(-sin, localOffset.x(), cos * localOffset.z()));
    }

    /** 只执行逆旋转。输入必须已经是不含世界原点的相对向量。 */
    public FiniteVec3 inverseRotate(FiniteVec3 worldOffset) {
        Objects.requireNonNull(worldOffset, "worldOffset");
        double sin = sinYaw();
        double cos = cosYaw();
        return new FiniteVec3(
                Math.fma(cos, worldOffset.x(), -sin * worldOffset.z()),
                worldOffset.y(),
                Math.fma(sin, worldOffset.x(), cos * worldOffset.z()));
    }

    public FiniteVec3 localToWorld(FiniteVec3 localOffset) {
        return origin.add(rotate(localOffset));
    }

    public FiniteVec3 worldToLocal(FiniteVec3 worldPosition) {
        Objects.requireNonNull(worldPosition, "worldPosition");
        return inverseRotate(worldPosition.subtract(origin));
    }

    private double sinYaw() {
        if (yawDegrees == 0.0 || yawDegrees == -180.0) {
            return 0.0;
        }
        if (yawDegrees == 90.0) {
            return 1.0;
        }
        if (yawDegrees == -90.0) {
            return -1.0;
        }
        return StrictMath.sin(StrictMath.toRadians(yawDegrees));
    }

    private double cosYaw() {
        if (yawDegrees == 0.0) {
            return 1.0;
        }
        if (yawDegrees == -180.0) {
            return -1.0;
        }
        if (yawDegrees == 90.0 || yawDegrees == -90.0) {
            return 0.0;
        }
        return StrictMath.cos(StrictMath.toRadians(yawDegrees));
    }
}
