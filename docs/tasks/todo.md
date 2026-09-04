# SubnetDrop 任务状态

## 当前计划：暂存区自动提交 Skill 重命名

- [x] 将 Skill 从 `staged-commit-message` 重命名为 `staged-auto-commit`，同步 frontmatter 与 UI 元数据。
- [x] 将显式调用 Skill 定义为一次普通提交的授权，自动生成 Conventional Commit 信息并立即提交暂存区。
- [x] 保留不自动暂存、不 amend、不跳过 hook、不 push 的安全边界。
- [x] 同步 AGENTS.md、README、经验与验证记录，并运行 Skill validator 和差异检查。

## 当前计划：键盘与最新消息底部锚定

- [x] 保持聊天页 imePadding 负责把输入区抬到键盘顶部。
- [x] 将时间线改为底部锚定的 reverse layout，让键盘压缩可视区时从顶部收缩。
- [x] 新消息使用稳定 key 滚动到反向列表索引 0，始终位于输入框上方。
- [x] 输入框聚焦时回到底部，覆盖用户此前停留在历史消息位置的情况。
- [x] 运行共享 JVM/Android 编译和差异检查，不安装 APK，并记录验证结果。

## 当前计划：聊天页系统栏与返回动效

- [x] 让透明状态栏的承载背景与聊天顶部栏统一为 Material surface。
- [x] 保留页面内容的 surfaceContainerLowest 层级，不让它延伸到系统状态栏区域。
- [x] 关闭紧凑布局返回首页时的 pop 与 predictive-pop 动画。
- [x] 运行共享 Android/JVM 编译和差异检查，不安装 APK，并记录验证结果。

## 历史计划：暂存区提交信息 Skill（已被 `staged-auto-commit` 取代）

- [x] 将 Skill 区分为只生成 message 和明确授权后 commit 两种模式。
- [x] commit 模式只使用已有暂存区，不自动暂存、amend、push 或跳过 hooks。
- [x] 同步 Skill UI 元数据、AGENTS.md 与 README 中的调用说明。
- [x] 运行 Skill validator 和差异检查，记录验证结果。

## 当前计划：聊天消息内容自适应

- [x] 让文字正文在最小高度气泡中垂直居中，多行内容仍按 padding 自然增长。
- [x] 移除文件消息固定最小宽度和内部强制填满宽度，按文件名、状态和操作图标自适应。
- [x] 运行共享 JVM/Android 编译与差异检查，不安装 Android APK，并记录验证结果。

## 当前计划：可配置文件接收与平台文件体验

- [x] 新增持久化文件设置：默认自动接收，可选开启每次接收确认，并可选择保存目录。
- [x] 调整文件协议：自动模式收到 offer 后直接准备写盘并开始传输，确认模式保留接受/拒绝流程。
- [x] 使用 FileKit 的目录选择和跨平台默认应用打开能力，文件卡片可点击打开已完成文件。
- [x] 保留接收中媒体的真实扩展名，明确系统打开不等于可靠渐进播放，并形成应用内预览设计。
- [x] 修正 Android edge-to-edge 系统栏样式，状态栏和导航栏透明并使用一致的内容背景。
- [x] 补充设置、协议分支和文件路径测试，运行共享、数据、网络、Android 与桌面验证，不安装 APK。
- [x] 更新规格、原理、README、经验与验证记录。

## 当前计划：聊天文件消息与 Android 键盘布局

- [x] 将文本消息和当前会话的文件传输合并为同一个按时间排序的聊天时间线。
- [x] 将文件卡片改为按收发方向对齐的消息项，保留进度、状态、错误和取消操作。
- [x] 将文字消息的发送/已读状态移到气泡下方，避免状态改变正文气泡布局。
- [x] 调整 Android IME inset 与窗口 resize，让输入栏始终贴在键盘顶部且消息列表自行缩放。
- [x] 补充时间线排序测试，运行共享 JVM 测试、network 回归和 Android/桌面编译。
- [x] 更新审查记录、经验与验证证据；不安装 Android APK。

