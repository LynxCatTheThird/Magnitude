# Magnitude for Minecraft 26.3 Fabric

这是一个面向 Minecraft Java Edition 26.3 Fabric 的尺寸、携带、冲击和流体玩法模组。它把尺寸变化限制在有限范围内，并由服务端验证玩家请求；默认尺寸范围是 `1/64` 到 `32` 倍。

## 安装

1. 安装 Minecraft Java Edition 26.3、Fabric Loader 0.19.5 或更新版本，以及对应的 Fabric API。
2. 将 `magnitude-0.1.0+26.3.jar` 放进客户端和服务端的 `mods` 文件夹。缩放运行时已内置在这个 JAR 中，不需要另放依赖。
3. 首次启动后可编辑世界或服务端目录下的 `config/magnitude.json`，修改后使用 `/magnitude admin reload`。

客户端和服务端必须使用同一版本。单人游戏也会同时运行服务端逻辑。

## 基本操作

命令根节点是 `/magnitude`。常用命令如下：

| 命令 | 作用 |
|---|---|
| `/magnitude get` | 查看当前尺寸、目标尺寸和碰撞箱高度 |
| `/magnitude set <倍数> [ticks]` | 设置目标尺寸 |
| `/magnitude multiply <倍数> [ticks]` | 按当前目标尺寸相乘 |
| `/magnitude add <倍数> [ticks]` | 在当前目标尺寸上加值 |
| `/magnitude height <方块高度> [ticks]` | 按标准玩家高度换算尺寸 |
| `/magnitude reset` | 恢复为 1 倍 |
| `/magnitude consent resize true/false` | 允许或拒绝其他玩家改变自己的尺寸 |
| `/magnitude consent carry true/false` | 允许或拒绝被其他玩家携带 |
| `/magnitude terrain true/false` | 开关自己的落地冲击地形破坏 |
| `/magnitude pickup` | 拾取准星指向且同意被携带的实体 |
| `/magnitude release` / `/magnitude throw` | 释放或投掷携带的实体 |
| `/magnitude ride` | 骑乘准星指向的实体 |
| `/magnitude blow` / `/magnitude stomp` | 施放推力或落地冲击 |
| `/magnitude ability` | 使用当前尺寸对应的能力 |

管理员命令位于 `/magnitude admin`，包括对实体选择器批量设置、读取、重置、配置重载、全局地形破坏和食物尺寸系数。管理员命令需要游戏管理员权限。

## 客户端按键

按键可在“选项 -> 控制 -> Magnitude”中修改。默认按键：

- `Z` 按住观察，`N` 切换观察；`=` 和 `-` 调整倍率，`R` 重置视图。
- `G` 切换平滑镜头，`J` 隐藏实体名称，`[` 和 `]` 调整精度。
- `B` 推力，`V` 冲击，`X` 释放，`H` 投掷，`K` 能力，`M` 骑乘。

客户端按键只发送动作请求，最终判定在服务端执行。

## 物品与流体

创造模式物品栏中提供扩张、收缩、平衡、储存器、调谐器、光束、携带装具、休息装具、滑翔装具和四件尺寸装备。调谐器可以保存目标数值、操作模式、持续时间和绑定目标；手持调谐器执行 `/magnitude tool ...` 可配置它。

琥珀精华和蔚蓝精华提供两种可携带流体，并有桶、池、盆和场方块。相关配方、药水和战利品已经随模组注册。

## 规则与安全边界

- 尺寸输入必须是有限数，并被限制在配置范围内；服务端拒绝越界、远距离或未同意的目标。
- 玩家死亡默认不会保留尺寸，配置 `keepSizeAfterDeath` 可改变这一点。
- 地形破坏默认关闭。开启后仍会保护基岩、容器、流体、世界边界外方块和受保护标签方块，并受每 tick 检查和破坏配额限制。
- 携带位置、偏移、随机尺寸、同意状态和冷却状态会写入实体数据，重启后恢复。

## 验证

已在 Java 25、Fabric Loader 0.19.5、Fabric API 0.160.7+26.3 和 Minecraft 26.3 服务端完成构建与运行验证：规则测试 300012 项，真实服务端集成测试 36 项，覆盖注册、缩放碰撞箱、实体持久化、药水、流体、命令、携带同意及冲击保护。
