# MediaStore 文件大小校验修复

日期：2026-09-07

## 根因

Android 公共下载目标在 `IS_PENDING=1` 期间通过 FileKit `PlatformFile.size()` 查询 `OpenableColumns.SIZE`。
部分内容提供方不会在文件发布前同步刷新该元数据，完整写入的文件因此可能被误报为 `0`、`-1` 或旧长度，触发
`Received file size does not match offer`。

## 修复

- 接收完成时显式要求 session 累计接收字节数等于 offer 大小，并在错误中输出期望值和实际值。
- Android MediaStore 使用 `ParcelFileDescriptor.statSize` 读取底层文件实际长度，不再读取 pending 元数据。
- `statSize` 无法提供长度时，不把未知值当成不匹配；写入仍必须成功 flush/close，接收计数和 SHA-256 仍必须通过。

## 验证

```shell
./gradlew :network:jvmTest \
  --tests ink.x2.subnetdrop.network.storage.FileKitIncomingFileStoreTest \
  --tests ink.x2.subnetdrop.network.transport.SubnetDropTransportTest
```

结果：通过。

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest :app:shared:jvmTest \
  :app:desktopApp:compileKotlin :app:androidApp:assembleDebug
```

结果：通过，Android Debug APK 完成打包，未安装到设备。

```shell
git diff --check
```

结果：通过，无空白错误。

## 未覆盖

未在产生原截图的 Android 真机上重新发送文件。真机复测应确认完成文件出现在公共 `Download/SubnetDrop`，文件卡片
显示完成且可调用系统应用打开。
