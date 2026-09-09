# 发送方消息菜单锚点验证

日期：2026-09-09

## 根因与覆盖范围

- 文件消息的实际卡片按消息方向靠左或靠右，但操作菜单原先与外层 `fillMaxWidth` 容器共享锚点。
- Material `DropdownMenu` 根据父布局边界定位，因此发送方文件菜单从聊天区域左边界计算，视觉上跳到页面中部。
- 普通文件、图片和视频卡片现在都在内容尺寸容器内承载操作菜单；全宽容器只负责消息方向对齐。

## 自动验证

```shell
./gradlew :app:shared:jvmTest :app:desktopApp:compileKotlin
```

结果：`BUILD SUCCESSFUL`。共享 JVM 测试和桌面编译通过。

```shell
./gradlew :app:shared:compileAndroidMain
```

结果：`BUILD SUCCESSFUL`。公共 Compose 代码通过 Android shared 编译，未安装 Android 应用。

## 桌面运行边界

尝试启动新的桌面调试实例时，已有 SubnetDrop 实例仍占用监听端口 `45892`，新实例以 `BindException` 退出。为避免
直接关闭用户正在使用的实例，本次没有强制终止旧进程；需要退出旧实例并重新运行后完成最终位置目视复测。
