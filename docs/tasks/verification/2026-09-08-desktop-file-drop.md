# 桌面聊天页拖放发送文件验证

## 范围

- macOS/Windows 桌面聊天页接受操作系统文件列表拖放。
- 拖入期间展示明确的投放提示，离开、结束或投放后立即清除。
- 拖入文件与文件选择器共享批次上限、文件大小和元数据预检，并进入现有 `sendFiles`。
- Android 不注册桌面拖放目标，原有附件选择行为保持不变。

## 自动验证

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest :app:shared:jvmTest \
  :app:desktopApp:compileKotlin --no-configuration-cache --stacktrace
```

结果：`BUILD SUCCESSFUL`，51 个任务中 8 个执行、43 个为最新状态。核心、数据、网络、共享 UI 测试和桌面编译
全部通过。编译仅报告 `ChatScreen` 原有 `quadraticBezierTo` 弃用警告，与本次拖放无关。

```shell
./gradlew :app:shared:compileAndroidMain --no-configuration-cache --stacktrace
```

结果：`BUILD SUCCESSFUL`。Android actual 为无操作适配，未安装 Android 应用。

`PlatformFileDropTest` 覆盖普通文件读取、目录拒绝、超过 50 个文件拒绝，以及拖放与文件选择器共享的文件大小预检。

```shell
./gradlew :app:desktopApp:run --no-configuration-cache
```

结果：应用进入 `:app:desktopApp:run`，未出现拖放目标初始化异常，随后手动终止冒烟进程。当前 macOS UI 自动化
接口能识别运行中的 Zulu Java 进程，但无法附着该 Compose 窗口，因此没有把真实 Finder 指针投放标记为已验证。

## 尚未在本机自动化的边界

- 操作系统从 Finder/Explorer 到 Compose 窗口的真实指针拖动无法由当前 JVM 单元测试模拟。
- Windows Explorer 拖放需在 Windows 目标机或 GitHub Actions 之外的交互式 Windows 会话中最终验收。
