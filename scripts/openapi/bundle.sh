#!/usr/bin/env sh
# Bundle the split OpenAPI tree into a single document so generators do not
# emit duplicate *1 models from multi-file $ref identity collisions.
set -eu

ROOT="$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)"
INPUT="$ROOT/openapi/openapi.yml"
OUTPUT="$ROOT/openapi/openapi.bundled.yml"

cd "$ROOT/frontend"
bunx --bun @redocly/cli bundle "$INPUT" --output "$OUTPUT"
echo "Bundled OpenAPI -> $OUTPUT"
