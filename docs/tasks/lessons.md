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
