# Android 身份准备优化验证

> 后续方案已进一步收紧启动边界：应用启动只读取 `DeviceProfile`，完全不生成 HPKE/Ed25519 密钥。
> 密钥只在首次配对或加密聊天需要 `PublicIdentity` 时创建；最终状态与验证见
> [`2026-09-05-android-nav-state.md`](./2026-09-05-android-nav-state.md)。

## 范围

- Tink 注册从 Koin 对象构造阶段延迟到首次密钥加载。
- HPKE/X25519 和 Ed25519 密钥加载或生成在 `Dispatchers.Default` 并发执行。
- Android Keystore 与加密 `SharedPreferences` 读写在 `Dispatchers.IO` 执行，并缓存进程内包装密钥。

## 自动化验证

```shell
./gradlew :network:jvmTest --tests ink.x2.subnetdrop.network.crypto.TinkSecureMessageCodecTest \
  :app:shared:jvmTest :app:androidApp:compileDebugKotlin
```

结果：通过。新增回归用例并发请求 8 次本机身份，确认公钥始终一致且安全存储只写入两次。

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest :app:shared:jvmTest \
  :app:desktopApp:compileKotlin :app:androidApp:compileDebugKotlin
```

结果：`BUILD SUCCESSFUL in 2s`，60 个 task，10 个执行，50 个处于 up-to-date。

`git diff --check` 通过。

## 边界

本轮遵循不往 Android 设备安装的要求，因此没有覆盖新 APK 或清除手机上已有身份数据。
真机首次密钥生成的绝对耗时尚未重测；该过程不再属于应用启动路径，并继续在后台调度器执行。
