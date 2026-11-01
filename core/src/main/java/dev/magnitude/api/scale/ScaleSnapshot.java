package dev.magnitude.api.scale;

import java.util.Objects;
import java.util.UUID;

/**
 * 实验性尺寸观测快照。所有字段属于同一次提供者观测，构造函数不修改实体。
 *
 * <p>观测尺寸不会再次乘 effectiveScale。它们允许因姿态及提供者规则变化，也不要求
 * width/height 是固定比例。该基础记录不声称已实现部位碰撞、FULL／EXTENDED 能力协议
 * 或所有几何字段的原子世界读取；平台必须负责捕获时机和支持域检查。
 *
 * @param entityId 实体身份
 * @param revision 服务端赋予的非负版本；不能由客户端作为权威版本提交
 * @param requestedScale 已接受的原始请求倍率，不必与最终有效倍率相等
 * @param effectiveScale 提供者报告的最终有效倍率
 * @param observedWidth 引擎观测宽度，已是世界格单位
 * @param observedHeight 引擎观测高度，已是世界格单位
 * @param observedEyeHeight 相对实体原点的观测眼高，已是世界格单位；允许为零或超过高度
 */
public record ScaleSnapshot(
        UUID entityId,
        long revision,
        double requestedScale,
        double effectiveScale,
        double observedWidth,
        double observedHeight,
        double observedEyeHeight) {

    public ScaleSnapshot {
        Objects.requireNonNull(entityId, "entityId");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be non-negative: " + revision);
        }
        requirePositiveFinite(requestedScale, "requestedScale");
        requirePositiveFinite(effectiveScale, "effectiveScale");
        requirePositiveFinite(observedWidth, "observedWidth");
        requirePositiveFinite(observedHeight, "observedHeight");
        if (!Double.isFinite(observedEyeHeight) || observedEyeHeight < 0.0) {
            throw new IllegalArgumentException(
                    "observedEyeHeight must be finite and non-negative: " + observedEyeHeight);
        }
        if (observedEyeHeight == 0.0) {
            observedEyeHeight = 0.0;
        }
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive: " + value);
        }
    }
}
