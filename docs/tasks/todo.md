# SubnetDrop 任务状态

本文件只保留当前文档同步结果和仍未完成的产品技术事项。已完成工作的过程与证据统一保存在
[`verification/`](./verification/)；架构、长期原理和冻结规格分别从 [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md)、
[`technical-principles/`](../technical-principles/) 和 [`spec/`](../spec/) 进入。

## 当前计划：文档现状同步

- [x] 同步文件复制、桌面文件粘贴、媒体比例和 Android 公共下载目录说明。
- [x] 修正 README 平台验证状态、跨平台架构表和 AGENTS 导航约束。
- [x] 清理已完成但仍标为“当前计划”的历史内容，仅保留未完成路线图与本轮审查。
- [x] 检查 Markdown 本地链接、空白差异和文档关键词一致性，记录验证结果。

### 审查

- 根 README 的依赖版本、HTTP/1.1 Streaming 数据面、图片 4:3/视频 16:9、桌面文件粘贴和双栏导航均与代码一致。
- 文件操作规格不再把桌面原生文件复制描述为“复制文件名”；Android 的 URI/路径降级边界已明确。
- 文件传输规格和技术原理已补充桌面剪贴板文件确认流程，普通文字粘贴不受影响。
- 构建手册已改为 Android MediaStore 公共 `Download/SubnetDrop`，不再误导为应用专属外部目录。
- 项目开发约束与当前产品一致：底部主导航只保留“附近设备 / 设置”，聊天从受信设备项进入。
- 全部本地 Markdown 链接和代码围栏有效，差异无空白错误；本轮仅修改文档，没有运行构建或安装应用。

## 产品技术待办

- [ ] 将配对、聊天和文件会话职责从当前传输实现中拆分，保持协议行为不变。
- [ ] 为 FileKit Android provider 路径补充真机回归和可测试的适配层覆盖。
- [ ] 设计并实现本地消息正文加密、密钥轮换和 SQLDelight 数据迁移。
- [ ] 在相同网络和文件上记录 `iperf3`、旧 WebSocket 数据面与当前 HTTP Streaming 数据面的阶段吞吐。
- [ ] 使用最新 Android 与 macOS 构建复测首次发现、文件传输和开启 VPN 时的双端互通。
- [ ] 在 macOS 和 Windows 目标机验证文件复制/粘贴、发送消息菜单锚点及桌面视频首帧兼容范围。
- [ ] 在 GitHub Actions 复跑 macOS 双架构 DMG 与 Windows MSI/便携 ZIP，并记录目标 Runner 结果。
- [ ] 完成 macOS 签名、公证与发布身份下的 Keychain 隔离验证。
- [ ] 在 Windows 验证安装包、防火墙、Credential Manager 和 Android/macOS/Windows 三端互通。
- [ ] 对外发布前选择并添加开源许可证。
