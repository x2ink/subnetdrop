# Staged auto-commit Skill verification — 2026-09-05

## Scope

- Rename the project Skill to `staged-auto-commit`.
- Treat explicit Skill invocation as authorization for one ordinary commit of the current staging area.
- Generate the Conventional Commit subject only from staged changes before committing.
- Never stage files, amend, push, alter hooks or bypass hooks.
- Keep the Skill metadata and repository-facing instructions consistent.

## Validation

```shell
python3 /Users/yangchenglin/.codex/skills/.system/skill-creator/scripts/quick_validate.py \
  .agents/skills/staged-auto-commit
git diff --check
```

Result: `Skill is valid!`; the final diff check reported no whitespace errors.

## Behavior review

- Explicitly invoking `$staged-auto-commit` counts as commit authorization without a second confirmation.
- The workflow stops when `git diff --cached --quiet` reports an empty staging area.
- The generated subject comes only from `git diff --cached`; unstaged and untracked content remains excluded.
- The Skill runs one `git commit -m`, leaves hooks enabled, verifies the resulting SHA and never pushes.
- A message-only request that does not invoke this Skill remains read-only under the repository instructions.

The validator checks structure and metadata rather than performing a real commit. This update does not invoke the Skill
against the current staging area, so it does not create a commit itself.
