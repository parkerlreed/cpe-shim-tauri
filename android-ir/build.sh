#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
: "${EMSDK:?Set EMSDK to your Emscripten SDK directory}"
: "${PALM_IMAGE:?Set PALM_IMAGE to your own Cloudpilot Palm m130 session image with OmniRemote installed}"
if [[ ! -f "$PALM_IMAGE" ]]; then
    echo "Session image does not exist: $PALM_IMAGE" >&2
    exit 1
fi
source "$EMSDK/emsdk_env.sh"
core_dir="$("$project_dir/prepare-cloudpilot.sh")"
make -C "$core_dir/src/common" -j8 emscripten
make -C "$core_dir/src/skins" -j8 emscripten
make -C "$core_dir/src/cloudpilot" -j8 emscripten
cp "$core_dir/src/cloudpilot/cloudpilot_web.js" "$project_dir/app/src/main/assets/"
cp "$core_dir/src/cloudpilot/cloudpilot_web.wasm" "$project_dir/app/src/main/assets/"
cp "$PALM_IMAGE" "$project_dir/app/src/main/assets/session.img"
cd "$project_dir"
./gradlew assembleDebug
