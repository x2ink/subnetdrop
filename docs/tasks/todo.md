# SubnetDrop 任务状态

## 当前计划：附近设备删除与历史清理

- [x] 定义忘记设备语义：清除信任并从附近列表移除，可选删除文本与文件消息，但不删除已保存实体文件。
- [x] 新增 SQLDelight 设备隐藏字段、事务化删除查询与数据库迁移，保证 StateFlow 自动刷新。
- [x] Android 长按设备、桌面右键设备时打开删除菜单，再由确认 Dialog 选择是否同时删除聊天记录。
- [x] 删除前取消该设备的活跃文件传输，并从发现在线跟踪器移除，避免残留状态立即覆盖删除结果。
- [x] 补充数据、发现与共享 UI 测试，更新三语资源、架构、README 和验证记录。
- [x] 最多进行 3 轮编译/测试修正，运行完整 JVM、桌面和 Android shared 验证，不安装 Android 应用。

## 当前计划：中英日三语适配

- [x] 使用 Compose Multiplatform Resources 建立英文默认资源、简体中文和日文资源，按系统语言自动选择。
- [x] 迁移首页、聊天页、设置、配对/文件弹窗、无障碍描述和文件状态中的硬编码用户文案。
- [x] 将 ViewModel 通知改为稳定的本地化消息模型，将文件输入校验改为稳定错误类型后由 UI 翻译。
- [x] 保留底层网络和操作系统异常详情原文，并用当前语言的错误前缀包装。
- [x] 增加三语资源完整性与代表性文案测试，更新 README、规格、架构和验证记录。
- [x] 最多进行 3 轮编译/测试修正，运行完整 JVM、Android shared 和桌面验证，不安装 Android 应用。

## 当前计划：桌面聊天页拖放发送文件

- [x] 复用现有文件预检与批量发送入口，避免拖放形成第二套传输链路。
- [x] 使用 Compose Desktop 系统拖放 API 接收普通文件，拒绝目录、非文件数据和超过批次上限的输入。
- [x] 在聊天页拖入期间显示明确的投放提示，Android 保持原有行为。
- [x] 补充桌面拖放输入测试、聊天 UI 规格和验证记录。
- [x] 运行共享 JVM 测试、Android shared 编译和桌面编译，不安装 Android 应用。

## 当前计划：首页手动刷新附近设备

- [x] 为发现端口增加立即刷新语义：发送一次组播公告并立即探测所有已知端点。
- [x] 通过 Runtime 和 ViewModel 暴露刷新动作，不重启聊天服务或清空数据库列表。
- [x] 在 HomeScreen 附近设备页右下角加入 Material 3 刷新悬浮按钮，并接入紧凑/桌面布局。
- [x] 补充退避期间强制探测测试，更新发现规格、技术原理和验证记录。
- [x] 运行网络、共享 UI、桌面和 Android 编译验证，不安装 Android 应用。

## 当前计划：移除未采纳的媒体方案

- [x] 确认代码与依赖中不存在播放器、媒体数据源或接收中打开入口。
- [x] 删除未采纳的媒体预览设计文档及架构入口。
- [x] 清理 README、规格、技术原理、任务、经验和验证记录中的未来方案表述。
- [x] 保留普通文件分块落盘、完整性校验和完成后系统打开能力，并执行引用与差异检查。

## 当前计划：GitHub Actions 桌面测试包修复

- [x] 将 macOS Intel、macOS Apple Silicon 与 Windows 从 Android job 解耦，分别输出可定位的打包日志。
- [x] 固定目标系统打包工具检查，并关闭原生打包任务的 configuration cache。
- [x] Windows 同时生成便携 ZIP（内含可执行 EXE）与 MSI 安装包，并分别上传 Artifact。
- [x] 更新 README 与构建手册，明确产物形式、Runner 和未签名测试限制。
- [x] 本机复现 Apple Silicon DMG、校验 Gradle 任务与工作流语法，记录 Windows Runner 待验证边界。

