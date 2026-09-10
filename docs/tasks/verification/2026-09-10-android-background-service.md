# Android 后台局域网服务验证

日期：2026-09-10

## 变更范围

- Android 使用不可导出的 `connectedDevice` 前台 Service 持有共享 `SubnetDropRuntime`。
- Activity 在用户可见时启动服务并在 Android 13+ 请求通知权限。
- 删除 `ProcessLifecycleOwner.onStop` 停止 Runtime 的逻辑及不再使用的 lifecycle-process 依赖。
- 服务通知和通知渠道提供英语、简体中文、日语资源。
- macOS/Windows 继续由桌面应用进程持有 Runtime，窗口失焦或最小化不触发停止。

## 自动验证

执行：

```shell
./gradlew :app:androidApp:compileDebugKotlin :app:androidApp:processDebugMainManifest \
  :app:desktopApp:compileKotlin :app:shared:jvmTest :network:jvmTest
```

结果：`BUILD SUCCESSFUL`。

合并 Manifest 静态检查确认存在：

- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE`
- `android.permission.POST_NOTIFICATIONS`
- `SubnetDropService`，`foregroundServiceType="connectedDevice"`，`stopWithTask="false"`

`git diff --check` 通过。构建仅出现 `ChatScreen` 既有 `quadraticBezierTo` 弃用警告，与本次生命周期变更无关。

首次只执行 Kotlin 编译时没有触发 `checkDebugAarMetadata`，直接依赖的 AndroidX Core 1.19.0 与项目 AGP 9.0.1、
compileSdk 36 不兼容。通知权限检查已改用 minSdk 30 原生 `Context.checkSelfPermission` 并移除该直接依赖，随后增加
以下安装前完整打包验证：

```shell
./gradlew :app:androidApp:assembleDebug
```

结果：`BUILD SUCCESSFUL`，共 94 个 task；`checkDebugAarMetadata`、Manifest 合并、资源处理、Dex 和 APK 打包均通过。
唯一额外输出是既有 `libandroidx.graphics.path.so` 无法 strip、按原样打包的提示，不影响构建结果。

## 尚未验证

本轮遵循项目约束，没有安装 Android 应用或执行 ADB。以下场景仍需真机验证：

1. 传输中按 Home、锁屏和切换应用，双端进度持续并最终完成。
2. 从最近任务划除后服务通知、局域网发现和活动传输保持运行。
3. Android 13+ 分别允许和拒绝通知权限；拒绝时服务仍运行，并可在系统活动应用界面看到。
4. 用户从系统“强制停止/活动应用停止”后连接终止，重新打开应用后服务恢复。
5. 大文件传输中系统销毁 Service 时，三秒清理上限不产生 ANR 或残留临时文件。
