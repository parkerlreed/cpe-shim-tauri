#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
core_dir="${CLOUDPILOT_SOURCE:-$project_dir/build/cloudpilot-emu}"
revision="$(cat "$project_dir/cloudpilot-revision.txt")"
patch_file="$project_dir/patches/cloudpilot-omniremote-ir.patch"

if [[ ! -d "$core_dir" ]]; then
    mkdir -p "$(dirname -- "$core_dir")"
    git clone --no-checkout https://github.com/cloudpilot-emu/cloudpilot-emu.git "$core_dir" >&2
    git -C "$core_dir" checkout --detach "$revision" >&2
fi
if [[ "$(git -C "$core_dir" rev-parse HEAD)" != "$revision" ]]; then
    echo "Cloudpilot must be checked out at $revision: $core_dir" >&2
    exit 1
fi
if git -C "$core_dir" apply --reverse --check "$patch_file" 2>/dev/null; then
    : # This exact patch is already applied.
else
    git -C "$core_dir" apply --check "$patch_file"
    git -C "$core_dir" apply "$patch_file"
fi
printf '%s\n' "$core_dir"
