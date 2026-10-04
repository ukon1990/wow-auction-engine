#!/usr/bin/env sh
# Bundle the split OpenAPI tree into a single document so generators do not
# emit duplicate *1 models from multi-file $ref identity collisions.
set -eu

ROOT="$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)"
INPUT="$ROOT/openapi/openapi.yml"
OUTPUT="$ROOT/openapi/openapi.bundled.yml"

find_bun() {
    if [ -n "${BUN_EXECUTABLE:-}" ]; then
        if [ ! -x "$BUN_EXECUTABLE" ]; then
            echo "BUN_EXECUTABLE is not executable: $BUN_EXECUTABLE" >&2
            return 1
        fi
        printf '%s\n' "$BUN_EXECUTABLE"
        return
    fi

    if command -v bun >/dev/null 2>&1; then
        command -v bun
        return
    fi

    # IDE-launched Maven often omits the NVM-managed Node directory from PATH.
    for candidate in "${NVM_DIR:-$HOME/.local/share/nvm}"/*/bin/bun; do
        if [ -x "$candidate" ]; then
            printf '%s\n' "$candidate"
            return
        fi
    done

    echo "Could not find Bun. Install Bun or set BUN_EXECUTABLE to its absolute path." >&2
    return 1
}

BUN="$(find_bun)"

cd "$ROOT/frontend"
"$BUN" x --bun @redocly/cli bundle "$INPUT" --output "$OUTPUT"
echo "Bundled OpenAPI -> $OUTPUT"
