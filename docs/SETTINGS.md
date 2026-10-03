# 设置界面与命令

适用版本：0.1.4-alpha7+26.3。客户端与服务器使用同一版本。

## 打开界面

游戏内按 `F8`（可在控制设置中改键），或执行 `/magnitude menu`。暂停菜单与“选项”页面右上角也有 Magnitude 按钮；未连接世界时可以通过选项页面修改本地视角。

界面提供概览、视角、个人、服务器和诊断五页，内容较多时用左右按钮分页。界面保持世界运行，以便服务器处理和确认设置。

| 页面 | 内容 | 谁能修改 |
| --- | --- | --- |
| 概览 | 当前尺寸、地形和压力允许状态、未生效原因 | 只读，点击刷新更新 |
| 视角 | 观察倍率、灵敏度、平滑镜头、实体名称 | 本地玩家 |
| 个人 | 地形、压力、改变尺寸和被携带许可 | 本人，服务器确认后生效 |
| 服务器 | 尺寸范围、地形/压力/身体伤害、死亡规则和材料作用配额 | 游戏管理员；其他玩家只读 |
| 诊断 | 服务器刻耗时、移动查询耗时、拒绝次数和待处理脚印 | 只读，点击刷新采样 |

修改先保留为草稿，点击“应用”才保存或提交。关闭未应用的草稿会询问是否放弃；已提交的服务器请求不会因关页而取消。服务器修改须收到确认；保存失败保留原生效设置。其他操作修改了同一配置版本时，服务器拒绝过期提交并返回最新状态，核对草稿后可重新应用。

“刷新状态”更新确认值，不清除草稿；“撤销修改”清除当前页草稿。界面不持续轮询，诊断也不是实时性能曲线。确认超时后先刷新核对实际结果，因为请求可能已在服务器完成。

服务端作用需服务器规则与个人许可同时允许，建造权限和当前玩家状态也可能阻止作用。显示“已允许”不保证每块材料一定破坏：保护、容器、流体、强度和预算仍逐次检查。

当前地面仍使用单层脚印卸载后分批挖掘。浅层形变、即时视觉脚印、效果质量预设及体积模拟尚未提供，界面不显示这些功能的可用开关。

## 推荐命令结构

| 分组 | 示例 | 用途 |
| --- | --- | --- |
| scale | `/magnitude scale get`、`scale set 5 20`、`scale reset` | 自身尺寸 |
| config | `/magnitude config show` | 查看有效状态；服务器控制台显示规则 |
| config player | `/magnitude config player terrain on` | 个人许可，字段为 terrain、pressure、resize、carry |
| config server | `/magnitude config server terrainDamage true` | 管理员修改服务器规则 |
| config server | `/magnitude config server maximum 64` | 设置尺寸上限；读取单字段时省略值 |
| config server | `/magnitude config server` | 管理员列出服务器字段和值 |
| config reload | `/magnitude config reload` | 管理员重读服务器配置 |
| action | `/magnitude action pickup`、`action release`、`action stomp` | 动作请求 |
| carry / tool / random | `/magnitude carry hand`、`tool value 2`、`random stop` | 携带、工具和随机规则 |
| admin | `/magnitude admin set @e[type=minecraft:pig] 2` | 管理员实体操作与食物系数 |
| physics status | `/magnitude physics status` | 保留详细物理诊断 |

服务器布尔字段用 true/false；个人许可用 on/off。服务器字段与 GUI 一致：minimum、maximum、allowSelfChange、terrainDamage、standingPressure、bodyDamage、keepSizeAfterDeath、walkDamageFactor、landingDamageFactor、pressureHardnessFactor、impactScaleFactor、blocksPerTick、checksPerTick、blocksPerImpact、impactRadius。自动补全提供字段与合法值范围；整数配额拒绝小数。

原有根节点尺寸/动作、consent、terrain、physics 参数及 admin reload/terrain/food 保持兼容，并调用同一配置服务。`scale reset` 现在是直接入口，旧 `scale get reset` 也保留。

`/magnitude physics enable` 保留快捷行为：普通玩家开启自身地形与压力许可；管理员同时开启服务器两项规则。`disable` 对应关闭；执行后显示有效状态。GUI 的个人页与服务器页始终分别提交，不隐式改变另一范围。

## 配置存储与性能边界

服务器规则存于 `config/magnitude.json`，客户端视角存于 `config/magnitude-client.json`，个人许可沿用实体存档。服务器配置仍对整个服务器生效，本版没有按世界覆盖配置。

写入先形成临时文件，成功替换后才发布配置，替换前保留 `.bak`。缺失字段采用默认值；服务器文件新增 schemaVersion=1，较新不支持的格式拒绝加载。配置文件只支持本版已定义字段，不把它作为第三方任意配置容器。

服务器修改需要真实服务端管理员权限，隐藏/禁用按钮并不是权限边界。客户端编辑请求限制字段数量和长度，并检查类型、范围、版本和请求频率。界面不会修改碰撞权威方向或放宽原有世界访问预算。

诊断页耗时来自服务器：服务器平均刻时，以及最近最多128次移动查询的平均/最大值。它们不是客户端 FPS；最近失败原因也可能属于姿态或位置检查。通过启动或菜单自动化不能证明复杂世界长期性能。
