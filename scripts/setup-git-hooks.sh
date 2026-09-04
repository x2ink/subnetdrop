#!/usr/bin/env bash

set -euo pipefail

repository_root=$(git rev-parse --show-toplevel)
hooks_directory=$(git -C "$repository_root" rev-parse --git-path hooks)

mkdir -p "$hooks_directory"

for hook_name in prepare-commit-msg pre-push; do
    source_hook="$repository_root/.githooks/$hook_name"
    target_hook="$hooks_directory/$hook_name"
    if [ ! -f "$source_hook" ]; then
        printf 'Missing shared hook: %s\n' "$source_hook" >&2
        exit 1
    fi
    cp "$source_hook" "$target_hook"
    chmod 0755 "$target_hook"
    printf 'Installed: %s\n' "$hook_name"
done

printf '%s\n' "Git hooks are active. Run 'git commit' to open the Conventional Commit builder."
