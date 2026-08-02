#!/usr/bin/env sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
"$SCRIPT_DIR/bundle.sh"

cd "$SCRIPT_DIR/../../backend"
./mvnw generate-sources
