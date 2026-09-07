# VPN 与局域网发现共存验证

日期：2026-09-07

## 覆盖范围

- 真实 IPv4 LAN 网卡仍可加入 UDP 组播组。
- point-to-point、虚拟以及 `tun`、`tap`、`utun`、`ppp`、`ipsec`、`wg`、`vpn`、`tailscale` 隧道网卡被排除。
- Android 在打开发现 Socket 前将进程绑定到 IPv4 Wi-Fi `Network`，让后续 Ktor 探测和业务连接避开 VPN 默认路由。
- 发现停止或启动失败时恢复此前的进程网络；恢复失败时清回系统默认网络。
- 无 IPv4 Wi-Fi、系统拒绝绑定或 Android 网络 API 抛错时安全降级，不阻塞发现启动。
- Android 新增 `CHANGE_NETWORK_STATE` 普通权限，没有新增运行时授权界面。

## 自动化验证

```shell
./gradlew :network:jvmTest \
  --tests ink.x2.subnetdrop.network.discovery.UdpPeerDiscoveryTest \
  :app:desktopApp:compileKotlin \
  :app:androidApp:assembleDebug
```

结果：`BUILD SUCCESSFUL`。网卡选择单测通过，桌面端编译成功，Android Debug APK 生成成功。首次运行发现 Android
同步网络快照 API 的弃用提示；实现需要在第一个 Socket 打开前完成选择，因此将说明和 suppress 限定在该函数。

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest :app:shared:jvmTest \
  :app:desktopApp:compileKotlin :app:androidApp:assembleDebug
```

结果：`BUILD SUCCESSFUL`，共 117 个 task（18 个执行，99 个复用缓存）。完整 JVM 回归、桌面编译和 Android
Debug APK 构建均通过，本次代码没有产生编译警告。

最后的恢复分支格式调整后再次运行发现规则定向单测与 `:app:androidApp:compileDebugKotlin`，结果
`BUILD SUCCESSFUL`，共 42 个 task（8 个执行，34 个复用缓存）。

```shell
git diff --check
```

结果：通过，无空白错误。本次未安装 Android APK，未执行 ADB，也未 commit 或 push。

## 尚未覆盖

- 尚未在两台真实设备同时开启 VPN 后测量首次发现与聊天/文件连接。
- VPN kill switch、Android always-on lockdown 或 VPN 客户端明确关闭“允许局域网访问”时，系统可能拒绝绕过隧道；
  应用不能越过该安全策略。
- Windows 防火墙及会强制 RFC1918 私网地址进入隧道的桌面 VPN 仍需 Windows 实机验证。
