# 项目经验

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