## 当前计划：MediaStore 完成校验

- [x] 接收完成时显式校验协议累计字节数，错误信息包含期望值和实际值。
- [x] Android MediaStore 使用文件描述符读取真实落盘大小，不依赖 pending 条目的元数据。
- [x] 文件提供方无法报告大小时，使用写入计数、流关闭结果与 SHA-256 完成校验，避免错误失败。
- [x] 补充存储目标测试并运行文件传输、完整 JVM、桌面和 Android 构建验证。

## 当前计划：Android 公共下载目录

- [x] 将 Android 默认接收位置设为公共 `Download/SubnetDrop`。
- [x] 使用 MediaStore pending 条目接收文件，校验成功后公开，失败或取消时删除未完成条目。
- [x] 保留用户通过 SAF 选择自定义目录的最终产品能力，不提供旧默认路径迁移。
- [x] 让设置页显示可读的公共目录名称，完成文件继续通过系统应用打开。
- [x] 补充存储目标测试，运行 JVM、桌面和 Android 构建，不安装 Android 应用。

## 当前计划：离线设备自动恢复

- [x] 将已知设备端点与当前在线状态解耦，首次探测失败后仍保留单播重试目标。
- [x] 连续失败达到阈值时只发布离线事件，不删除端点；恢复可达后重新发布在线事件。
- [x] 避免未验证的变更地址探测失败污染已确认端点的健康状态。
- [x] 补充首次失败恢复、连续失败恢复和端点变更隔离测试。
- [x] 更新发现规格、技术原理和验证记录，运行网络、桌面与 Android 构建，不安装 Android 应用。

## 当前计划：文件传输进度同步与吞吐优化

- [x] 将发送端展示进度改为接收端确认的落盘进度，使用有界窗口确认而不是逐块 ACK。
- [x] 限制 Ktor WebSocket 收发队列容量，避免大文件被提前堆入内存并让 TCP 背压真正生效。
- [x] 将接收端文件写入与 SHA-256 计算移出 Ktor 调度线程，并降低 StateFlow 高频更新开销。
- [x] 补充进度一致性、窗口校验、文件完整性和并行传输回归测试。
- [x] 更新协议、技术原理和验证记录，运行网络专项、完整 JVM、桌面与 Android 构建，不安装 Android 应用。

## 当前计划：VPN 开启时保持局域网发现

- [x] 排除 point-to-point 与虚拟 VPN 网卡，只在真实 IPv4 LAN 网卡加入和发送组播。
- [x] Android 发现会话启动时将本应用进程绑定到当前 Wi-Fi Network，停止时恢复系统默认网络。
- [x] 为网卡选择规则补充纯逻辑测试，并验证无 Wi-Fi/绑定失败时安全降级。
- [x] 更新发现规格、技术边界与经验，运行网络、桌面及 Android 构建验证，不安装 Android 应用。

## 当前计划：跨平台文字消息选择与复制

- [x] 仅为文字消息正文启用 Compose 官方选择容器，不让送达状态和文件卡片进入选择范围。
- [x] 验证 Android 长按选择/系统复制入口与桌面拖选、Ctrl/Cmd+C 所需代码可跨平台编译。
- [x] 更新聊天 UI 规格、经验与验证记录，不安装 Android 应用。

## 当前计划：可配置单文件大小上限

- [x] 将固定 10 GiB 上限建模为跨平台持久化设置，默认 10 GiB，可配置范围 1–1024 GiB。
- [x] 在设置页提供带单位和范围校验的数值输入，并通过 ViewModel/领域端口保存。
- [x] 发送方使用本机上限预检，接收方使用本机上限校验 offer，保留 1 TiB 协议绝对上限。
- [x] 补充设置默认值、持久化、非法值和收发双方独立上限测试。
- [x] 更新文件协议与技术文档，运行 JVM/桌面及 Android 编译验证，不安装 Android 应用。

