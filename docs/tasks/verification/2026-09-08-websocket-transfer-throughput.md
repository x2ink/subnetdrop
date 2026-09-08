# WebSocket 文件吞吐代码优化验证

## 变更

- 保持协议规定的 512 KiB 二进制分块与 4 MiB 接收确认窗口。
- 文件帧有界队列从 2 帧调整为 8 帧，每条连接最多约 4 MiB 排队数据。
- 发送端直接读入确认窗口内预分配的 8 个数组，不再额外建立同尺寸 BufferedInputStream；仅在收到有序确认后
  复用数组，最后一个非整块仍使用精确长度数组。
- 接收目标从 RawSink 改为复用的 buffered Sink，直接写入帧字节，不再为每个分块创建临时 Buffer。

## 验证

文件传输专项：

```shell
./gradlew :network:jvmTest \
  --tests ink.x2.subnetdrop.network.transport.SubnetDropTransportTest \
  --tests ink.x2.subnetdrop.network.storage.FileKitIncomingFileStoreTest \
  --no-configuration-cache --stacktrace
```

结果：`BUILD SUCCESSFUL`。覆盖多窗口内容完整性、发送端不超过接收端确认进度、三文件并行、批量失败隔离、
SHA-256/长度终态和临时文件发布。

完整回归：

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest :app:shared:jvmTest \
  :app:desktopApp:compileKotlin :app:shared:compileAndroidMain \
  --no-configuration-cache --stacktrace
```

结果：`BUILD SUCCESSFUL`，共 52 个任务。

为保证 buffered Sink 的尾段也在进度确认前提交给底层 RawSink，增加 `emit()` 后再次运行：

```shell
./gradlew :network:jvmTest \
  --tests ink.x2.subnetdrop.network.transport.SubnetDropTransportTest \
  --tests ink.x2.subnetdrop.network.storage.FileKitIncomingFileStoreTest \
  :network:compileAndroidMain --no-configuration-cache
```

结果：`BUILD SUCCESSFUL`，共 15 个任务。

```shell
git diff --check
```

结果：通过。

## 未验证边界

本轮没有安装 Android 应用，也没有占用用户设备做传输。自动测试证明协议行为、内容和构建没有回归，但不能证明
真实 Wi-Fi 下的 MB/s 提升幅度。需要在同一电脑、手机、Wi-Fi 频段和 VPN 状态下对比修改前后吞吐，并以 `iperf3`
TCP 结果作为网络上限。后续架构取舍见
[文件传输吞吐升级方案](../../design-docs/file-transfer-throughput-options.md)。
