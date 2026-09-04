# SubnetDrop 任务状态

## 当前计划：GitHub Actions 测试安装包

- [x] 新增可手动触发、也可由 `v*` 标签触发的 GitHub Actions 工作流。
- [x] 配置 Ubuntu Runner 运行 JVM 回归、Android lint，并生成 Debug APK。
- [x] 配置 Windows x64 Runner 生成未签名 MSI。
- [x] 分别配置 macOS Apple Silicon 与 Intel Runner 生成未签名 DMG。
- [x] 将四类测试安装包作为保留 14 天的 Actions Artifacts 上传，缺失产物时让任务显式失败。
- [x] 更新 README 与构建手册，说明触发方式、下载位置、未签名安装限制和未验证边界。
- [x] 完成本地 YAML、Gradle 任务与差异检查，并在 `docs/tasks/verification/` 留痕。

## 当前计划：文档信息架构整理

- [x] 将根 README 更新为当前产品、技术栈、技术方案、截图和平台适配状态首页。
- [x] 将 `docs/ARCHITECTURE.md` 更新为全部长期文档的统一入口。
- [x] 新建 `docs/technical-principles/`，说明局域网发现、P2P、端到端加密、可靠消息、本地存储、
  文件传输和 KMP 平台边界。
- [x] 修正 v1 规格中过时的“文件传输非目标”和缺失帧类型。
- [x] 删除一次性依赖整改/重命名/Skill 记录，以及仍展示旧品牌或旧导航的截图。
- [x] 检查本地链接、代码围栏、旧品牌引用和 diff 格式，不运行 Android task。

## 产品技术待办

- [ ] 将配对、聊天和文件会话职责从当前传输实现中拆分，保持协议行为不变。
- [ ] 为 FileKit Android provider 路径补充真机回归和可测试的适配层覆盖。
- [ ] 将文件 Base64 JSON 分块升级为经过认证的有界二进制数据通道。
- [ ] 设计并实现本地消息正文加密、密钥轮换和 SQLDelight 数据迁移。
- [ ] 完成 macOS 签名、公证与发布身份下的 Keychain 隔离验证。
- [ ] 在 Windows 构建 MSI，验证防火墙、Credential Manager 和 Android/macOS/Windows 三端互通。
- [ ] 对外发布前选择并添加开源许可证。

## 审查记录

GitHub Actions 测试包工作流只读仓库并使用开源基础缓存，不配置签名密钥，也不创建 Release。Android job
先执行 JVM 回归、lint、APK 和桌面编译，成功后才并行启动三个桌面 job，避免基础验证失败时浪费 Runner。
本机已复现 Android/验证 job 并重新构建 macOS DMG；Windows x64 与 macOS Intel 仍需工作流推送后在对应
GitHub Runner 首次执行，不能把“工作流已配置”误写成“目标平台已运行验收”。

文档现在分成三层：`technical-principles/` 保存长期原理，`spec/` 保存可测试的 v1 约束，`tasks/` 只记录当前
计划与验证。根 README 采用当前版本表中的真实依赖版本，明确区分“已实现”“已在目标平台验证”和“仍待发布
验证”，并展示经过账户名脱敏的当前 macOS 截图。

删除内容仅包括已经完成的一次性整改/重命名/Skill 记录和旧品牌截图；当前协议规格、文件传输规格与最新平台
验证均保留。所有本地 Markdown 链接可解析，代码围栏成对，`git diff --check` 通过。本轮未执行 Android 构建、
安装、模拟器、真机或 ADB 操作。
