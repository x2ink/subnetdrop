# Staged commit Skill verification — 2026-09-05

## Scope

- Preserve read-only message generation when the user only asks for a commit message.
- Allow one ordinary commit when the user explicitly asks to commit the current staging area.
- Never stage files, amend, push, alter hooks or bypass hooks.
- Keep the Skill metadata and repository-facing instructions consistent.

## Validation

```shell
python3 /Users/yangchenglin/.codex/skills/.system/skill-creator/scripts/quick_validate.py \
  .agents/skills/staged-commit-message
git diff --check
```

Result: `Skill is valid!`; the final diff check reported no whitespace errors.

## Behavior review

- A plain request for a message selects message mode and does not mutate Git state.
- “提交”, “commit”, “帮我提交” and “帮我 commit” select commit mode and count as the user's commit authorization.
- Both modes stop when `git diff --cached --quiet` reports an empty staging area.
- The generated subject comes only from `git diff --cached`; unstaged and untracked content remains excluded.
- Commit mode runs one `git commit -m`, leaves hooks enabled, verifies the resulting SHA and never pushes.

The validator checks structure and metadata rather than performing a real commit. No commit was created during this Skill
update because the user requested a workflow change, not execution against the current staging area.
