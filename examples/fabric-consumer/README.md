# 独立 Fabric API 消费示例

这个示例是一个可单独构建的 Fabric 模组。它通过已发布的 Maven 产物调用 Magnitude 的公共尺寸和携带 API，不引用项目内部实现，也不读取产品源码目录。

固定环境：Minecraft 26.3、Fabric Loader 0.20.1、Fabric API 0.165.0+26.3、Java 25、Loom 1.19.3；构建使用根目录的 Gradle 9.8.2 wrapper。

## 构建

先在工作区根目录将 `JAVA_HOME` 指向 Java 25，再执行：

```bash
./gradlew :core:publishCorePublicationToVerificationRepository :fabric:publishFabricPublicationToVerificationRepository
./gradlew -p examples/fabric-consumer build --refresh-dependencies
```

依赖坐标为 `dev.magnitude:fabric:0.0.2-dev.1` 与 `dev.magnitude:core:0.0.2-dev.1`。本地 Maven 仓库位于工作区 `.local/maven/`；没有发布产物时应构建失败，不回退到源码或 composite build。

平台依赖关闭传递解析，core 仅用于编译期公开类型；运行时使用 Magnitude 模组内嵌的 core。示例自身不重复打包 Magnitude、core 或 Fabric API。

产物为 `build/libs/magnitude-fabric-consumer-0.1.0.jar`，路径相对于此示例目录。使用时将它与匹配版本的 Magnitude、Fabric API 放入专服 mods 目录；参与携带的两个客户端仍须安装匹配的 Magnitude。示例只提供服务端命令，专服玩家无需安装此示例。单人测试则在该客户端安装示例，由其集成服务器注册命令。

## 命令

| 命令 | 行为 |
| --- | --- |
| `/magnitude-example request <target>` | 当前真实玩家向目标发起携带；玩家目标仍须确认 |
| `/magnitude-example accept` | 被请求玩家确认 |
| `/magnitude-example decline` | 被请求玩家拒绝 |
| `/magnitude-example release` | 当前参与者放下或自退 |
| `/magnitude-example recover` | 重试本人的恢复或当前服务支持的关联恢复 |
| `/magnitude-example status` | 只显示与自己有关的关系状态 |
| `/magnitude-example observe [target]` | 读取公共尺寸快照；控制台需指定目标 |
| `/magnitude-example scale <value> [target]` | 管理员通过公共尺寸服务修改，保留库的权限／状态检查 |

例如，测试服管理员先将宿主设为 2 倍，再向附近 1 倍猪请求携带：

```text
/magnitude-example scale 2
/magnitude-example request @e[type=minecraft:pig,sort=nearest,limit=1]
/magnitude-example release
```

双人测试中，宿主使用目标玩家名字发起 request，由目标执行 accept；目标随时可执行 release。示例不会伪造目标同意、绕过体型比／范围／碰撞条件，或自行移动实体。所有调用都来自游戏命令的服务端执行线程。

## 结果含义与范围

`REQUESTED` 只表示请求已发送，`HELD` 才表示抓取成功；`RECOVERY` 表示原宿主控制已解除但尚未完成安全安置。失败结果原样显示原因，示例不改写拒绝为成功。

这是下游调用方式演示，不是 Magnitude 实现的一部分。构建成功只证明 API 可被独立产物消费，不证明双客户端行为、异常恢复或性能已经通过；这些结果应由实际运行记录提供。
