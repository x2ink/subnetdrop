# 多文件并行传输与双端进度验证

日期：2026-09-07

## 覆盖范围

- FileKit 一次最多选择 50 个文件，并通过共享 ViewModel 提交为一个批次。
- 所有批次共享最多 3 个活跃发送槽位，排队文件仍显示为 `PREPARING` 消息。
- 每个文件使用独立 WebSocket 会话，单文件失败不取消正常的兄弟任务。
- 发送端显示已提交字节，接收端显示已写盘字节，完成确认后双方均为 100%。
- Android 仅生成 Debug APK，没有安装到设备，也没有执行 ADB 操作。

## 自动化验证

```shell
./gradlew :network:jvmTest --tests ink.x2.subnetdrop.network.transport.SubnetDropTransportTest \
  :app:shared:jvmTest :app:desktopApp:compileKotlin
```

结果：`BUILD SUCCESSFUL`。新增 5 文件用例验证首批恰有 3 个并发 offer，接受后剩余两个接力发送；发送端与
接收端均得到 5 个 `COMPLETED`、100% 进度、5 条终态文件消息和逐字节一致的文件。失败隔离用例验证一个不存在的
源文件不会取消同批次的正常文件。

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest :app:shared:jvmTest \
  :app:desktopApp:compileKotlin
```

结果：`BUILD SUCCESSFUL`，完整 JVM 回归与桌面编译通过。

```shell
./gradlew :data:verifyCommonMainChatDatabaseMigration
```

结果：`BUILD SUCCESSFUL`，本次未修改 schema，现有 SQLDelight migration 仍一致。

```shell
./gradlew :app:androidApp:assembleDebug
```

结果：`BUILD SUCCESSFUL`，仅构建 APK。编译仍报告聊天气泡旧 Path 代码中的 `quadraticBezierTo` 弃用警告，
与本次传输功能无关。

```shell
git diff --check
```

结果：通过，没有空白错误。本次未执行 `git add`、commit、push 或 Android 安装。

## 尚未覆盖

- 没有在两台真实设备上测量 Wi-Fi 吞吐、系统文件选择器的多选上限表现或双方进度动画。
- 本次不包含断点续传；连接断开后文件仍需重新发送，断点能力留给后续可恢复传输协议版本。
