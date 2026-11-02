# Magnitude

Minecraft 体型交互与玩法能力库。当前试玩构建 `0.0.3-dev.1` 提供受控基础倍率与独立宽高、玩家确认式手持、猪的手持跟随，以及放下和异常恢复。

## 运行环境

- Minecraft **26.3**
- Fabric Loader **0.20.1** 或兼容版本
- Fabric API **0.165.0+26.3**
- Java **25**

安装文件为 **`dist/0.0.3-dev.1/magnitude-fabric-0.0.3-dev.1.jar`**。将这个文件和对应的 Fabric API 放入客户端与服务器的 `mods` 目录。核心库已内嵌，不要额外安装 `magnitude-core`，也不要同时放入多个 Magnitude 版本。

单人游戏直接使用同一个模组文件。建议先在专门的测试世界体验。尺寸调整需要管理员权限；携带请求、接受、拒绝和自行退出不需要管理员权限。

## 快速试玩

1. 打开允许命令的测试世界，执行 `/magnitude scale 2`。
2. 对准附近的普通猪，执行 `/magnitude carry request`。
3. 走动、转身，观察猪是否在身前跟随。
4. 执行 `/magnitude carry drop` 放下。

与其他玩家测试时，双方都安装模组，携带者至少是目标体型的两倍。可以用 `/magnitude carry request 玩家名` 发出请求，目标需在有效期内执行 `/magnitude carry accept`；也可执行 `decline` 拒绝。

| 命令 | 用途 |
| --- | --- |
| `/magnitude status` | 查看实际加载的版本及当前功能入口 |
| `/magnitude scale <倍率> [目标]` | 管理员调整基础倍率 |
| `/magnitude shape <宽倍率> <高倍率> [目标]` | 管理员调整相对宽高；`1 1`恢复正常比例 |
| `/magnitude carry request [目标]` | 请求携带；省略目标时使用视线选择 |
| `/magnitude carry accept` | 接受玩家携带请求 |
| `/magnitude carry decline` | 拒绝请求 |
| `/magnitude carry drop` | 携带者放下目标 |
| `/magnitude carry escape` | 被携带者自行退出 |
| `/magnitude carry recover` | 重试待完成的安全安置 |
| `/magnitude carry recover <目标>` | 管理员协助指定玩家或猪恢复 |

## 本构建的范围

- 支持玩家与猪，倍率 **0.25～4**，单人单目标，不支持嵌套携带。
- 宽高、碰撞箱、眼高和模型采用同一确认形状；基础倍率与相对宽高各应用一次，组合后的宽／高倍率也必须在0.25～4内。移动速度、跨阶高度和交互距离仍保留原版行为。
- 客户端与服务器均需支持0.0.3的形状同步能力；旧0.0.2客户端会被拒绝连接。玩家目标仍必须确认。
- 本版内化了Pehkui Rebuilt的几何／渲染接线，见[来源与许可](docs/THIRD_PARTY.md)。不要同时安装Pehkui、Pehkui Rebuilt或Scopic；不提供其API／命令／存档兼容。
- 手持位置在身前。玩家暂时保留原版乘员坐姿，尚无专用抱持动画。
- 目标路径受阻、尺寸改变或参与者离开时结束携带，并尝试安全放下。危险区域和未知区块不会被当作安全空间。
- 没有安全落点时，先撤销携带控制，保留受保护的待安置状态；可自行重试或由管理员协助。
- 此版本不提供完整身体部位碰撞、极端倍率、压力伤害、鞋袜或吞咽玩法。
- 正在携带或恢复中的实体不能通过本模组改变尺寸。外部模组修改尺寸时，按冲突／释放路径处理，不承诺任意模组组合兼容。

已在 Linux 上验证单客户端的尺寸移动、两客户端的携带与退出，以及世界关闭重开恢复。本构建仍为实验试玩版，尚未完成普通独立服务器部署、多人数压力和高延迟网络验收。

可以用 `/magnitude shape 2 0.5` 体验宽扁形状，用 `/magnitude shape 0.5 2` 体验窄高形状，再用 `/magnitude shape 1 1` 还原。已有0.0.2存档默认形状为`1 1`，已有基础倍率和携带恢复记录保留。

更多场景及反馈信息见 [试玩步骤](docs/TESTING.md)。

## 开发构建

```bash
./gradlew :core:test :fabric:runGameTest :fabric:runClientGameTest :fabric:stageRelease
```

`JAVA_HOME` 需指向 Java 25。`stageRelease` 检查实际 JAR 的版本、入口类、Mixin 类和内嵌核心后，将玩家安装包及 SHA-256 放到 `dist/<版本>/`。源码与测试包放在 `fabric/build/development/`，不能安装到 `mods`。真实客户端测试需要图形环境。

若出现版本 `${version}` 和 Mixin 类找不到，请检查是否误装了旧版 `-sources.jar`：它包含源码而非运行所需的 class。移出源码包、测试包和旧 Magnitude 后，只保留上述安装文件。

`core` 不依赖 Minecraft，`fabric` 提供平台接线。[API 契约](docs/API.md)说明线程、权限、返回值和恢复语义；独立示例分别演示[核心规则](examples/core-consumer/README.md)与 [Fabric 服务](examples/fabric-consumer/README.md)。公开 API 尚为实验性，升级前请检查对应版本。
