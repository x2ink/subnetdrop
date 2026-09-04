---
name: staged-auto-commit
description: Inspect only the current Git staging area, generate a Conventional Commit message, and immediately commit the staged changes. Use when the user invokes this skill or asks to auto-commit staged changes. Never stage files, amend, or push.
---

# Staged Auto Commit

Generate a Conventional Commit subject from the repository's staged changes and immediately create one ordinary commit.
Invoking this Skill explicitly counts as authorization to commit the current staging area; do not ask for confirmation.

## Inspect the staging area

Run these commands from the repository root:

```shell
git status --short
git diff --cached --name-status
git diff --cached --stat
git diff --cached
git branch --show-current
```

Use only `git diff --cached` to infer the change. Do not incorporate unstaged or untracked content into the message.
`git status --short` is only for distinguishing those states.

If `git diff --cached --quiet` succeeds, report that the staging area is empty and stop. Do not stage files or infer a
message from working-tree or untracked changes.

## Generate the message

Generate one subject in this form:

```text
type(scope): subject
```

- Choose from `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`, `build`, `ci`, or `revert`.
- Infer the narrowest useful scope from staged paths and behavior. Prefer `core`, `ui`, `network`, `data`, `infra`,
  `docs`, or the affected feature or module name.
- Prefer a concise Chinese subject while preserving technical names such as API, UI, SQLDelight, and Gradle.
- Describe the staged outcome rather than filenames or implementation steps. Use imperative wording, no trailing period,
  and keep the subject line within 72 characters when practical.
- Do not append task IDs, dates, issue IDs, or footers unless the user explicitly requested that exact text.

The staging area is the user-selected commit boundary. Generate one message for it even when related changes span modules.
If clearly unrelated changes are staged, warn the user before committing and stop so they can correct the staging area.

## Commit

Run exactly one ordinary commit using the generated subject:

```shell
git commit -m "type(scope): subject"
```

Do not use `--no-verify`; allow repository hooks to validate or update the message. If a hook rejects the commit, report
the failure and leave the remaining staging area intact. Do not bypass the hook or retry with a different Git operation.

After a successful commit, verify and report the actual commit SHA and subject:

```shell
git log -1 --format='%h %s'
git status --short
```

Do not claim success unless `git commit` exits successfully. Never push after committing.

## Safety boundary

Never run `git add`, `git restore --staged`, `git reset`, `git commit --amend`, or `git push`. This Skill may run only the
single ordinary `git commit` authorized by its invocation. Do not modify hooks, remotes, branches, the working tree, or
unstaged files.
