# GitHub Actions 桌面测试包修复验证

## 历史失败范围

公开的 [Build test packages run 33881533832](https://github.com/x2ink/subnetdrop/actions/runs/33881533832)
基于 `beaa84e`：Android Debug APK 与 macOS Intel DMG 成功，macOS Apple Silicon DMG 和 Windows x64 MSI
在打包步骤退出。公开 Check Run 只提供 `Process completed with exit code 1`，原始 job log 需要仓库登录态，不能据此
把失败归因到某个未经证实的异常。

本轮针对可验证的薄弱点调整：

- Android、macOS matrix 与 Windows 分成独立 job，不再串联等待 Android。
- macOS 在打包前输出 Java、`jpackage`、CPU 架构、Xcode CLI 与 `hdiutil` 信息。
- Windows 使用当前预装 WiX Toolset 3.14 的 `windows-2022`，显式把 WiX 加入 PATH 并验证 `candle.exe`、
  `light.exe` 与 `jpackage`。
- 原生打包使用 `--no-configuration-cache --stacktrace --info`；失败时上传 problems report 与 Compose 打包参数。
- Windows 先上传 `createDistributable` 生成的便携 ZIP，再执行 MSI 打包，两类产物相互独立。

## 本机验证

### Apple Silicon DMG

```shell
./gradlew :app:desktopApp:packageDmg \
  --rerun-tasks --no-configuration-cache --stacktrace --info
```

结果：通过，`BUILD SUCCESSFUL in 39s`。Gradle 使用 Azul JDK `21.0.11` ARM64 的 `jpackage`，生成：

```text
app/desktopApp/build/compose/binaries/main/dmg/SubnetDrop-1.0.0.dmg
```

文件大小约 139 MiB；应用入口经 `file` 检查为 `Mach-O 64-bit executable arm64`。

### Gradle 任务与工作流结构

```shell
./gradlew :app:desktopApp:tasks --all --no-configuration-cache
ruby -e 'require "yaml"; YAML.load_file(".github/workflows/build-test-packages.yml")'
git diff --check
```

结果：通过。任务列表包含 `createDistributable`、`packageDmg` 和 `packageMsi`；YAML 可解析，job 集合为
`android`、`macos`、`windows`，差异无空白错误。

## 待目标 Runner 验证

- 当前 macOS 主机不能生成或执行 Windows EXE/MSI，因此 ZIP 目录结构、MSI 安装和 SmartScreen 行为必须由
  Windows job 验证。
- Intel DMG 与修订后的 Apple Silicon DMG 需要在对应 GitHub Runner 重新执行。
- 本轮没有提交或推送，远端 Actions 尚未运行新工作流；不能将本机验证表述为远端已修复通过。
