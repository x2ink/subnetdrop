# SubnetDrop 构建手册

本文汇总项目日常开发、测试、安装和打包命令。除 Windows 特别说明外，命令均在项目根目录执行。

## 环境要求

- Gradle Wrapper 9.1.0；始终使用仓库内的 `./gradlew` 或 `gradlew.bat`。
- JDK 21。Gradle Daemon toolchain 已固定为 Azul JDK 21。
- Android SDK 36；Android 最低支持 API 30。
- macOS 构建 DMG，Windows 构建 MSI，Linux 构建 DEB。Compose Desktop 安装包不能跨操作系统构建。
- Android 真机需要开启开发者选项和 USB 调试，并通过 `adb devices -l` 确认为 `device` 状态。

Windows PowerShell/CMD 将下文的 `./gradlew` 替换为 `gradlew.bat`。

## Git hooks

克隆仓库后执行一次：

```shell
bash scripts/setup-git-hooks.sh
```

之后使用 `git commit` 进入 Conventional Commit 交互选择。共享 hook 位于 `.githooks/`，本地激活副本位于
`.git/hooks/`；修改共享 hook 后需要重新运行安装脚本同步。

## 环境与工程检查

```shell
# 确认 Gradle 与 JVM
./gradlew --version

# 查看参与构建的模块
./gradlew projects

# 查看全部 Gradle 任务
./gradlew tasks --all

# 为 Android Studio / IntelliJ 准备 KMP 模型
./gradlew :app:shared:prepareKotlinIdeaImport
```

修改 `settings.gradle.kts`、版本目录或插件配置后，在 Android Studio 中执行
`File > Sync Project with Gradle Files` 重新导入。

## 推荐的完整校验

```shell
./gradlew \
  :core:jvmTest \
  :data:jvmTest \
  :network:jvmTest \
  :app:shared:jvmTest \
  :app:androidApp:assembleDebug \
  :app:androidApp:lintDebug \
  :app:desktopApp:compileKotlin
```

在当前操作系统额外生成桌面安装包：

```shell
./gradlew :app:desktopApp:packageDistributionForCurrentOS
```

## Android

### 编译与安装

```shell
# Debug APK
./gradlew :app:androidApp:assembleDebug

# 由 Gradle 安装到当前连接设备
./gradlew :app:androidApp:installDebug

# 或使用 ADB 覆盖安装并保留应用数据
adb install -r app/androidApp/build/outputs/apk/debug/androidApp-debug.apk

# 启动应用
adb shell am start -n ink.x2.subnetdrop/.MainActivity

# 停止应用
adb shell am force-stop ink.x2.subnetdrop
```

Debug APK 输出位置：

```text
app/androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

### Release 产物

```shell
# Release APK
./gradlew :app:androidApp:assembleRelease

# Release Android App Bundle
./gradlew :app:androidApp:bundleRelease
```

正式发布前需要配置独立的 release 签名，并验证 R8/ProGuard；当前 release 构建没有开启代码压缩。

### Android 校验

```shell
# Debug 静态检查
./gradlew :app:androidApp:lintDebug

# Release 静态检查
./gradlew :app:androidApp:lintRelease

# 共享模块 Android 本地测试
./gradlew :app:shared:testAndroidHostTest

# 在已连接真机/模拟器上运行共享模块设备测试
./gradlew :app:shared:connectedAndroidDeviceTest
```

## 桌面端

### 开发运行

```shell
# 普通开发运行
./gradlew :app:desktopApp:run

# Compose Hot Reload 开发运行
./gradlew :app:desktopApp:hotRun

# 编译桌面 Kotlin 源码
./gradlew :app:desktopApp:compileKotlin

# 创建可直接运行的应用目录
./gradlew :app:desktopApp:createDistributable

# 运行已创建的应用目录
./gradlew :app:desktopApp:runDistributable
```

### 当前系统安装包

```shell
./gradlew :app:desktopApp:packageDistributionForCurrentOS
```

### 各平台安装包

以下命令必须在对应操作系统执行：

```shell
# macOS
./gradlew :app:desktopApp:packageDmg

# Windows
gradlew.bat :app:desktopApp:packageMsi

# Linux
./gradlew :app:desktopApp:packageDeb
```

Release 优化版本：

```shell
# 当前系统 Release 安装包
./gradlew :app:desktopApp:packageReleaseDistributionForCurrentOS

# macOS Release DMG
./gradlew :app:desktopApp:packageReleaseDmg

# Windows Release MSI
gradlew.bat :app:desktopApp:packageReleaseMsi

# Linux Release DEB
./gradlew :app:desktopApp:packageReleaseDeb

# 当前系统独立 Uber JAR
./gradlew :app:desktopApp:packageReleaseUberJarForCurrentOS
```

桌面产物统一位于：

```text
app/desktopApp/build/compose/binaries/main/
app/desktopApp/build/compose/binaries/main-release/
```

## 分层测试

```shell
# 领域用例
./gradlew :core:jvmTest

# SQLDelight 持久化
./gradlew :data:jvmTest

# 加密、配对与网络传输
./gradlew :network:jvmTest

# 共享状态与 UI 逻辑
./gradlew :app:shared:jvmTest

# 所有 JVM 测试
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest :app:shared:jvmTest

# 只运行端到端聊天与加密文件传输测试
./gradlew :network:jvmTest --tests ink.x2.subnetdrop.network.transport.SubnetDropTransportTest
```

测试报告位于各模块的 `build/reports/tests/`。

桌面端接收文件默认保存到 `~/Downloads/SubnetDrop`。Android 使用应用专属外部 Downloads 目录，卸载应用前
应自行备份其中需要保留的文件。

## 清理与重建

```shell
# 清理全部模块产物
./gradlew clean

# 清理后重新构建 Android Debug APK
./gradlew clean :app:androidApp:assembleDebug

# 清理后重新构建当前系统桌面安装包
./gradlew clean :app:desktopApp:packageDistributionForCurrentOS

# 停止 Gradle Daemon
./gradlew --stop
```

只有依赖缓存确实损坏时才使用 `--refresh-dependencies`：

```shell
./gradlew :app:androidApp:assembleDebug --refresh-dependencies
```

## 依赖与故障诊断

```shell
# 查看整个工程依赖
./gradlew dependencies

# 查看指定模块依赖
./gradlew :app:shared:dependencies

# 定位特定依赖的选择来源
./gradlew :app:shared:dependencyInsight \
  --dependency koin-core \
  --configuration jvmRuntimeClasspath

# 输出详细构建日志
./gradlew :app:androidApp:assembleDebug --info

# 输出异常堆栈
./gradlew :app:androidApp:assembleDebug --stacktrace

# 不复用配置缓存进行诊断
./gradlew :app:androidApp:assembleDebug --no-configuration-cache
```

## 国内镜像

- Gradle 分发包：腾讯云镜像，配置在 `gradle/wrapper/gradle-wrapper.properties`。
- Google、Maven Central、Gradle Plugin：阿里云镜像，配置在 `settings.gradle.kts`。
- npm、Yarn、Node：项目级 `.npmrc`、`.yarnrc` 和 Gradle 属性。

不要在项目构建脚本中临时添加国外仓库；缺失依赖时应先确认国内镜像是否已经同步。
