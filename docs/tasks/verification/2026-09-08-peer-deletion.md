# 附近设备删除与历史清理验证

日期：2026-09-08

## 验证范围

- Android 设备项长按、桌面设备项右键和共用 Material 3 删除菜单/确认 Dialog。
- 保留历史和同时删除历史两种 SQLDelight 事务分支。
- 设备信任、发现跟踪、配对候选和内存文件传输状态清理。
- v1/v2 数据库到新增 `is_hidden` 字段的 v3 schema 迁移。

## 自动化验证

```shell
./gradlew :data:jvmTest \
  --tests ink.x2.subnetdrop.data.SqlDelightPersistenceTest \
  --no-configuration-cache
```

结果：`BUILD SUCCESSFUL`。覆盖保留历史时隐藏 peer、清除信任并在重新发现后恢复显示，以及删除历史时清除
conversation、文字消息、文件消息和 peer；旧数据库迁移后文字与文件消息仍可读取。

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest \
  :app:shared:jvmTest :app:desktopApp:compileKotlin \
  :app:shared:compileAndroidMain --no-configuration-cache --stacktrace
```

最终结果：`BUILD SUCCESSFUL`，52 个任务中 31 个执行、21 个复用缓存。发现测试覆盖忘记 peer 后停止退避探测及
后续公告重新发现；传输测试覆盖双方进程内文件卡清理。共享 JVM 测试同时确认中、英、日资源均有 118 个同构键。
桌面与 Android shared 源集编译通过，没有安装 Android 应用。

```shell
git diff --check
```

结果：通过，无空白错误。

## 修正记录与边界

- 第一轮仅有旧 schema fixture 失败：fixture 在移除 v3 字段后仍用 v3 生成查询写入。调整为先用当前查询造数，
  再降级为真实 v1 结构后，迁移与删除测试通过；产品代码没有采用绕过迁移的兼容分支。
- 本轮未在 Android 真机执行长按，也未在 Windows 主机执行右键视觉验收；Android shared 和 macOS/Windows 共用
  Desktop 源码已编译，真实输入手势仍需目标设备交互验证。
- 编译保留 `ChatScreen.kt` 既有 `quadraticBezierTo` 弃用警告，与本功能无关。
