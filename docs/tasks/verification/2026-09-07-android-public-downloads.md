# Android 公共下载目录验证

日期：2026-09-07

## 变更边界

- Android 默认接收位置改为 MediaStore 公共 `Download/SubnetDrop`，不再创建新的
  `Android/data/ink.x2.subnetdrop/files/Download/SubnetDrop` 接收文件。
- 接收期间使用 pending 下载项，长度与 SHA-256 校验成功后发布；失败、取消和服务停止清理未完成项。
- 不提供旧默认私有路径迁移；开发期验证从清空应用数据后的最终默认状态开始。SAF 自定义目录和桌面目录能力不变。
- 本次没有修改 UDP 设备发现与在线状态实现。

## 自动验证

首次联合编译发现 Android 常量初始化不符合 Kotlin `const val` 规则，改为普通只读值后重新验证。

```shell
./gradlew :data:jvmTest :network:jvmTest \
  --tests ink.x2.subnetdrop.network.transport.SubnetDropTransportTest
```

结果：通过。覆盖设置持久化及既有文件协议、完整性、取消和并行传输回归。

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest :app:shared:jvmTest \
  :app:desktopApp:compileKotlin :app:androidApp:compileDebugKotlin
```

结果：通过。新增覆盖 FileKit 临时目标发布、同名文件不覆盖、取消删除，以及公共目录设置标签。

```shell
./gradlew :app:androidApp:assembleDebug
```

结果：通过，Debug APK 打包成功但未安装。

```shell
git diff --check
```

结果：通过，无空白错误。

## 未执行项

- 按项目约束未安装 Android APK，也未在真机实际接收文件。
- 真机验收时应确认文件管理器可在 `Download/SubnetDrop` 看到完成文件，点击聊天文件消息能由系统应用打开，取消传输
  后公共下载目录不存在半成品。
