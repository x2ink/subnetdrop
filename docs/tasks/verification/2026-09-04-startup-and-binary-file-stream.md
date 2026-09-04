# Android 启动与二进制文件流验证

> 当时未执行真机安装，底部导航的单元级结论不足以覆盖 Navigation3 entry 缓存行为。后续真机定位、修复与
> 验收见 [`2026-09-05-android-nav-state.md`](./2026-09-05-android-nav-state.md)。

## 启动边界

- `SubnetDropRuntime.start()` 只请求 `DeviceProfile`，不请求包含 HPKE/Ed25519 公钥的 `PublicIdentity`。
- `LocalIdentityService.getProfile()` 回归测试确认安全存储写入次数为 0；后续请求 `get()` 才写入两份私钥。
- Ktor 客户端为线程安全的延迟初始化，不在 ViewModel/Koin 构造路径中创建。
- 底部导航状态不读取 `RuntimeState` 或 `PublicIdentity`；最终实现继续使用 `StateFlow`，由 Compose 生命周期感知地收集。

## 文件通道

- 文件提议、决策、流开始、流完成和取消帧使用 Ed25519 签名。
- 文件内容使用 512 KiB 有界 `Frame.Binary`，不执行 HPKE、Base64 或 JSON 转换。
- 单个上传 WebSocket 顺序发送全部分块，仅在开始和完成时等待 ACK。
- 双方边传输边计算 SHA-256，接收方在长度和签名摘要一致后才发布临时文件。
- 接收端整次传输复用一个 512 KiB 缓冲输出流，仅在完成、取消或失败时关闭。
- JVM 集成测试传输 1,300,000 字节，覆盖多二进制分块并逐字节核对结果。

## 命令与结果

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest :app:shared:jvmTest \
  :app:desktopApp:compileKotlin :app:androidApp:compileDebugKotlin
```

结果：`BUILD SUCCESSFUL in 2s`，60 个 task，15 个执行，45 个 up-to-date。

`git diff --check` 通过。遵循现有约束，本轮未向 Android 手机安装 APK，因此真机触摸与实际 Wi-Fi 吞吐仍需在新包上验收。