## 当前计划：多文件并行传输与双端进度

- [x] 定义批量选择上限、全局并行度、单文件失败隔离和双端进度语义。
- [x] 使用 FileKit Multiple 模式选择多文件，并一次性交给 ViewModel/领域端口。
- [x] 在传输服务中建立全部文件消息，使用有界并发并行发送且不因单文件失败取消其他任务。
- [x] 保持发送端按已提交字节、接收端按已写盘字节更新各自文件卡片，完成 ACK 后收敛到 100%。
- [x] 补充多文件并行、失败隔离和双端终态测试，并通过共享 UI 编译验证多选接入。
- [x] 运行 JVM/桌面、协议专项、SQLDelight 迁移和 Android 编译验证，不安装 Android 应用。
- [x] 更新架构、README、文件传输原理与验证记录。

## 当前计划：文件消息持久化

- [x] 定义文件消息数据库字段、终态持久化规则、活跃传输去重规则和本地文件失效语义。
- [x] 新增 SQLDelight 文件消息表、查询与 v1→v2 迁移，并让 Android/桌面旧数据库自动执行迁移。
- [x] 通过 ChatRepository/UseCase/StateFlow 向聊天页提供当前会话的历史文件消息。
- [x] 在发送、接收、拒绝、取消或失败进入终态时保存文件消息，并保留本地最终路径。
- [x] 合并历史文件消息与当前传输，同 ID 优先展示当前传输；文件不存在时显示“已失效”并禁止打开。
- [x] 补充数据库重开/迁移、传输持久化、时间线去重与文件失效测试，运行完整 JVM/桌面和 Android 编译验证。
- [x] 更新 README、架构、文件传输原理和验证记录，且不改变现有暂存区内容。

## 当前计划：首页导航与聊天返回稳定性

- [x] 删除首页“聊天”Tab 及其只为会话列表服务的 presentation/UI 状态和回调。
- [x] 将首页系统状态栏与顶部区域固定为白色，并保持聊天页为不透明背景。
- [x] 移除聊天页顶部栏和输入栏的阴影/色调海拔效果。
- [x] 返回首页时保留退出条目的文字消息快照，并按会话 ID 过滤时间线，避免只剩文件消息或跨会话闪烁。
- [x] 补充时间线过滤回归，运行共享 JVM、Android 与桌面编译并记录验证结果。

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
- [x] 保留 Android provider 文件的原始扩展名和 MIME 类型。
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

附近设备项现在通过同一个 Material 3 操作菜单提供删除能力：Android 使用 `combinedClickable` 长按，桌面使用
Compose Desktop 的 Secondary `PointerMatcher` 响应右键，二者都不会替代普通点击。删除菜单之后还有确认 Dialog，
“同时删除聊天记录”默认不勾选；Dialog 明确说明实体文件仍会保留，并完整提供中、英、日文案。

删除链路先清理目标设备的配对候选、活跃/终态内存传输状态和发现跟踪代次，再执行 SQLDelight 事务。保留历史时
peer 变为隐藏、离线、未配对，conversation 与文本/文件消息保留；再次收到公告会取消隐藏但要求重新配对。勾选历史
时显式删除消息、文件消息、conversation 与 peer，不依赖平台 SQLite 外键开关。v2→v3 迁移新增 `is_hidden`，旧库
迁移、两种删除分支、发现忘记/重新发现和传输内存清理测试均通过；完整 JVM、桌面和 Android shared 回归通过，
没有安装 Android 应用。证据见
[`verification/2026-09-08-peer-deletion.md`](./verification/2026-09-08-peer-deletion.md)。

