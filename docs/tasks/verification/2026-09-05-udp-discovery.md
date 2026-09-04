# UDP 设备发现与在线状态验证

日期：2026-09-05

## 变更范围

- Android 与桌面端由平台 mDNS/DNS-SD 改为共享 IPv4 UDP 组播发现。
- 新设备公告按 100 ms、500 ms、2 s 突发发送，并每 30 s 低频重复。
- 公告只产生候选地址；现有 Ktor `/chat` WebSocket `PING/PONG` 在 1.5 s 内成功后才写入 ONLINE。
- 数据库中的已知地址在启动时并发探测；在线设备每 5 s 探测，连续 3 次失败写入 OFFLINE。
- PING 服务端只读取 `DeviceProfile`，不会生成 HPKE 或 Ed25519 密钥。
- SQLDelight 继续作为 peer 状态唯一数据源，ViewModel 通过 `stateIn` 暴露 `StateFlow`。

## 自动验证

```shell
./gradlew :network:jvmTest
```

结果：通过。覆盖发现报文合法性、无效报文拒绝、三次失败离线、失败后恢复、地址变化刷新，以及真实 Ktor
服务端的 PING/PONG。测试同时确认探测过程不会写入密码学密钥存储。

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest :app:shared:jvmTest \
  :app:desktopApp:compileKotlin :app:androidApp:compileDebugKotlin
```

结果：通过，共执行或复用 60 个 Gradle task；JVM 回归、Android 编译和桌面编译均成功，Android 编译无本次
变更产生的警告。

```shell
git diff --check
```

结果：通过，无空白错误。

## 尚未宣称通过的边界

- 按用户约束没有向 Android 设备安装 APK，也没有执行 ADB 操作。
- 新 UDP 协议尚未在 Android 与 macOS 两台真实设备间测量首次发现延迟。
- Windows 防火墙放行 UDP `45893` 与 TCP `45892` 的安装包行为仍需 Windows Runner/真机验证。
- 当前只实现 IPv4 组播和已知地址探测；IPv6 与 `/24` 扫描兜底没有启用。
