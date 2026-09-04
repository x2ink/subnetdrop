# 局域网密聊

基于 Kotlin Multiplatform 与 Compose Multiplatform 的无中心一对一局域网聊天应用。设备连接同一可达 Wi-Fi
后可以自动发现、核对安全码、即时聊天和传输文件，全程不依赖互联网或中心服务器。

支持目标：

- Android 11 及以上。
- macOS 桌面端。
- Windows 10 及以上桌面端。

## 已实现能力

- Android NSD 与桌面 JmDNS 自动发现同一局域网设备。
- 双端核对安全码后建立信任；设备密钥变化会阻断静默替换。
- Ktor WebSocket 点对点即时消息，包含签名 ACK、幂等去重、失败重试和断线恢复。
- HPKE（X25519/HKDF-SHA256/AES-256-GCM）内容加密与 Ed25519 身份签名。
- SQLDelight 本地聊天记录、会话未读数，以及“未读 / 已读”消息状态。
- 已读回执按批次签名发送，发送方只更新属于对应受信设备的消息。
- 文件发送前由接收方确认；支持拒绝、取消、实时进度和失败状态。
- 文件以 24 KiB 加密分块流式传输，完成后验证长度与 SHA-256，再从临时文件发布。
- Compose Material 3 共享 UI：底部主导航、紧凑单栏和桌面自适应双栏。
- Koin 构造函数注入与 Clean Architecture 模块边界。

## 使用方式

1. 让两台设备连接同一个允许客户端互访的 Wi-Fi，避免使用开启 AP 隔离的访客网络。
2. 启动应用并允许局域网访问；Windows/macOS 防火墙弹窗应允许专用网络通信。
3. 在“附近设备”选择对端，两边核对并确认相同的六位安全码。
4. 进入一对一会话发送消息；使用输入框左侧的回形针按钮选择文件。
5. 对端接受文件后开始传输。桌面端文件保存到 `~/Downloads/LanChat`，不会覆盖同名文件。

通信默认监听 TCP `45892`。设备互相看不到时，先检查是否处于同一网段、VPN 是否拦截局域网、路由器
是否开启 AP 隔离，以及系统防火墙是否放行应用。

## 工程结构

| 模块 | 职责 |
|---|---|
| `:core` | 纯 Kotlin 领域模型、端口和用例 |
| `:data` | SQLDelight schema、消息与身份仓库适配 |
| `:network` | 设备发现、持久身份、配对、加密与 WebSocket 传输 |
| `:app:shared` | 共享 Compose UI、ViewModel、Koin 公共组合与运行时编排 |
| `:app:androidApp` | Android 入口、权限和进程生命周期 |
| `:app:desktopApp` | macOS/Windows 入口、窗口生命周期和原生分发 |

依赖方向始终指向 `core`。客户端之间直接通信，仓库不包含生产中心服务器。

## 快速开始

要求 Gradle Wrapper 9.1.0、JDK 21；Android 构建还需要 Android SDK 36。依赖仓库、Gradle 分发以及
Node/npm/Yarn 均使用项目级国内镜像配置，不需要修改用户全局配置。

```shell
# 运行桌面开发版
./gradlew :app:desktopApp:run

# 构建当前操作系统的桌面安装包
./gradlew :app:desktopApp:packageDistributionForCurrentOS

# 构建 Android Debug APK（不会自动安装）
./gradlew :app:androidApp:assembleDebug
```

macOS、Windows 和 Linux 的原生安装包必须在对应操作系统构建。全部构建、测试、诊断和安装命令见
[BUILD_GUILD.md](BUILD_GUILD.md)。

## 测试

```shell
# 领域、数据库、网络和共享 UI 的桌面/JVM 回归
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest :app:shared:jvmTest

# 加密聊天与文件传输端到端测试
./gradlew :network:jvmTest --tests ink.x2.kmp.network.transport.LanChatTransportTest
```

最近一次 macOS 验证、截图和产物记录见
[docs/tasks/verification/2026-09-04-mvp.md](docs/tasks/verification/2026-09-04-mvp.md)。

## 安全与数据边界

- 私钥保存在 Android Keystore 或桌面系统密钥存储中，不进入数据库。
- 聊天正文和文件内容在传输过程中端到端加密；ACK 和已读回执经过身份签名校验。
- 文件只有在用户接受且 SHA-256 校验成功后才会成为最终文件；异常临时文件会被清理。
- 聊天记录当前以明文保存在本机 SQLite。传输加密不等于磁盘数据库加密。
- 文件传输进度和传输卡历史当前只保留在本次应用会话中，接收成功的文件会持续保存在磁盘。
- 文件协议参考 LocalSend 的元数据优先会话模型，但不是 LocalSend 协议兼容实现。

## 文档与贡献

- [架构入口](docs/ARCHITECTURE.md)
- [聊天与安全协议](docs/spec/lan-chat-v1.md)
- [加密文件传输规格](docs/spec/file-transfer-v1.md)
- [构建手册](BUILD_GUILD.md)
- [项目开发约束](AGENTS.md)
- [当前任务与审查记录](docs/tasks/todo.md)

开始修改代码前请先阅读 [AGENTS.md](AGENTS.md)。协议、数据库或跨平台行为发生变化时，必须同步更新规格、
架构和验证记录。

首次克隆后安装项目 Git hooks：

```shell
bash scripts/setup-git-hooks.sh
```

之后直接运行 `git commit`，终端会引导选择 `feat`、`fix`、`docs`、`refactor` 等类型、影响范围、描述和
任务号，并自动生成 remote review `review footer`。不符合规范的 `git commit -m` 会被拒绝；符合 Conventional Commit
格式的自动化提交仍可使用 `-m`。推送前会自动执行桌面/JVM 回归检查。
