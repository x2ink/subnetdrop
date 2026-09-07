# 跨平台文字消息选择与复制验证

日期：2026-09-07

## 覆盖范围

- 仅文字消息正文进入 Compose 官方 `SelectionContainer`。
- Android 使用长按文本选择与系统复制工具栏。
- macOS/Windows 桌面使用鼠标选择以及 Cmd+C/Ctrl+C。
- 送达/已读状态、失败重试提示和文件消息不进入文本选择范围。
- 没有新增平台剪贴板实现或第三方依赖。

## 自动化验证

```shell
./gradlew :app:shared:jvmTest :app:desktopApp:compileKotlin :app:androidApp:assembleDebug
```

结果：`BUILD SUCCESSFUL`。共享 JVM、桌面和 Android source set 均解析 Compose 选择容器，Android Debug APK
生成成功。编译仍报告聊天气泡旧 Path 代码中的 `quadraticBezierTo` 弃用警告，与本次功能无关。

```shell
git diff --check
```

结果：通过，没有空白错误。本次未执行 Android 安装、`git add`、commit 或 push。

## 尚未覆盖

- 未在 Android 真机上实际拉动选择手柄或点击系统“复制”。
- 未在 Windows 实机验证 Ctrl+C；桌面共享实现已在 macOS JVM 目标编译通过。