共享 UI 已迁移到 Compose Multiplatform Resources：默认 `values` 为英语，`values-zh` 为简体中文，
`values-ja` 为日语，共 118 个同构资源键，Android、macOS 和 Windows 均按系统语言选择，不支持的语言回退英语。
首页、聊天、设置、配对、文件确认、传输状态、Snackbar 和无障碍描述不再依赖中文 Kotlin 字面量；ViewModel
只保存稳定消息键和格式参数，文件输入使用稳定错误类型，底层平台异常详情仍保留原文便于诊断。资源完整性测试、
完整 JVM 回归、桌面编译与 Android shared 编译通过，未安装 Android 应用。证据见
[`verification/2026-09-08-localization.md`](./verification/2026-09-08-localization.md)。

桌面聊天页现在通过 Compose Desktop `dragAndDropTarget` 接收操作系统文件列表。拖入、离开和结束事件只控制
“松开发送文件”覆盖提示；投放后将普通文件转换为 FileKit `PlatformFile`，与附件选择器共享
`toTransferFiles` 预检，再调用现有 `sendFiles`，因此批次上限、用户设置的单文件上限、文件消息和三路并行传输
语义保持一致。目录、非文件载荷和超过 50 个文件会显式失败。Android actual 不注册拖放目标。完整 JVM 回归、
Android shared 编译和桌面编译通过，桌面应用也可启动；未向 Android 安装应用，真实 Finder/Explorer 指针拖动
仍需交互式目标机验收。证据见
[`verification/2026-09-08-desktop-file-drop.md`](./verification/2026-09-08-desktop-file-drop.md)。

首页“附近设备”页现在使用内层 Material 3 Scaffold 在右下角展示刷新 FAB，底部导航的 content padding 会为设备
列表保留可滚动高度，切换到设置页后按钮自动隐藏。点击经 ViewModel 和 Runtime 调用 `PeerDiscovery.refresh()`：
立即发送一次带回复请求的 UDP 组播公告，并绕过正常心跳/离线退避时间探测全部已知端点；in-flight 集合继续避免
同设备重复并发探测。该动作不重启 Ktor 聊天服务、不重建发现 Socket，也不清空 SQLDelight 列表，触发成功后显示
“已重新发起设备发现”。

新增测试覆盖离线退避中的强制探测。网络与共享 JVM 测试、共享 Android 源集和桌面编译在最终修改后通过，桌面
应用启动 smoke test 也到达运行态；现有聊天气泡路径的弃用警告与本次无关。本轮没有安装 Android 应用，真实双设备
刷新时延和真机触控仍待后续交互验证，证据见
[`verification/2026-09-07-manual-discovery-refresh.md`](./verification/2026-09-07-manual-discovery-refresh.md)。

未采纳的接收中媒体播放方案已从产品路线图与长期文档中移除，独立设计文档也已删除。代码和版本目录审计确认项目
从未引入播放器、媒体数据源或接收中打开入口，因此本轮没有删除业务实现或依赖。接收方文件消息仍只在状态为
`COMPLETED`、本地路径存在且长度与 SHA-256 校验成功后调用系统默认应用；发送方打开自己的源文件不属于接收中
播放。文档引用、Markdown 本地链接与 `git diff --check` 均通过，详见
[`verification/2026-09-07-media-plan-cleanup.md`](./verification/2026-09-07-media-plan-cleanup.md)。

公开可见的历史 Actions run `33881533832` 中，Android 与 macOS Intel job 成功，实际失败的是 Windows MSI 和
macOS Apple Silicon DMG；公开接口只保留退出码，无法恢复具体 Gradle 异常。当前工作流已将三个桌面目标从 Android
job 解耦，并在原生打包前显式验证 JDK `jpackage`、macOS `hdiutil` 和 Windows WiX 3。原生任务关闭 configuration
cache、启用 `--info`，失败时额外上传 Gradle problems report 和 Compose 打包参数，后续不再只有 exit code 1。

