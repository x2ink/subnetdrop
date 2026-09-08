# 应用内语言切换验证

## 范围

- 应用语言领域模型、持久化端口和 Multiplatform Settings 适配器。
- 设置页“跟随系统 / 简体中文 / English / 日本語”单选弹窗。
- Android 与 JVM 平台 Locale 应用，以及 Compose Resources 根节点重组。
- 三语资源完整性、README、架构和本地化规格。

## 自动验证

定向验证：

```shell
./gradlew :data:jvmTest \
  --tests ink.x2.subnetdrop.data.MultiplatformAppSettingsRepositoryTest \
  :app:shared:compileKotlinJvm \
  :app:shared:compileAndroidMain \
  --no-configuration-cache --stacktrace
```

结果：`BUILD SUCCESSFUL`。覆盖默认跟随系统、语言选择持久化以及非法存储值回退。

完整回归：

```shell
./gradlew :core:jvmTest :data:jvmTest :network:jvmTest :app:shared:jvmTest \
  :app:desktopApp:compileKotlin :app:shared:compileAndroidMain \
  --no-configuration-cache --stacktrace
```

结果：`BUILD SUCCESSFUL`，共 52 个任务。英语、简体中文和日语资源均为 126 个同构 key；资源测试包含语言设置
代表性文案。编译仅报告聊天气泡已有的 `quadraticBezierTo` 弃用提示，本次代码无新增警告。

弹窗按钮布局调整后的最终 UI 回归：

```shell
./gradlew :app:shared:jvmTest :app:desktopApp:compileKotlin \
  :app:shared:compileAndroidMain --no-configuration-cache
```

结果：`BUILD SUCCESSFUL`，共 46 个任务。

```shell
git diff --check
```

结果：通过，无空白错误。

## 交互验证边界

尝试通过 `./gradlew :app:desktopApp:run --no-configuration-cache` 启动第二个桌面实例时，本机已有 SubnetDrop 进程占用
服务端口，第二实例以 `BindException: Address already in use` 退出。没有终止用户已有实例，因此本轮未完成鼠标操作下
的三种语言逐项视觉走查，也没有安装 Android 应用；Android 真机和 Windows 的即时切换仍需在目标平台验收。
