# 图片与视频消息预览验证

## 变更范围

- 图片消息使用不带气泡尖角的 4:3 缩略图卡片，视频使用 16:9 首帧卡片；准备、等待、传输、失败和已失效保持
  对应比例，完成且本地文件存在才显示预览。
- 图片由 Coil 3 + FileKit 读取；Android 视频由 Coil Video 读取首帧；macOS/Windows JVM 使用 JCodec 读取首帧。
- 预览点击继续调用既有 `FileKit.openFileWithDefaultApplication`，没有引入应用内播放器或修改传输协议、数据库 schema。
- 阿里云镜像缺少 `filekit-coil-jvm` JAR，因此在同一依赖解析区增加仅匹配 `io.github.vinceglb` 的华为云国内镜像。

## 自动验证

```shell
./gradlew :app:shared:compileKotlinJvm :app:shared:compileAndroidMain --no-configuration-cache
```

结果：`BUILD SUCCESSFUL`。共享 UI、JVM JCodec 适配器、Android Coil Video 适配器和平台 ImageLoader 均编译通过。

```shell
./gradlew :app:shared:dependencyInsight \
  --configuration jvmCompileClasspath \
  --dependency org.jetbrains.compose.ui:ui \
  --no-configuration-cache
```

结果：Compose UI 最终解析为项目既有 `1.11.1`；Coil/FileKit 没有把它升级到 1.12。

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest \
  :app:shared:jvmTest :app:desktopApp:compileKotlin \
  --no-configuration-cache
```

结果：`BUILD SUCCESSFUL`。新增测试覆盖 MIME 优先级、通用 MIME 的扩展名回退、大小写和非媒体文件隔离；完整 JVM
回归与桌面编译通过。

```shell
./gradlew :app:androidApp:assembleDebug --no-configuration-cache
```

结果：`BUILD SUCCESSFUL`，Android Debug APK 打包通过；按用户要求没有安装到 Android 设备。

```shell
./gradlew :app:shared:jvmTest :app:desktopApp:compileKotlin :app:shared:compileAndroidMain
```

结果：`BUILD SUCCESSFUL`。媒体卡去除左右尖角后，共享测试、桌面和 Android 编译通过。

同一命令在图片缩略图限制与视频 16:9 调整后再次执行，结果为 `BUILD SUCCESSFUL`。新增纯逻辑测试验证图片 4:3、
视频 16:9；图片 Coil 请求显式限制为 720×540。

```shell
git diff --check
```

结果：通过，无空白错误。

## 未覆盖的目标机验收

- 未在 Android 真机验证公共下载目录的 `content://` 图片和视频首帧视觉结果。
- 未在 Windows 主机验证 H.264/MP4 的 JCodec 首帧和外部打开；macOS 与 Windows 共用 JVM 实现，但不能据此宣称
  Windows 已完成视觉验收。
- HEVC、AV1 或非 MP4/QuickTime 桌面视频可能回退到视频占位；文件仍保持完成状态并可交给系统应用打开。