Windows 改用当前仍预装 WiX 3.14 的 `windows-2022` Runner，并关闭曾出现 tar 路径警告的 Gradle User Home 缓存。
Windows job 先用 `createDistributable` 生成包含 `SubnetDrop.exe` 和完整运行时的便携目录、压缩并上传 ZIP，再构建和
上传 MSI；因此 MSI 失败也不会吞掉已经完成的便携产物。本机 Azul JDK 21.0.11 ARM64 以与 CI 相同参数重新生成
139 MiB DMG 成功，入口是 ARM64 Mach-O；Gradle 任务名、YAML 结构和 diff 检查通过。Windows MSI/ZIP 与 GitHub
双架构 DMG 仍需提交并推送本次工作流后由目标 Runner 验证，证据见
[`verification/2026-09-07-github-actions-desktop-packages.md`](./verification/2026-09-07-github-actions-desktop-packages.md)。

截图中的 `INVALID_REQUEST: Received file size does not match offer` 是 MediaStore pending 条目的元数据误判：
FileKit 的 `size()` 查询 `OpenableColumns.SIZE`，部分 Android 内容提供方在文件发布前返回 `0`、`-1` 或尚未刷新的值。
接收流程现在先检查协议累计字节数，再正常 flush/close 写入流并校验 SHA-256；Android 额外通过
`ParcelFileDescriptor.statSize` 读取真实落盘长度，提供方报告未知长度时跳过这项可选检查。文件存储与传输专项测试、
完整 JVM 回归、桌面编译和 Android Debug APK 构建通过，未安装 Android 应用。证据见
[`verification/2026-09-07-mediastore-size-validation.md`](./verification/2026-09-07-mediastore-size-validation.md)。

Android 默认接收目标已从 `getExternalFilesDir()` 下的应用私有目录改为 MediaStore 公共
`Download/SubnetDrop`。传输创建 `IS_PENDING=1` 的下载项，仍沿用共享传输层的长度与 SHA-256 校验，成功后清除
pending 状态，失败、取消或服务停止时删除条目；文件消息保存可跨重启使用的 `content://` URI，并继续交给 FileKit
调用系统应用打开。不保留旧应用私有目录的迁移分支；开发期已有数据应直接清除。完整 JVM 回归、
桌面编译和 Android Debug Kotlin 编译通过，未安装 Android 应用。验证记录见
[`verification/2026-09-07-android-public-downloads.md`](./verification/2026-09-07-android-public-downloads.md)。

本次桌面端找不到 Android 并非 Wi-Fi、组播或 VPN 路由本身不通：两端分别位于同一 `/23` 网段，桌面可以收到
Android 的 UDP 公告，也能连通 Android 的 TCP `45892` 并收到 WebSocket PONG。运行时 JFR 显示 Android 每
5 秒主动探测桌面，但桌面没有再向 Android 发起探测。根因是发现状态机在首次竞态或连续失败后把端点从
`PeerLivenessTracker` 删除，而数据库中的已知地址只在服务启动时探测一次；只要后续组播公告没有再次进入探测，
设备就会永久停留在 OFFLINE。

发现层现在将“记住端点”和“当前在线”解耦：在线设备保持 5 秒心跳，离线端点按 5、10、20、30 秒封顶退避继续
单播探测，恢复后重新发布 ONLINE，不依赖再次收到组播。未验证的新地址失败不会污染已确认路由；停止与重启发现
会话的清理在同一生命周期锁内完成，探测协程取消时也一定释放 in-flight 标记。9 个发现测试、完整 JVM 回归、
桌面编译和 Android Debug APK 构建通过，未安装 Android 应用。保持桌面 VPN 开启且不改动手机端，重启桌面后
数据库中的 PGFM10 已从 OFFLINE 恢复为 ONLINE，并出现桌面到 `192.168.21.100:45892` 的已建立 TCP 连接。证据见
[`verification/2026-09-07-offline-peer-recovery.md`](./verification/2026-09-07-offline-peer-recovery.md)。