## 当前计划：低延迟设备发现与在线状态

- [x] 将设备发现从平台 mDNS/DNS-SD 解析改为共享 UDP 组播公告，启动时按短间隔突发重试。
- [x] 收到候选设备后复用现有 Ktor WebSocket `PING/PONG` 做单播可达性确认，再写入 ONLINE。
- [x] 为已发现设备增加后台心跳和连续失败离线判定，发现工作不得阻塞 UI 或身份初始化。
- [x] 保持 SQLDelight 为设备状态唯一数据源，由 ViewModel 使用 `stateIn` 暴露 `StateFlow`。
- [x] 补充发现报文、可达性确认和在线超时测试，并运行 JVM/桌面与 Android 编译验证。
- [x] 更新架构、协议与局域网发现文档，记录验证证据和未覆盖的真机/Windows 边界。

## 当前计划：真机局域网启动卡死

- [x] 用真机安装包复现启动长期停留在 `Starting`，采集启动日志、页面截图和帧统计。
- [x] 用点击断点、无调试器复现和 Navigation3 1.1.1 本地源码确认 `NavEntry` 缓存旧 UI 快照。
- [x] 让缓存的 `NavEntry` 读取最新 `AppUiState`，并恢复底部导航的 `StateFlow`。
- [x] 撤回仅用于排查、未被根因证据支持的 Ktor 启动轮询代码。
- [x] 修复阻塞根因，重新安装到真机并验证服务完成启动、聊天与设置均可切换。
- [x] 运行 JVM 回归和 Android/桌面编译，将真机证据写入验证记录。

## 当前计划：启动交互与接收写盘复核

- [x] 确认底部导航状态更新不依赖运行时或密码学身份就绪。
- [x] 文件接收期间复用单个缓冲输出流，避免每个二进制帧重复打开和关闭临时文件。
- [x] 运行文件多分块回归、共享 UI 测试及 Android/桌面编译，不安装到 Android 设备。

## 当前计划：Android 首启解耦与高速文件通道

- [x] 移除运行时构造阶段剩余的重型 Ktor 初始化，保证身份准备时底部 Tab 可交互。
- [x] 将文件内容从 HPKE/Base64 JSON 分块改为同一 WebSocket 上的有界原始二进制流。
- [x] 取消逐块加密和逐块往返 ACK，保留接收确认、会话身份认证、大小上限和最终 SHA-256 校验。
- [x] 更新安全边界、协议规格、README 和架构文档，明确文件内容在局域网中是明文。
- [x] 补充文件传输回归并运行 JVM/桌面测试和 Android 编译，不安装到 Android 设备。

## 当前计划：Android 身份准备性能与可交互性

- [x] 将 Tink 全局注册移出 Android 主线程的 Koin 对象构造阶段。
- [x] 并行加载或生成 HPKE 与 Ed25519 身份密钥，保持安全存储和单次初始化语义。
- [x] 补充单元测试，验证并发请求只生成一份稳定身份。
- [x] 运行 network/shared 回归与 Android 编译，将结果写入 `docs/tasks/verification/`。

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
- [x] 将文件 Base64 JSON 分块升级为经过认证的有界二进制数据通道。
- [ ] 设计并实现本地消息正文加密、密钥轮换和 SQLDelight 数据迁移。
- [ ] 完成 macOS 签名、公证与发布身份下的 Keychain 隔离验证。
- [ ] 在 Windows 构建 MSI，验证防火墙、Credential Manager 和 Android/macOS/Windows 三端互通。
- [ ] 对外发布前选择并添加开源许可证。

## 审查记录

聊天页继续由外层 `imePadding` 把 Composer 抬到键盘顶部；时间线改为 `reverseLayout`，把倒序后的最新消息放在
索引 0，并使用 Bottom arrangement。这样键盘动画或窗口缩放减少列表高度时，viewport 从顶部方向收缩，底部最新
item 保持在 Composer 上方；新消息到达时也直接 `scrollToItem(0)`，不再依赖键盘动画完成时机。共享 JVM 测试、
Android shared 编译和桌面编译通过，未安装 APK；证据补充在
[`verification/2026-09-05-chat-timeline-ime.md`](./verification/2026-09-05-chat-timeline-ime.md)。

