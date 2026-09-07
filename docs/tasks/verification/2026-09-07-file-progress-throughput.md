# 文件进度同步与吞吐优化验证

日期：2026-09-07

## 问题证据与根因

- 同一传输截图中，电脑发送端显示 `216.2 MB / 216.2 MB`，Android 接收端同时只显示
  `180.0 MB / 216.2 MB`，相差 36.2 MiB，约等于 72 个 512 KiB 数据帧。
- 旧发送端在 `WebSocketSession.send` 返回后立即增加进度，但 Ktor 3.5 的 `send` 只是向 outgoing channel 入队，
  不代表 TCP 写完或对端落盘。
- Ktor 3.5 WebSocket 收发 channel 默认无限容量，发送端与接收端可以保留大量 512 KiB `ByteArray`，造成虚假
  100%、内存压力和 GC。
- 接收端原本在全局 `transferMutex` 内执行文件写入、SHA-256 和 StateFlow 列表更新，使最多三路文件的磁盘路径
  实际串行，并阻塞当前 Ktor 会话处理。

## 修复后的语义

- 每 4 MiB 数据后，发送端在同一有序 WebSocket 插入签名 `FILE_STREAM_PROGRESS` 检查点。
- 接收端完成此前数据的校验与写盘后，只对等于实际累计字节的检查点签名回应。
- 发送端只使用接收端确认值更新文件卡片；尾段在 `FILE_STREAM_COMPLETE` 前补一次确认。
- 文件帧 outgoing/incoming channel 容量设为 2，通过挂起而不是丢帧来传播 TCP 背压。
- 接收端直接消费 Ktor frame data，减少一次整块复制；写盘与摘要位于 IO dispatcher，并由每个 session 独立保序。

## 自动化验证

```shell
./gradlew :network:jvmTest --tests ink.x2.subnetdrop.network.transport.SubnetDropTransportTest
```

结果：`BUILD SUCCESSFUL`。9 个传输专项测试全部通过。新增 10 MiB 回环测试覆盖：

- 观察到非零、非终态的中间进度；
- 发送端确认进度从不超过接收端；
- 短暂差值不超过一个 4 MiB 窗口；
- 两端最终字节相同，接收文件逐字节一致；
- JUnit 报告中该用例耗时 0.13 秒。该数字只能证明本机回环代码路径没有 6 MB/s 的固定上限，不能代表真实 Wi-Fi。

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest :app:shared:jvmTest \
  :app:desktopApp:compileKotlin :app:androidApp:assembleDebug
```

结果：`BUILD SUCCESSFUL`，117 个 task（18 个执行，99 个复用缓存）。完整 JVM 回归、桌面编译与 Android Debug
APK 构建通过。

```shell
git diff --check
```

结果：通过。本次没有安装 Android APK、执行 ADB、commit 或 push。

## 尚未覆盖

- 未在截图对应的电脑、Android 手机与 Wi-Fi 环境重新测量真实吞吐。
- 真实速度仍受 Wi-Fi 频段、信号、路由器客户端隔离、VPN 策略和 Android 接收目录后端影响。
- WebSocket 客户端按协议必须执行 masking；若背压、内存和锁优化后仍存在 CPU 上限，应评估保持认证控制帧的
  专用 Ktor TCP/HTTP 数据通道，不能通过关闭 masking 破坏 WebSocket 协议。