截图中的发送端 216.2 MB、接收端 180.0 MB 来自不同事实：Ktor `send` 只完成本机无界 outgoing channel 入队，
旧实现却立刻增加发送进度；接收端则在实际写盘后增加进度，36.2 MiB 差值对应约 72 个仍在队列中的 512 KiB 帧。
文件会话现在每 4 MiB 插入一个 Ed25519 签名检查点，接收端只有在此前有序帧全部写入且累计值吻合时才签名回应，
发送端只发布这个确认值。客户端 outgoing 与服务端 incoming 文件队列限制为 2 帧，背压可以传回源文件读取。

接收端不再复制 Ktor binary frame，文件写入与 SHA-256 转移到 IO dispatcher，并以每个 session 的 Mutex 保序；
全局传输锁只管理 session map，所以三文件并行不会被单个文件写盘重新串行。9 个传输专项测试通过，新增 10 MiB
回环用例覆盖中间进度不超前、4 MiB 最大窗口差、双方最终值与文件内容一致；完整 JVM、桌面和 Android Debug APK
构建通过，未安装 Android 应用。真实 Wi-Fi 吞吐仍取决于频段、信号、VPN 和接收目录存储，需在同一设备组合复测。
证据见 [`verification/2026-09-07-file-progress-throughput.md`](./verification/2026-09-07-file-progress-throughput.md)。

VPN 共存失败并不是数据库或在线 StateFlow 的问题，而是两段网络路径不一致：UDP Socket 虽然按网卡加入组播，
后续 Ktor WebSocket 探测仍会遵循 VPN 改写后的默认路由。发现层现在拒绝 point-to-point、虚拟及常见隧道网卡；
Android 在发现会话开始前保存原进程网络并绑定 IPv4 Wi-Fi，停止时恢复，绑定/恢复失败时安全退回默认网络。
网卡规则单测、完整 JVM 回归、桌面编译和 Android Debug APK 构建通过，未安装 Android 应用。VPN kill switch、
lockdown 和显式禁止局域网访问仍属于系统策略，必须由用户在 VPN 客户端放行；真实双设备 VPN 互通尚待实机验证。
证据见 [`verification/2026-09-07-vpn-lan-discovery.md`](./verification/2026-09-07-vpn-lan-discovery.md)。

文字消息正文现在由 Compose 官方 `SelectionContainer` 承载：Android 长按后使用系统选择工具栏复制，桌面端可用
鼠标拖选并通过 Ctrl+C 或 Cmd+C 复制。选择容器仅包裹 `message.body`，已读/未读/失败重试状态和文件卡片不进入
选区，也没有引入自定义剪贴板或平台分支。共享 JVM 测试、桌面编译和 Android Debug APK 构建通过，未安装
Android 应用；证据见
[`verification/2026-09-07-message-text-selection.md`](./verification/2026-09-07-message-text-selection.md)。

单文件大小上限现在是 `FileTransferSettings` 的持久化字段，默认 10 GiB，允许用户在设置页输入并保存
1–1024 GiB 的整数值。Multiplatform Settings 在 Android 使用 SharedPreferences、桌面使用 Preferences，因此重启
后仍会恢复，并且不需要修改 SQLDelight schema。发送前按发送设备设置预检，收到 offer 时再按接收设备设置校验；
接收方限制更小时会在内容流开始前拒绝，1 TiB 同时作为不可绕过的协议绝对上限。Android provider 文件会在复制到
应用缓存前检查元数据大小，避免超限文件提前消耗本机存储。

数据测试覆盖默认值、25 GiB 持久化及越界拒绝；网络测试使用稀疏文件验证发送端本地阻断与接收端独立拒绝，不读取
超大文件内容。完整 JVM/桌面回归和 Android Debug APK 构建通过，没有安装 Android 应用；证据见
[`verification/2026-09-07-configurable-file-size-limit.md`](./verification/2026-09-07-configurable-file-size-limit.md)。

