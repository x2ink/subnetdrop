# 当前平台与桌面验证

日期：2026-09-04

## 已验证范围

本轮最后一次实现级验证覆盖 FileKit 文件选择接入、Navigation 3 紧凑导航、单 WebSocket 文件分块上传、
SubnetDrop 身份存储迁移和桌面日志配置。

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest :app:shared:jvmTest \
  :app:desktopApp:compileKotlin :app:desktopApp:packageDmg
```

结果：全部通过。覆盖领域用例、SQLDelight 持久化、Tink 加密与篡改拒绝、配对、聊天 ACK/已读回执、接受与
拒绝文件传输、70,000 字节多分块传输，以及桌面安全存储迁移。

随后重新执行 `./gradlew :app:desktopApp:packageDmg` 验证最终日志配置，构建通过。当前产物：

- `app/desktopApp/build/compose/binaries/main/dmg/SubnetDrop-1.0.0.dmg`
- `app/desktopApp/build/compose/binaries/main/app/SubnetDrop.app`

打包后的当前应用已在 macOS 启动，并用于生成 README 桌面截图；截图中的本机账户名已替换为中性设备名
`Desktop`，避免把个人身份写入开源仓库。

## 历史真机结论

Android 与 macOS 曾在同一 Wi-Fi 上完成发现、双方安全码配对、双向加密消息、签名 ACK 与重启持久化验证。
该结论发生在 FileKit 替换和 SubnetDrop 品牌迁移之前，因此只能证明核心互操作路径，不能替代当前 Android
版本的完整回归。

## 尚未验证

- 本轮未运行任何 Android Gradle task、安装、模拟器、真机或 ADB 操作。
- FileKit 的 Android provider 文件选择路径尚未在当前版本真机测试。
- 当前品牌 Android 包需要重新进行发现、配对、聊天、已读和文件传输回归。
- Windows MSI 尚未在 Windows 主机构建，Windows 防火墙、Credential Manager 和三端互通未验证。
- macOS 正式签名、公证与发布身份下的 Keychain 隔离未验证。

## 文档验证

文档改组后检查根 README、架构、规格、技术原理和验证文档的所有本地 Markdown 链接；13 个 Markdown 文件的
相对路径均可解析。代码围栏成对，`git diff --check` 通过，已删除旧品牌截图和一次性重命名/Skill 记录。
文档变更没有触发 Android 构建或安装。
