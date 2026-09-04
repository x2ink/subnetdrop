---
name: staged-commit-message
description: Analyze only the current Git staging area and generate a copy-ready Conventional Commit message. Use when the user asks for a commit message, 提交信息, or 根据暂存区生成 message. Never stage files, create or amend commits, or push.
---

# Staged Commit Message

Generate a commit message from the repository's staged changes without modifying Git state.

## Read-only inspection

Run these commands from the repository root:

```shell
git status --short
git diff --cached --name-status
git diff --cached --stat
git diff --cached
git branch --show-current
```

Use only `git diff --cached` content to infer the change. Do not incorporate unstaged or untracked content into the
message. `git status --short` is only for distinguishing those states.

If `git diff --cached --quiet` succeeds, say that the staging area is empty and stop. Do not suggest a message based on
working-tree changes.

## Message generation

Return one copy-ready message using:

```text
type(scope): subject
```

- Choose from `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`, `build`, `ci`, or `revert`.
- Infer the narrowest useful scope from the staged paths and behavior. Prefer `core`, `ui`, `network`, `data`, `infra`,
  `docs`, or the affected feature/module name.
- Prefer a concise Chinese subject while preserving technical names such as API, UI, SQLDelight, and Gradle.
- Describe the staged outcome, not filenames or implementation steps. Use imperative wording, no trailing period, and
  keep the subject line within 72 characters when practical.
- Do not append task IDs, date-based identifiers, issue IDs, or extra footers unless the user explicitly
  includes that text in the requested subject.

If the staging area contains unrelated logical changes, still provide the best single message requested, then add one
short warning that the staged set would be cleaner as separate commits. Do not change the staging area.

Present the result first in a plain text code block. A one-sentence rationale may follow when the type or scope is not
obvious.

## Safety boundary

This Skill is read-only. Never run `git add`, `git restore --staged`, `git reset`, `git commit`, `git commit --amend`,
`git push`, or any command that changes the index, history, remotes, hooks, or working tree. If the user separately asks
to commit, amend, or push, leave this Skill's workflow and use the repository's normal Git authorization process.