文件选择器现在使用 FileKit 的 `Multiple` 模式，一次最多选择 50 个文件。每个合法文件会立即创建独立的
`PREPARING` 消息，进程级 Semaphore 将所有批次合计的活跃发送限制为 3 个；排队项保持可见，不会通过创建大量
WebSocket 同时争抢磁盘和局域网带宽。批次使用 supervisor 语义隔离失败，单文件读取或网络失败不会取消已经运行的
兄弟任务，批次结束后仍会把失败汇总给 UI。

发送端按已提交到 WebSocket 的字节更新进度，接收端按已写入临时文件的字节更新进度；二者各自通过同一个
`StateFlow` 驱动文件消息卡片，并在完成确认后收敛到 100%。5 文件并发测试验证首批只有 3 个 offer、后续任务能
接力执行、双方均得到 5 个完成终态与一致文件内容；失败隔离测试验证坏文件不影响正常文件送达。完整 JVM/桌面、
协议专项、SQLDelight 迁移和 Android Debug APK 构建通过，没有安装 Android 应用；证据见
[`verification/2026-09-07-parallel-file-transfer.md`](./verification/2026-09-07-parallel-file-transfer.md)。

文件传输进入 `COMPLETED`、`REJECTED`、`CANCELLED` 或 `FAILED` 后，现在通过 `ChatRepository` 幂等写入
`fileMessageEntity`，同时保存会话、对端、文件元数据、终态、本地路径和错误。聊天页观察当前会话的 SQLDelight
文件消息 Flow，并与 `FileTransferService.transfers` 的实时进度合并；相同 transfer ID 优先实时项，所以落库瞬间不会
产生重复卡片。完成文件会在组合和点击前通过 FileKit 检查路径，缺失时显示“已失效”，且不会调用系统打开器。

新增 `1.sqm` 完成 v1→v2 迁移；桌面端还兼容此前因手动建库而 `user_version=0` 的旧数据库，按逻辑 v1 迁移并
保留原有文字消息。SQLDelight schema/migration 一致性、旧库迁移、数据库重开、收发/拒绝终态落库、时间线去重和
失效判定均有回归覆盖。完整 JVM/桌面回归、协议定向测试和 Android Debug APK 构建通过，未安装 Android 应用；
证据见 [`verification/2026-09-07-persisted-file-messages.md`](./verification/2026-09-07-persisted-file-messages.md)。

首页底部导航现在只包含“附近设备 / 设置”，会话列表 composable、`HomeSection.CHATS`、对应的 AppUiState 字段、
ViewModel observer/callback 和 Koin 注入均已移除；SQLDelight 的会话表仍承担消息外键和更新时间职责，不因删除入口
而破坏本地消息存储。根 Scaffold 与 HomeHeader 使用白色，聊天 Header/Composer 不再设置 tonal 或 shadow
elevation，聊天根 Column 明确绘制不透明背景。

返回问题的根因是 `closeChat()` 把 selection 设为 null 后，消息流主动发出空列表，而 Navigation3 的退出条目仍可
参与一帧组合，未清空的文件 StateFlow 因而单独显示。null selection 现在切换为 `emptyFlow`，保留最后的文字消息
快照供退出条目使用；时间线又按 conversation ID 过滤保留值，切换其他设备时不会串会话。返回动作先移除 route
再关闭 selection。共享/核心 JVM 测试、Android shared 编译和桌面编译通过，未安装 APK；证据见
[`verification/2026-09-05-home-navigation-chat-return.md`](./verification/2026-09-05-home-navigation-chat-return.md)。

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

Android 两个系统栏改为透明，并关闭 Android 10+ 三键导航强制对比色遮罩。数据、网络和共享 JVM 测试、Android
Debug APK、桌面编译全部通过；
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
地址，已确认设备每 5 s 探测一次；连续 3 次失败后写入 OFFLINE，但保留端点并按 5、10、20、30 秒封顶退避
继续单播探测，恢复后不需要等待新组播。PING 两端只读取轻量 `DeviceProfile`，没有把密码学身份生成重新放回
启动路径。

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
