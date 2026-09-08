# 中英日三语适配验证

日期：2026-09-08

## 范围

- Compose Multiplatform Resources 默认英语、简体中文和日语资源。
- 首页、设置、聊天、配对、文件接收、文件输入错误、状态与 Snackbar 文案。
- Android shared、macOS/Windows 共用桌面代码和 JVM 回归。

## 自动化验证

```shell
./gradlew :app:shared:jvmTest \
  --tests ink.x2.subnetdrop.resources.LocalizationTest \
  --no-configuration-cache
```

结果：`BUILD SUCCESSFUL`。测试确认三套资源均有 118 个同名、非空资源键，代表性英语、中文和日文文案正确，
动态文件大小及接收确认文案的格式占位符一致。测试直接解析 XML，避免在无图形环境的 JVM test worker 中初始化
Skiko。

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest \
  :app:shared:jvmTest :app:desktopApp:compileKotlin \
  :app:shared:compileAndroidMain --no-configuration-cache --stacktrace
```

结果：`BUILD SUCCESSFUL`，52 个任务中 10 个执行、42 个复用缓存。核心、数据、网络和共享 UI 的 JVM 测试通过，
桌面代码与 Android shared 源集编译通过。未安装 Android 应用。

```shell
rg -n --glob '*.kt' --glob '!**/*Test.kt' \
  '"[^"\\n]*[一-龥ぁ-んァ-ン][^"\\n]*"' app/shared/src
git diff --check
```

结果：共享生产 Kotlin 中没有中文或日文用户文案字面量，差异无空白错误。产品名、`GiB`、设备名、文件名、路径、
聊天正文与底层系统异常详情按规格不翻译。

## 未覆盖边界

- 本轮未在三种系统语言下逐个平台执行视觉走查；Windows 安装包仍需在 Windows Runner 或真机验证。
- 编译保留 `ChatScreen.kt` 既有 `quadraticBezierTo` 弃用警告，与本次国际化无关。