Android 系统状态栏本身保持透明，但其后方现在由根 Scaffold 的 `MaterialTheme.colorScheme.surface` 绘制，与
聊天 Header 使用完全相同的 token；Header 同时移除会叠加主题色的 tonal elevation，只保留阴影。页面正文的
`surfaceContainerLowest` 背景移到系统 inset 内部，避免再次露出两段颜色。紧凑布局的 Navigation3 同时把
`popTransitionSpec` 和 `predictivePopTransitionSpec` 设为无动画，顶部
返回按钮与系统返回手势都会立即回到首页。共享 JVM 测试、Android shared 编译和桌面编译通过，未安装 APK；
证据补充在 [`verification/2026-09-05-chat-timeline-ime.md`](./verification/2026-09-05-chat-timeline-ime.md)。

文字消息的最小高度此前只加在 `Surface`，内部 `Text` 没有对齐容器，因此短文本会按起始位置布局；现在由
`CenterStart` 的 `Box` 在保留水平/垂直 padding 的同时完成单行垂直居中，多行仍自然增高。文件消息移除了
260dp 固定最小宽度、内部 Row 的 `fillMaxWidth` 和 Column 的填满型 `weight`，改由文字与状态的 intrinsic width
决定实际宽度，440dp 只作为桌面端最大宽度保护。共享 JVM 测试、Android shared 编译和桌面编译通过，未安装 APK；
证据补充在 [`verification/2026-09-05-chat-timeline-ime.md`](./verification/2026-09-05-chat-timeline-ime.md)。

文件接收策略现在由持久化 `FileTransferSettingsRepository` 统一提供，默认
`requireIncomingConfirmation=false`。可信对端的合法 offer 在默认模式下会立即准备目标文件并返回签名接受决策；
开启“接收文件前确认”后才进入 `WAITING_FOR_ACCEPTANCE` 并显示接受/拒绝对话框。该设置不是 UI 临时状态，
Android 使用 SharedPreferences、桌面使用 Preferences，传输层在每个 offer 到达时读取当前值。

设置页通过 FileKit 选择保存目录。Android 对 SAF 目录创建 FileKit bookmark 以保留 URI 权限，桌面保存本地路径；
接收中数据写入保留原扩展名的隐藏临时目录，通过长度和 SHA-256 校验后才发布。发送侧源文件和已完成接收文件
可以从消息卡片调用系统默认应用打开，未校验完成的接收文件不会交给外部应用。

当前“流式传输”是边收边写磁盘，并不宣称“边收边播”。外部播放器对增长中文件没有统一读取契约，MP4 索引也
可能在文件尾部；可靠方案需要应用内播放器与可等待尚未到达字节的受控数据源，已记录在
[`design-docs/progressive-media-preview.md`](../design-docs/progressive-media-preview.md)。Android 两个系统栏改为透明，
并关闭 Android 10+ 三键导航强制对比色遮罩。数据、网络和共享 JVM 测试、Android Debug APK、桌面编译全部通过；
按用户要求未安装 APK。证据见
[`verification/2026-09-05-file-settings-system-bars.md`](./verification/2026-09-05-file-settings-system-bars.md)。

聊天页不再维护独立的固定高度文件面板。展示层使用带命名空间稳定 key 的统一时间线，将 SQLDelight 文字消息
和当前会话 `FileTransfer` 状态按 `createdAt` 排序；文件卡片根据方向左右对齐，传输进度、失败原因和取消动作
保留在卡片内。文件传输进度仍只保留当前进程会话，本轮没有伪装成可跨重启恢复的数据库消息。

