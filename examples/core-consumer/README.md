# 独立核心 API 消费示例

此目录是独立 Gradle 工程，通过本地 Maven 仓库中的 `dev.magnitude:core:0.0.3-dev.1` 消费核心公开 API。没有 project、composite build 或产品源码目录依赖，也不导入 `dev.magnitude.core.*`。

先使用 Java 25 与根目录的 Gradle 9.8.2 wrapper 发布待验证产物，再从独立工程运行：

```bash
./gradlew :core:publishCorePublicationToVerificationRepository
./gradlew -p examples/core-consumer run --refresh-dependencies
```

`JAVA_HOME` 应指向本机 Java 25。发行物由发布任务写到根目录 `.local/maven/`；尚未发布时，消费构建应报找不到依赖，不自动回退到源码。开发期间重复发布同一坐标后，使用 `--refresh-dependencies` 确保校验刚发布的产物。

示例验证：

- 倍率范围与非有限输入拒绝，观测几何不会重复乘倍率。
- 已换算为世界格的局部偏移，按公开 yaw 约定变换与逆变换。
- 一个会话中的抓取、重放、改载荷冲突和容量拒绝；请求序号按 actor＋epoch 从 1 单调增加，拒绝后的旧序号不能换 ID 再提交。
- 释放至待安置状态时撤销原宿主关系、归还槽位；只有显式安全安置授权才能完成恢复，请求自身携带 SAFE 不代表已获得授权。
- 新会话拒绝旧 epoch 的请求。

示例直接扮演可信服务端调用方构造授权，**不提供网络身份或同意校验实现**。真实适配器必须在授予权限前独立检查来源、许可、目标和世界条件。

这里没有运行 Minecraft，没有实施碰撞、实体移动、网络通信、落点搜索或磁盘恢复；通过只证明所用公开核心 API 可被独立发行物消费者编译和调用。
