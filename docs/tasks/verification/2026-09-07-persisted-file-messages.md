# 文件消息持久化验证

日期：2026-09-07

## 覆盖范围

- 文件传输终态及本地路径写入 SQLDelight，并在数据库关闭重开后恢复。
- 桌面历史 `user_version=0` 的逻辑 v1 数据库升级到 schema v2，保留原有文字消息。
- 发送、接收和拒绝文件后保存终态快照。
- 数据库历史与实时传输按 transfer ID 去重，完成文件缺失时判定为“已失效”。
- JVM/桌面和 Android 共享代码可编译；未执行 Android 安装或 ADB 操作。

## 验证结果

```shell
./gradlew :data:verifyCommonMainChatDatabaseMigration
```

结果：`BUILD SUCCESSFUL`，SQLDelight v1→v2 migration 与当前 CREATE schema 一致。

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest :app:shared:jvmTest \
  :app:desktopApp:compileKotlin
```

结果：`BUILD SUCCESSFUL`。覆盖领域端口、数据库重开/旧库迁移、网络终态持久化、共享时间线和桌面编译。

```shell
./gradlew :network:jvmTest --tests ink.x2.subnetdrop.network.transport.SubnetDropTransportTest
```

结果：`BUILD SUCCESSFUL`。文件自动接收、多分块传输、拒绝和终态数据库适配器调用通过。

```shell
./gradlew :app:androidApp:assembleDebug
```

结果：`BUILD SUCCESSFUL`，仅生成 Debug APK，没有安装到设备。编译仍报告聊天气泡自定义 Path 中既有
`quadraticBezierTo` 弃用警告，不影响本次结果。

```shell
git diff --check
```

结果：通过，没有空白错误。任务开始前已有的 4 个暂存文件保持暂存状态；本次没有执行 `git add`、commit 或 push。
