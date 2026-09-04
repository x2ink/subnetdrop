---
name: staged-commit-message
description: Analyze only the current Git staging area, generate a Conventional Commit message, and optionally create the commit when explicitly requested. Use for 暂存区提交信息, commit message, 提交, or commit requests. Never stage files, amend, or push.
---

# Staged Commit Message

Generate a Conventional Commit message using only the repository's staged changes. Create the commit only when the user
explicitly asks to commit.

## Select the mode

- **Message mode:** the user asks to generate, suggest, or show a commit message. Inspect the staging area and return the
  message without changing Git state.
- **Commit mode:** the user explicitly says to commit, 提交, 帮我提交, or 帮我 commit. Treat that request as authorization
  to commit the current staging area; do not ask for another confirmation.

An explicit Skill invocation alone does not authorize a commit unless the accompanying request asks for one.

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

If `git diff --cached --quiet` succeeds, say that the staging area is empty and stop in both modes. Do not stage or infer
a message from working-tree or untracked changes.

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

The staging area is the user-selected commit boundary. Even if it contains multiple related modules, generate one message
for the staged outcome. If clearly unrelated changes are staged, add one short warning; never restage or split them.

In message mode, present the result first in a plain text code block. A one-sentence rationale may follow when the type or
scope is not obvious.

## Create the commit

In commit mode, run exactly one ordinary commit with the generated subject:

```shell
git commit -m "type(scope): subject"
```

Do not use `--no-verify`; allow repository hooks to validate or update the message. If a hook rejects the commit, report
the failure and leave the staging area intact instead of bypassing it.

After success, verify and report the actual commit SHA and subject:

```shell
git log -1 --format='%h %s'
git status --short
```

Do not claim success unless `git commit` exits successfully. Do not push after committing.

## Safety boundary

Never run `git add`, `git restore --staged`, `git reset`, `git commit --amend`, or `git push`. Commit mode may run only the
single ordinary `git commit` authorized above. Do not modify hooks, remotes, branches, the working tree, or unstaged files.
