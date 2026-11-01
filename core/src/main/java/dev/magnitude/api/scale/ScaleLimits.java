package dev.magnitude.api.scale;

/**
 * 实验性尺寸请求区间，两个端点均为包含端点。
 *
 * <p>区间由具体提供者和已验证的支持域指定，本类型不硬编码产品倍率，也不会钳制请求。
 * 通过本区间检查只表示数值合法，不证明对应尺寸的碰撞、渲染或网络能力已通过验收。
 */
public record ScaleLimits(double min, double max) {
    public ScaleLimits {
        if (!Double.isFinite(min) || min <= 0.0) {
            throw new IllegalArgumentException("min must be finite and positive: " + min);
        }
        if (!Double.isFinite(max) || max <= 0.0) {
            throw new IllegalArgumentException("max must be finite and positive: " + max);
        }
        if (min > max) {
            throw new IllegalArgumentException("min must not exceed max: " + min + " > " + max);
        }
    }

    public boolean contains(double value) {
        return Double.isFinite(value) && value >= min && value <= max;
    }

    /**
     * 返回未经修改的合法请求；非法请求抛出异常，不产生一个看似成功的钳制值。
     * 平台服务应将该异常转换为明确的请求拒绝回执。
     */
    public double requireValid(double value) {
        if (!contains(value)) {
            throw new IllegalArgumentException(
                    "scale must be finite and within [" + min + ", " + max + "]: " + value);
        }
        return value;
    }
}
