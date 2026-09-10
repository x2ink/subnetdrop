# Android 文件传输通知进度验证

日期：2026-09-10

## 变更范围

- 前台 Service 收集 `FileTransferService.transfers`，不新增独立上传计数。
- 单文件通知展示发送/接收方向、文件名、累计字节、总字节、百分比和系统进度条。
- 多文件并行传输按所有活动文件的字节汇总；同时存在发送和接收时使用中性“传输”文案。
- 通知状态以 500ms 周期采样；全部活动文件结束后恢复局域网可见通知。
- Android 通知文案覆盖英语、简体中文和日语。

## 自动验证

执行：

```shell
./gradlew :app:androidApp:testDebugUnitTest :app:androidApp:assembleDebug
```

结果：`BUILD SUCCESSFUL`，共 104 个 task。验证覆盖：

- 无活动任务时返回空闲通知状态。
- 单个发送文件按接收端确认字节计算进度和百分比。
- 多文件并行汇总字节、混合方向以及异常上报字节钳制。
- `checkDebugAarMetadata`、资源格式化、Kotlin 编译、Dex 和 APK 打包通过。

`git diff --check` 通过。

## 尚未验证

本轮没有安装 Android 应用。需要在真机确认通知栏在单文件、多文件、发送、接收、锁屏和后台状态下的实际布局，
以及 Android 13+ 拒绝通知权限后仅在系统活动应用入口展示服务的系统行为。
