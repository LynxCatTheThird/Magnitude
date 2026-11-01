# Magnitude

Minecraft 体型交互与玩法能力库。当前试玩构建 `0.0.2-dev.1` 提供受控尺寸调整、玩家确认式手持、猪的手持跟随，以及放下和异常恢复。

## 运行环境

- Minecraft **26.3**
- Fabric Loader **0.20.1** 或兼容版本
- Fabric API **0.165.0+26.3**
- Java **25**

将 `magnitude-fabric-<版本>.jar` 和对应的 Fabric API 放入客户端与服务器的 `mods` 目录。核心库已内嵌，不要额外安装 `magnitude-core`，也不要同时放入多个 Magnitude 版本。

单人游戏直接使用同一个模组文件。建议先在专门的测试世界体验。尺寸调整需要管理员权限；携带请求、接受、拒绝和自行退出不需要管理员权限。

## 快速试玩

1. 打开允许命令的测试世界，执行 `/magnitude scale 2`。
2. 对准附近的普通猪，执行 `/magnitude carry request`。
3. 走动、转身，观察猪是否在身前跟随。
4. 执行 `/magnitude carry drop` 放下。

与其他玩家测试时，双方都安装模组，携带者至少是目标体型的两倍。可以用 `/magnitude carry request 玩家名` 发出请求，目标需在有效期内执行 `/magnitude carry accept`；也可执行 `decline` 拒绝。

| 命令 | 用途 |
| --- | --- |
| `/magnitude status` | 查看当前功能入口 |
| `/magnitude scale <倍率> [目标]` | 管理员调整自己或目标尺寸 |
| `/magnitude carry request [目标]` | 请求携带；省略目标时使用视线选择 |
| `/magnitude carry accept` | 接受玩家携带请求 |
| `/magnitude carry decline` | 拒绝请求 |
| `/magnitude carry drop` | 携带者放下目标 |
| `/magnitude carry escape` | 被携带者自行退出 |
| `/magnitude carry recover` | 重试待完成的安全安置 |
| `/magnitude carry recover <目标>` | 管理员协助指定玩家或猪恢复 |

## 本构建的范围

- 支持玩家与猪，倍率 **0.25～4**，单人单目标，不支持嵌套携带。
- 玩家目标必须确认；没有安装对应客户端能力时不会开始携带。
- 手持位置在身前。玩家暂时保留原版乘员坐姿，尚无专用抱持动画。
- 目标路径受阻、尺寸改变或参与者离开时结束携带，并尝试安全放下。危险区域和未知区块不会被当作安全空间。
- 没有安全落点时，先撤销携带控制，保留受保护的待安置状态；可自行重试或由管理员协助。
- 此版本不提供完整身体部位碰撞、极端倍率、压力伤害、鞋袜或吞咽玩法。
- 正在携带或恢复中的实体不能通过本模组改变尺寸。外部模组修改尺寸时，按冲突／释放路径处理，不承诺任意模组组合兼容。

更多场景及反馈信息见 [试玩步骤](docs/TESTING.md)。

## 开发构建

```bash
./gradlew :core:test :fabric:runGameTest :fabric:runClientGameTest :fabric:jar
```

`JAVA_HOME` 需指向 Java 25。产物在 `fabric/build/libs/`；带 `gametest` 或 `sources` 后缀的文件不是玩家安装包。真实客户端测试需要图形环境。

`core` 不依赖 Minecraft，`fabric` 提供平台接线。[API 契约](docs/API.md)说明线程、权限、返回值和恢复语义；独立示例分别演示[核心规则](examples/core-consumer/README.md)与 [Fabric 服务](examples/fabric-consumer/README.md)。公开 API 尚为实验性，升级前请检查对应版本。
