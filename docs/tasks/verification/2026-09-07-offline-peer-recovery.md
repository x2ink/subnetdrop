# 离线设备自动恢复验证

日期：2026-09-07

## 问题与现场证据

桌面端开启 VPN 时，Android 能看到桌面，桌面却长期把 Android 显示为离线。现场检查确认：

- macOS Wi-Fi 为 `192.168.20.86/23`，Android 为 `192.168.21.100/23`，处于同一局域网；
- 桌面 UDP `45893` 监听器收到 Android 的主动公告；
- 桌面到 Android 的 ICMP、TCP `45892` 与 WebSocket `PING/PONG` 均成功；
- Android 服务监听端口与公告端口一致；
- 35 秒桌面 JVM Flight Recording 中，Android 每 5 秒连接桌面，而桌面没有向 Android `45892` 发起连接。

这些证据排除了当前 Wi-Fi 可达性、Android 监听服务和基础 VPN 路由故障。故障发生在桌面发现状态机：已知设备只在
启动时探测一次，首次探测失败或在线设备连续三次失败后，端点会从心跳集合删除。没有新的组播公告时，数据库中的
OFFLINE 记录就再也不会被探测。

## 修复

- 已知设备和曾确认设备的端点在离线后继续保存在发现层；在线状态不再决定是否保留探测目标。
- 在线设备每 5 秒探测；离线设备按 5、10、20、30 秒封顶退避，避免永久离线与固定高频空轮询。
- 探测恢复后重新发出 `Found`，由运行时把 SQLDelight 记录更新为 ONLINE，不要求重新收到组播。
- 失败只影响与已确认 host/port 相同的端点，伪造或尚未验证的新地址不能让真实端点掉线。
- 停止、清理与重新启动发现会话在同一生命周期锁中串行，避免旧会话清理覆盖新会话状态。
- 探测协程即使被取消，也在 `NonCancellable` 清理段释放 in-flight 标记，避免端点永久无法再次调度。

## 自动化验证

发现状态机新增并覆盖以下场景：首次启动探测失败后仍可重试、在线设备连续失败后保留端点、无需新公告即可恢复、
未验证端点隔离、名称变化不掩盖同一路由失败，以及 5/10/20/30 秒有界退避。

```shell
./gradlew :network:jvmTest \
  --tests ink.x2.subnetdrop.network.discovery.UdpPeerDiscoveryTest \
  :network:compileAndroidMain
```

结果：成功；`UdpPeerDiscoveryTest` 共 9 个测试，0 failure，0 error。

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest \
  :app:shared:jvmTest :app:desktopApp:compileKotlin \
  :app:androidApp:assembleDebug
```

结果：成功；117 个 task，15 executed，102 up-to-date。Android 只构建 Debug APK，没有安装到设备。

```shell
git diff --check
git diff --cached --check
```

结果：均通过。

## 真实设备验证

保持 macOS 的 FlClash VPN 开启，Android 应用继续使用此前已经运行的版本，不重装、不重启手机端。修复后的桌面
应用于 13:22:50 启动后，SQLDelight 中 PGFM10 的记录由 OFFLINE 更新为 ONLINE：

```text
PGFM10  192.168.21.100  45892  ONLINE  2026-09-07 13:22:50
```

同时系统连接表显示桌面进程主动建立了以下连接：

```text
192.168.20.86:<ephemeral> -> 192.168.21.100:45892 (ESTABLISHED)
```

这验证了桌面端能够从数据库已知地址主动恢复 Android 在线状态，且不依赖关闭 VPN、重装 Android 或等待人工刷新。

## 未覆盖边界

- 本轮没有重新安装 Android；Android 当前运行的是此前已安装版本。
- Windows 防火墙、VPN kill switch/lockdown 与真实 Windows 互操作仍需在对应目标系统验证。
