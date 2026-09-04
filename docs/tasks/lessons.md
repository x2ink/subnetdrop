# 项目经验

## 暂存区提交 Skill 的授权语义

- “生成 commit message”和“帮我 commit”不是同一种授权：前者必须只读，后者可以直接提交当前暂存区，不应再要求
  一次确认。
- 用户已经用暂存区表达了提交边界，Skill 不应擅自 `git add`、拆分或混入工作区内容；提交失败也不能用
  `--no-verify` 绕过 hook。
- Skill 行为改变时同时检查 `agents/openai.yaml`、根 `AGENTS.md` 和 README，避免入口描述继续触发旧行为。

## Compose 消息卡片测量

- `heightIn(min = ...)` 只保证容器高度，不会自动把子项居中；气泡正文需要明确的对齐容器，否则单行文字会偏上。
- 自适应卡片不能同时保留固定 `minWidth`、内部 `fillMaxWidth` 和填满型 `weight`，其中任一项都可能把短内容撑到
  最大可用宽度。应由内容的 intrinsic width 决定实际宽度，只保留防止桌面端过宽的最大值。

## 文件接收策略与媒体预览

- “默认自动接收、可选逐文件确认”是传输策略，不是对话框显隐选项；必须持久化到领域端口，并由网络层处理每个
  offer 时读取，才能保证 UI 与协议行为一致。
- 文件边收边写与媒体边收边播是两种能力。系统默认应用没有等待增长中文件或限制 seek 的跨平台契约，不能把
  “临时文件可见”当成渐进播放已完成；可靠实现需要受控数据源与应用内播放器。
- 自定义 Android SAF 目录不能只保存 `content://` 字符串，还要在选择时获取持久化权限；优先调用 FileKit 的
  bookmark API，不重复实现 ContentResolver 权限细节。
- edge-to-edge 同时包含状态栏、手势/三键导航栏和内容 inset。仅设置透明色不一定消除 Android 10+ 三键导航的
  对比度遮罩，需要把 `isNavigationBarContrastEnforced` 纳入验证。

## 聊天时间线与 IME 布局

- 文件传输如果属于聊天语义，就必须作为带稳定 key 和时间戳的时间线 item 与文字共同滚动；不要在输入框上方
  再放一个独立的固定高度传输面板，否则文件不具备消息的位置和方向语义。
- 发送/已读状态不应放进文字气泡的内容容器，它会参与气泡宽高测量；状态应作为气泡下方的同方向附属行。
- edge-to-edge 的聊天输入栏不能只在输入栏内部叠加 `imePadding`。先消费 Scaffold 已应用的系统栏 inset，再由
  页面外层处理 IME，并配合 Activity `adjustResize`，才能避免导航栏与键盘 inset 双算。

## 项目级 Codex Skill 的发现目录

- 项目内需要自动发现或通过 `$skill-name` 调用的 Skill 必须放在 `.agents/skills/<skill-name>/`，不能使用普通
  `skills/` 目录替代。
- 创建后同时检查 `SKILL.md`、`agents/openai.yaml` 和 `AGENTS.md` 中的路径，运行 Skill validator，并确认
  `policy.allow_implicit_invocation` 符合用户要求。
- 涉及已有暂存内容时，先区分 index 与 working tree；修正文件位置不等于已经更新暂存区，未经提交计划确认
  不擅自执行 `git add`。

## 个人 GitHub 项目的提交格式

- 本仓库使用标准 Conventional Commit：`type(scope): subject`。
- 不从外部模板继承任务号、日期后缀或额外 footer；修改提交生成 Skill、共享 hook 和本地激活副本时
  必须同时核对，避免清理后再次生成。

## Android 交互与启动性能验证

- 仅用状态容器单元测试和 Android 编译不能证明真机触摸链路可用；用户报告真机交互故障时，必须安装当前 APK，
  通过实际点击前后截图验证页面内容和选中状态都发生变化。
- 启动流程包含多个异步系统组件时，`Starting` 必须暴露具体阶段或留下等价诊断证据，不能根据代码阅读猜测阻塞点。
- Navigation3 的 `entryProvider` 会按 back stack 保存 `NavEntry`；不能在 entry 内容闭包中直接捕获会整体替换的
  UI 快照，应捕获稳定的 `State` 并在 entry 组合内部读取最新值，否则运行时横幅和页面切换会一起停在旧状态。
- 调试器断点会暂停主线程并可能触发 OEM 的输入超时 ANR；断点日志只能证明回调命中，判断真实 ANR 必须在
  断开调试器后重新冷启动复现。
