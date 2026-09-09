# 文字消息操作与多选验证

日期：2026-09-09

## 覆盖范围

- Android 长按与桌面右键文字气泡的统一操作菜单。
- 完整复制、部分文字选择、单条/多条本地删除、在线受信设备转发。
- 多选顶部计数、文字消息左侧选择圆点和底部转发/删除操作栏。
- SQLDelight 会话限定删除、转发顺序、单条失败隔离和中英日资源完整性。

## 自动验证

```shell
./gradlew :core:jvmTest :data:jvmTest :app:shared:jvmTest \
  :app:desktopApp:compileKotlin :app:shared:compileAndroidMain
```

结果：`BUILD SUCCESSFUL`。领域用例、SQLDelight、共享 UI 纯逻辑与本地化测试通过；Android 共享源集和
Compose Desktop 编译通过。

```shell
./gradlew :app:shared:compileKotlinJvm :app:shared:compileAndroidMain
```

结果：`BUILD SUCCESSFUL`。Compose 新剪贴板 API 的 Android `ClipData` 与桌面 AWT `Transferable` 适配均通过编译，
未新增剪贴板弃用警告。

```shell
./gradlew :network:jvmTest \
  --tests ink.x2.subnetdrop.network.transport.SubnetDropTransportTest.senderProgressNeverRunsAheadOfReceiverConfirmedBytes \
  :app:shared:jvmTest :app:desktopApp:compileKotlin
```

结果：`BUILD SUCCESSFUL`。完整网络套件首次运行时，既有的
`senderProgressNeverRunsAheadOfReceiverConfirmedBytes` 时序测试波动失败；未修改网络实现或断言，随后原样单独重跑通过。

```shell
git diff --check
```

结果：通过，无空白错误。未安装 Android 应用。

## 审查结论

- 文字消息删除同时使用当前会话 ID 和消息 ID 限定，超过 500 条时事务内分批执行。
- 转发目标只包含 `ONLINE + TRUSTED` 设备；每条转发重新生成身份、时间和送达状态，并复用现有加密发送用例。
- 多条转发使用连续毫秒时间戳保持顺序；一条失败不会阻止后续消息建立真实成功/失败状态。
- 文件卡片没有混入文字复制、部分选中和多选语义；文件转发与文件记录删除留待独立生命周期规格。
- 本轮没有真实 Android 设备安装或手势回归；平台事件接入复用了项目已有、已用于设备菜单的 Android
  `combinedClickable` 与桌面 `PointerButton.Secondary` 实现。
