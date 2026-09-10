# 设置页 GitHub 入口验证

## 范围

- 在共享设置页底部增加“关于”标题和 SubnetDrop GitHub 仓库入口。
- 使用共享资源中的 GitHub 官方 Primer Octicons Mark，Android 与桌面复用同一图标。
- 点击入口后通过 Compose `LocalUriHandler` 交给系统默认浏览器打开
  `https://github.com/x2ink/subnetdrop`。
- 补齐英文、简体中文和日文资源，并在 URI 打开失败时显示应用内提示。

## 验证

执行：

```shell
./gradlew :app:desktopApp:compileKotlin :app:androidApp:compileDebugKotlin
./gradlew :app:desktopApp:compileKotlin
git diff --check
```

结果：

- Android 与桌面 Kotlin 编译成功。
- 替换新入口使用的弃用图标后，桌面复编译成功；剩余警告来自已有聊天气泡 Path API。
- `git diff --check` 通过。
- 按项目约束未安装 Android；Android Intent 和桌面默认浏览器的实际跳转仍需目标设备交互验收。
