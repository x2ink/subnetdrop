# GitHub Actions 测试安装包验证

## 范围

本轮新增 `.github/workflows/build-test-packages.yml`，目标是在不配置发布证书的情况下生成以下测试产物：

| 平台 | Runner | Gradle 任务 | Artifact 内容 |
|---|---|---|---|
| Android | `ubuntu-24.04` x64 | `:app:androidApp:assembleDebug` | Debug APK |
| macOS | `macos-15` arm64 | `:app:desktopApp:packageDmg` | Apple Silicon DMG |
| macOS | `macos-15-intel` x64 | `:app:desktopApp:packageDmg` | Intel DMG |
| Windows | `windows-2025` x64 | `:app:desktopApp:packageMsi` | MSI |

工作流由手动操作或 `v*` 标签触发，只授予 `contents: read`，不会创建 GitHub Release、提交代码或安装应用。
每个 Artifact 保留 14 天，路径不匹配时使用 `if-no-files-found: error` 显式失败。

## 本地验证

YAML 语法检查：

```shell
ruby -e "require 'yaml'; YAML.load_file('.github/workflows/build-test-packages.yml')"
```

结果：通过。

Gradle 任务发现：

```shell
./gradlew :app:desktopApp:tasks --all
```

结果：通过；确认存在 `packageDmg`、`packageMsi`、`packageDeb` 和当前系统聚合任务。

复现 Actions Android/验证 job：

```shell
./gradlew \
  :core:jvmTest \
  :data:jvmTest \
  :network:jvmTest \
  :app:shared:jvmTest \
  :app:androidApp:lintDebug \
  :app:androidApp:assembleDebug \
  :app:desktopApp:compileKotlin \
  --stacktrace
```

结果：通过，`BUILD SUCCESSFUL in 1m 8s`，163 个可执行任务中 141 个执行、22 个为最新状态。生成 APK，
未安装到设备，未调用 ADB。

macOS arm64 DMG：

```shell
./gradlew :app:desktopApp:packageDmg --stacktrace
```

结果：通过，`BUILD SUCCESSFUL`；当前 DMG 产物路径与工作流 glob 一致。

## 未验证边界

- 工作流尚未推送到 GitHub，因此 `workflow_dispatch`、标签触发和 Artifact 上传仍待首次远程运行确认。
- Windows MSI 与 macOS Intel DMG 不能在当前 Apple Silicon macOS 主机实跑，必须由对应 GitHub Runner 验证。
- CI 打包成功不等同于安装后功能验收；Windows 防火墙、Credential Manager、macOS Keychain、mDNS 与三端互通
  仍需目标设备测试。
- 测试包没有正式发布签名，Windows SmartScreen 和 macOS Gatekeeper 可能显示安全确认。
