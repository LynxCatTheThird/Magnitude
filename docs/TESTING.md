# 构建与测试

需要 Java 25。设置 `JAVA_HOME` 指向自己的 JDK，使用仓库提供的 Gradle wrapper。第一次构建需要网络下载固定的 Minecraft/Fabric/Loom 依赖；已有缓存时可以加 `--offline`。

```bash
./gradlew --no-daemon check build
```

`check` 包含纯规则 `ruleTest` 与 `runVerificationServer`。测试源码和测试模组元数据随仓库提交；测试类与验证入口不会打包进生产 JAR。生产 JAR 位于 `build/libs/`。

验证服务端只监听 `127.0.0.1`，使用系统分配的临时端口、离线测试身份和 `.local/verification/` 下的专用测试世界；不需要用户账户。任务为测试服务端准备 EULA 和配置文件。测试只修改该测试世界，夹具每轮清理固定区域，等待确定的 tick 后执行；正常和异常路径都会停止测试服务端。任务最长运行 2 分钟。

结果写入 `.local/verification/results.json`。任务删除旧结果后启动；无结果或 `success=false` 时 Gradle 必须失败，不能以 Minecraft 进程自身的正常退出码替代测试结果。失败的断言名称会进入 Gradle 错误。

纯规则包含 300012 条断言，大部分是固定随机种子的尺寸转移性质验证。真实服务端用例覆盖尺寸边界/渐变/NBT、注册、许可、携带、抛出、原版玩家载体 guard、断连/卸载、真实死亡/维度传送、重生状态、药水效果来源、红石、流体、保护 veto、请求洪泛拒绝及密集候选查询配额；断连与重生部分使用事件回调，身份与连接对象在本地构造，没有第二个真人客户端。

确认失败传播可以执行以下诊断命令；预期退出码非零：

```bash
./gradlew --no-daemon runVerificationServer -PverificationFailure=true
```

它在正常用例完成后故意抛出断言错误。之后重新运行普通 `check` 获得有效成功结果。

客户端开发启动：

```bash
./gradlew --no-daemon runClient
```

客户端需要可用显示环境。启动检查覆盖入口、Mixin 和资源加载，不覆盖世界中的全部交互。进一步人工验收应包含：两客户端 opt-in/撤回、携带偏移同步与安全释放、喷溅/滞留/箭投递、巨型/微型第一和第三人称视角、GUI、观察/精度按键、世界边界、具体领地模组以及混合场景长期负载。