文字消息气泡现在只测量正文，发送、未读、已读和失败重试状态位于气泡下方。Android 输入栏的
`imePadding` 从内部 Row 移到聊天页外层，Activity 使用 `adjustResize`；根 Scaffold 的系统栏 padding 被显式
消费，避免系统导航栏和 IME 底部高度重复计算。共享/网络 JVM 测试、Android Debug APK 和桌面编译通过；
按用户要求没有安装 Android APK，因此真实手机键盘贴合仍需后续真机交互确认。证据见
[`verification/2026-09-05-chat-timeline-ime.md`](./verification/2026-09-05-chat-timeline-ime.md)。

设备发现已从 Android NSD / 桌面 JmDNS 的“发现后再解析”改为共享 UDP 主动公告。两端在启动后的
100 ms、500 ms、2 s 发出公告，接收端使用报文源 IP 与声明端口进行 Ktor WebSocket PING；只有收到身份路由
匹配的 PONG 才通过 `DiscoveryEvent.Found` 写入 SQLDelight 为 ONLINE。启动时还会立即并发探测数据库中的已知
地址，已确认设备每 5 s 探测一次，连续 3 次失败后写入 OFFLINE。PING 两端只读取轻量 `DeviceProfile`，没有
把密码学身份生成重新放回启动路径。

设备列表仍由 SQLDelight `Flow` 驱动，`SubnetDropViewModel.toUiState` 使用 `stateIn` 转成只读 `StateFlow`，
Compose 没有直接读取网络层的临时状态。JVM 回归、Android 编译和桌面编译通过；按用户约束没有安装 Android
APK。新协议的 Android/macOS 真实双机延迟与 Windows 防火墙行为仍明确保留为未验证边界，详见
[`verification/2026-09-05-udp-discovery.md`](./verification/2026-09-05-udp-discovery.md)。

Android 紧凑布局的问题来自 Navigation3 对 `NavEntry` 的缓存：主页路由不变时，`entryProvider` 不会重新执行，
其中直接捕获的 `AppUiState` 因而停留在旧快照。修复后 `NavEntry` 捕获稳定的 `State<AppUiState>`，并在自身
组合中读取最新值；底部栏继续使用 `StateFlow`，未与运行时启动状态耦合。PGFM10 真机冷启动 705 ms，2 秒后
显示“已在线”，聊天和设置均可切换，45892 端口处于监听状态，日志无 ANR、输入超时或崩溃。完整 JVM 回归、
Android 编译和桌面编译通过；详细证据见
[`verification/2026-09-05-android-nav-state.md`](./verification/2026-09-05-android-nav-state.md)。

此前单元级复核确认底部导航状态不依赖 `RuntimeState`，但没有覆盖 Navigation3 对 `NavEntry` 的缓存行为；
该结论已由本次真机验证补全。文件接收端在接受提议时只打开一次 512 KiB 缓冲输出流，整次传输复用并在
完成、取消或失败时关闭，消除了每个分块一次文件打开/关闭的固定开销。

Android 启动现在只加载 `DeviceProfile`，不再生成 HPKE/Ed25519 密钥；密码学身份延迟到首次配对或加密聊天。
Ktor `HttpClient` 也改为首次发起请求时才创建，因此底部 Tab 不再以加密身份就绪为前置条件。

文件内容改为单 WebSocket 上的 512 KiB 原始二进制帧，不加密、不做 Base64/JSON 转换、不逐块等待 ACK。
发送和接收同时流式计算 SHA-256，避免传输前后额外扫描整个文件。文件控制帧仍由 Ed25519 签名，
但文件原文对局域网观察者可见，这是用户明确选择的性能取舍。完整回归和 Android 编译通过；本轮未安装 APK。

Android 身份准备不再于 Koin 创建 `TinkSecureMessageCodec` 时注册 Tink，密钥生成改为在后台
并行执行，Android Keystore 和加密偏好读写则显式使用 IO 调度器。底部导航本身没有禁用逻辑，
之前的不可交互是首次密码学初始化抢占 UI 导致。并发身份请求回归确认仍只持久化两份稳定私钥，
完整 JVM/桌面回归与 Android 编译通过。遵循当前约束，本轮没有安装 APK 到手机，真机首启耗时留待下次
明确授权安装时测量。

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
