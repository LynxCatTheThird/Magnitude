# 实验性 API

本文对应 `0.0.2-dev.2`。公开 Java 接口仍为实验性，消费方应固定版本；当前没有跨版本二进制兼容承诺。纯 Java 核心使用 `dev.magnitude.api.*`，Minecraft／Fabric 适配使用 `dev.magnitude.fabric.api.*`。不要依赖 `core`、`internal` 或 `mixin` 实现包。

## 服务与线程

在服务器启动完成后，通过 `MagnitudeScales.forServer(server)` 和 `MagnitudeCarry.forServer(server)` 取得服务。所有操作和观测都在该服务器线程调用；异步工作需用 `server.execute(...)` 切回，并重新检查玩家仍在线、实体仍属于该服务器。服务器未就绪时取得服务会抛出 `IllegalStateException`，关闭后丢弃服务引用。

调用方属于可信服务端代码。玩家动作的主体必须来自当前连接或服务端命令上下文，不应把客户端提交的 UUID 直接解析成可代行操作的主体。此 API 不隔离同一 JVM 中其他模组的任意代码。

## 尺寸

`ScaleAccess.observe(entity)` 只读返回当前快照，`setScale(source, target, scale, expectedRevision)` 返回 `ScaleChange`。提交时传入刚观测的 revision；过期、权限不足、空间不可用或有外部属性冲突时应读取返回码并重新观测，不能盲目循环重试。

```java
var scales = MagnitudeScales.forServer(source.getServer());
var before = scales.observe(target);
var result = scales.setScale(source, target, 2.0, before.revision());
if (result.succeeded()) {
    // 使用确认快照；不要再给 Minecraft 几何乘一次倍率。
    var confirmed = result.snapshot().orElseThrow();
}
```

`source` 的管理员权限由服务重新检查。当前只支持玩家和猪，统一倍率输入域为 `0.25～4`；使用原版尺寸属性，不提供独立宽高或完整部位物理。revision 追踪本服务接受的写入，不是任意模组修改的全局版本。外部 modifier、外部 base 改写、骑乘或携带／恢复状态会使写入被拒绝。

本适配只写入原版 SCALE 属性，读取引擎实际尺寸与眼高；移动速度、跨阶高度和交互距离不由本服务按倍率调整。调用方不能把尺寸快照理解为所有运动／交互属性已经同比例变化。

## 携带

`CarryAccess` 提供以下操作，所有位置、资格和碰撞判定由服务执行：

| 操作 | 行为 |
| --- | --- |
| `request(host, target)` | 玩家目标创建同意请求；猪目标在检查通过后直接进入手持 |
| `accept(target)` | 接受当前请求，重新验证双方及空间后尝试附着 |
| `decline(target)` | 移除当前待确认请求 |
| `release(actor)` | 任一参与者结束自身关系，并尝试安全安置目标 |
| `recover(actor)` | 为自己或自己留下的待安置目标重试退出 |
| `recover(source, target)` | 管理员为指定目标重试退出 |
| `views()` | 当前 HELD／RECOVERY 的只读快照，不包含待确认请求 |

玩家请求有效期为 200 个服务器 tick；重复同一请求不会延长期限。客户端必须支持本版本同步协议。初版为单宿主单目标、无嵌套，宿主倍率至少为目标的两倍，距离不超过 `min(6, 2 × 宿主倍率)` 格。接受请求也必须来自目标自己的动作，扩展不得替玩家自动接受。

`CarryResult.code()` 是可用于程序判断的结果；`detail()` 为诊断文本，不能作为稳定协议解析。`succeeded()` 表示操作被接受或已完成：`REQUESTED` 仍在等待同意，`RECOVERY` 仍在等待安全安置，均不等于已经处于 HELD 或已经落地。`changed()` 表示关系／恢复状态是否发生变更，不表示是否发送了提示或修改了待确认请求。

`CarryView.anchor()` 是最近经过服务端验证的实体根位置，单位为世界格。它不是骨骼局部偏移，也不是任意目标位置写入入口；调用方不应自行把目标传送过去。`RECOVERY` 的 `hostEntityId` 为 `-1`，原宿主已失去位置控制。

## 退出与存档

断线、死亡、尺寸冲突、传送导致的不合法状态，以及服务器停止都会结束活动携带。没有安全落点时撤销宿主控制并保留有界的恢复流程，玩家仍可使用退出／恢复命令。重启只恢复安全退出，不恢复未经再次确认的捕获。

恢复数据使用世界内 `data/magnitude/carry-recovery.json` 的 schema 1 和实体持久附件；它不是扩展可修改的公共存档接口。未知或损坏的 journal 会禁用新捕获，并尝试按合法实体标记恢复；不会覆盖损坏文件。卸载前应释放所有目标并关闭世界。遇到存储错误，保留世界与 journal 供恢复，不要通过删除它来清除提示。

网络使用 `magnitude:carry_state_v1`，服务端会话与顺序号只用于本会话同步；客户端不能据此提交位置或授权。API 版本、协议版本和存档 schema 分别演进。

## 独立示例

- [纯 Java 核心消费示例](../examples/core-consumer/README.md)：尺寸与关系规则，不运行 Minecraft。
- [Fabric 消费示例](../examples/fabric-consumer/README.md)：通过已发布产物调用尺寸与携带服务。

示例是独立工程。发布本地 Maven 产物后再构建，不会回退到本项目源码，也无需启用自定义 GUI。
