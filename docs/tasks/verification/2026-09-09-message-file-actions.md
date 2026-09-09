# 文字与文件消息操作验证

日期：2026-09-09

## 覆盖范围

- 文字与文件消息双击、长按、桌面右键菜单入口。
- 文件名复制、完成文件转发、终态文件本地记录删除及文字/文件混合多选。
- 转发目标 Material 3 底部弹层及首页附近设备项复用。
- SQLDelight 文件消息删除、终态实时传输移除，以及普通桌面路径转发输入准备。

## 自动验证

执行：

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest :app:shared:jvmTest \
  :app:desktopApp:compileKotlin :app:shared:compileAndroidMain :app:androidApp:assembleDebug
```

结果：`BUILD SUCCESSFUL`，118 个可执行任务中 94 个执行、1 个命中缓存、23 个为最新状态。

新增回归覆盖：

- 删除文件消息只作用于指定会话和 ID，不影响文字消息。
- 移除终态实时 transfer 后，数据库历史仍按设计独立存在。
- 仅完成且存在本地路径的文件具备转发语义，普通桌面路径可还原为新的 `LocalFile`。
- 转发设备筛选仍只返回 `ONLINE + TRUSTED`。

## 平台边界

- Android Debug APK 已构建但未安装，符合本次“不安装 Android 应用”的约束。
- Android 长按、`content://` 再转发和桌面双击/右键的最终手感仍需在真实设备交互验收；编译与纯逻辑测试不能替代
  指针、触摸及系统文件提供方验证。
