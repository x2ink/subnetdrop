# Android Navigation3 状态刷新验证

## 根因

Android 手机使用 `CompactContent`。它的 `NavDisplay` back stack 在主页保持为同一个 `HomeRoute`，而
Navigation3 1.1.1 的 `rememberDecoratedNavEntries` 只在 back stack 内容变化时重新调用 `entryProvider`。
原实现创建主页 `NavEntry` 时直接捕获整个 `AppUiState`，导致运行时状态与底部栏状态后续更新后，entry 内容
仍渲染旧快照。

这解释了真机上的两个同步现象：Ktor 端口已经监听、Android NSD 已经开始工作，但横幅仍停在启动阶段；
底部栏点击回调已经进入 ViewModel，但页面仍显示附近设备。

## 修复

- `CompactContent` 使用 `rememberUpdatedState(ui)` 保存稳定的状态引用。
- 缓存的主页和聊天 `NavEntry` 在自己的组合内容中读取该状态引用的最新值。
- 底部栏恢复为 ViewModel `StateFlow`，继续通过 `collectAsStateWithLifecycle()` 收集。
- 删除排查期间加入的 Ktor 后台启动任务和 loopback 端口轮询，恢复 `start(wait = false)`。

## PGFM10 真机验证

设备运行 Android 16，使用 Debug APK，无调试器连接。

1. 冷启动结果：`Status: ok`，`LaunchState: COLD`，`TotalTime: 705 ms`。
2. 启动 2 秒后横幅显示“已在线 · 仅同一局域网可见”。
3. 点击“聊天”后显示“还没有聊天”，聊天项出现选中态。
4. 点击“设置”后显示本机信息、安全与存储卡片，设置项出现选中态。
5. `*:45892` 处于 `LISTEN` 状态。
6. 日志中没有 `ANR`、`Input dispatching timed out`、`FATAL EXCEPTION` 或 `AndroidRuntime` 错误。
7. 页面切换后的帧统计：50th percentile 为 7 ms，janky frames 为 10/85（11.76%）。

此前一条输入超时 ANR 与 `selectSection()` 调试断点的暂停时间及点击坐标完全一致；断开调试器后的冷启动和
页面切换没有复现，因此该 ANR 被判定为调试器副作用，不作为产品故障。

## 自动化验证

```shell
./gradlew :network:jvmTest --tests ink.x2.subnetdrop.network.transport.SubnetDropTransportTest \
  :app:shared:jvmTest :app:desktopApp:compileKotlin :app:androidApp:installDebug
```

结果：`BUILD SUCCESSFUL in 16s`，114 个 task；Debug APK 成功安装到一台 PGFM10。

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest :app:shared:jvmTest \
  :app:desktopApp:compileKotlin :app:androidApp:compileDebugKotlin
```

结果：`BUILD SUCCESSFUL in 1s`，60 个 task，7 个执行，53 个 up-to-date。
