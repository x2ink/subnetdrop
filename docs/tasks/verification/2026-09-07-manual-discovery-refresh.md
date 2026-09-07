# 首页手动刷新附近设备验证

## 行为

- “附近设备”页右下角显示 Material 3 刷新悬浮按钮，设置页不显示。
- 点击后立即发送一次 UDP 组播公告，并立即探测所有已知设备端点，不等待 5–30 秒心跳或离线退避。
- 刷新不停止发现 Socket、不重启聊天服务、不清空 SQLDelight 数据；正在进行的同设备探测仍会去重。
- 刷新动作通过 Runtime 与 ViewModel 执行，成功触发后提示“已重新发起设备发现”。

## 自动验证

```shell
./gradlew :network:jvmTest :app:shared:jvmTest \
  :app:shared:compileAndroidMain :app:desktopApp:compileKotlin \
  --no-configuration-cache --stacktrace
```

首次结果：`BUILD SUCCESSFUL in 4s`，47 个任务中 24 个执行、23 个为 up-to-date。ViewModel 最终提示文案接入后
重跑同一命令，结果为 `BUILD SUCCESSFUL in 2s`，47 个任务中 8 个执行、39 个为 up-to-date。已有 `ChatScreen` 的
`quadraticBezierTo` 弃用警告与本次修改无关，没有新增编译警告或测试失败。

新增发现测试先让已知设备进入离线退避，确认普通 `probeTargets()` 暂时返回空列表，再验证手动刷新目标仍立即包含
该设备。共享 JVM、Android common 源集和桌面入口均完成编译，因此两种 HomeScreen 调用路径都已覆盖签名检查。

```shell
./gradlew :app:desktopApp:run --no-configuration-cache
```

结果：桌面应用成功启动并进入运行态，完成 smoke test 后主动停止进程。

本轮没有安装 Android 应用；按钮在真机上的触控、Snackbar 和真实双设备发现时延仍需后续交互验证。
