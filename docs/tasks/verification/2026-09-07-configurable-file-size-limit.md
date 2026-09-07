# 可配置单文件大小上限验证

日期：2026-09-07

## 覆盖范围

- 默认单文件上限保持 10 GiB，设置页允许保存 1–1024 GiB 的整数值。
- 上限通过 Multiplatform Settings 在 Android 和桌面持久化，不修改 SQLDelight schema。
- 发送方按本机设置预检，接收方按本机设置独立校验 offer。
- Android provider 文件在复制到应用缓存前按当前上限检查元数据。
- 1 TiB 是用户设置和协议共同的绝对上限。
- Android 仅构建 Debug APK，没有安装到设备或执行 ADB 操作。

## 自动化验证

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest :app:shared:jvmTest \
  :app:desktopApp:compileKotlin
```

结果：`BUILD SUCCESSFUL`。数据测试覆盖 10 GiB 默认值、25 GiB 重启恢复以及低于/高于合法范围的拒绝；网络测试
使用长度大于 1 GiB 的稀疏文件，验证发送端上限会在发送 offer 前阻断，接收端更小的上限会在内容传输前拒绝 offer。
共享设置 UI 和桌面入口编译通过。

```shell
./gradlew :app:androidApp:assembleDebug
```

结果：连续两次 `BUILD SUCCESSFUL`，第二次稳定复用增量与配置缓存。仅生成 Debug APK，没有安装到 Android 设备。

```shell
git diff --check
```

结果：通过，没有空白错误。本次未执行 `git add`、commit 或 push。

## 尚未覆盖

- 未真实传输接近 1 TiB 的文件；自动化只验证上限的持久化、元数据校验和拒绝路径。
- 未在 Android、macOS 和 Windows 原生设置页面手动输入边界值，当前由共享 Compose 编译和领域测试覆盖。
