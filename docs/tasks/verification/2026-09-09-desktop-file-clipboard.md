# 桌面文件剪贴板与消息交互验证

日期：2026-09-09

## 覆盖范围

- 消息项移除 hover、按压与涟漪视觉反馈，同时保留双击、长按、右键、打开和多选语义。
- 桌面文件消息复制使用原生文件列表剪贴板，不再复制文件名字符串。
- 桌面输入框粘贴文件时复用批量文件预检，并在用户确认后才进入现有发送链路。
- 普通文字粘贴不被文件处理器拦截；Android 不注册桌面文件列表粘贴快捷键。

## 自动验证

```shell
./gradlew :app:shared:jvmTest :app:desktopApp:compileKotlin :app:shared:compileAndroidMain
```

结果：`BUILD SUCCESSFUL`。测试覆盖 AWT 原生文件列表载荷可被现有普通文件解析器读取；共享 UI、桌面文件剪贴板
实现和 Android 安全降级均编译通过。

```shell
./gradlew :app:shared:jvmTest --tests ink.x2.subnetdrop.ui.PlatformFileDropTest
```

结果：`BUILD SUCCESSFUL`。使用隔离的 AWT Clipboard 完成原生文件列表写入、读取和普通文件校验闭环，不触碰用户的
系统剪贴板。

```shell
./gradlew :app:androidApp:assembleDebug
```

结果：`BUILD SUCCESSFUL`，94 个可执行任务中 15 个执行、79 个为最新状态；只构建 APK，未安装 Android 应用。

## 目标机边界

- 自动测试不写入开发机系统剪贴板，避免覆盖用户当前内容。Finder、Windows Explorer 和第三方程序之间的真实复制/
  粘贴互操作仍需分别在 macOS 和 Windows 目标机视觉验收。
