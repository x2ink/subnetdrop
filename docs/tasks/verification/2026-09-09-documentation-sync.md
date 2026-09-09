# README 与长期文档现状同步

日期：2026-09-09

## 同步范围

- 根 README 的 Android 平台状态反映 macOS 开启 VPN 后恢复 Android 在线的实机证据，同时保留最新版双端首次
  发现、FileKit 和后台策略的待验证边界。
- 消息操作规格按平台说明文件复制：桌面使用系统原生文件列表，Android 使用 URI 或路径降级。
- 文件传输规格和技术原理补充桌面剪贴板文件粘贴、统一预检与发送前确认；普通文字粘贴保持原生行为。
- 媒体原理修正为图片 4:3 缩略图、视频 16:9 首帧；构建手册修正 Android MediaStore 公共下载目录和明文但
  经过认证的文件数据面措辞。
- 跨平台架构表移除重复 source-set 行；项目导航约束改为底部仅保留“附近设备 / 设置”。
- `docs/tasks/todo.md` 只保留当前同步结果和未完成路线图，已完成工作的详细证据继续保存在 verification 目录。

## 文档验证

```shell
git diff --check
```

结果：通过，无空白错误。

使用只读脚本检查 `README.md`、`BUILD_GUILD.md`、`AGENTS.md` 和 `docs/**/*.md`：

- 所有相对 Markdown 链接均指向存在的文件或目录；
- 所有 Markdown 代码围栏成对；
- `docs/tasks/todo.md` 只保留一个“当前计划”；
- 现行文档中不再存在“图片和视频统一 4:3”“Android 应用专属外部 Downloads”“附近设备 / 聊天 / 设置”或
  “加密文件传输测试”等已知过时表述。

结果：全部通过。本轮只有文档变更，未运行 Gradle、安装应用或操作 Android 设备。
