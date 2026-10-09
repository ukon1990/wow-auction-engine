#!/usr/bin/env bash
# Verifies that a Java runtime is the Temurin release pinned in backend/.sdkmanrc.
# Usage: check-java-runtime.sh [command that runs java...]   (defaults to `java`)
# Example for the backend image: check-java-runtime.sh docker run --rm --entrypoint java <image>
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
expected="$(sed -n 's/^java=\(.*\)-tem$/\1/p' "$root/backend/.sdkmanrc")"
if [[ -z "$expected" ]]; then
  echo "backend/.sdkmanrc has no java=<version>-tem entry." >&2
  exit 1
fi

if [[ $# -eq 0 ]]; then
  set -- java
fi

output="$("$@" -version 2>&1)" || { echo "$output" >&2; echo "Could not run: $* -version" >&2; exit 1; }
echo "$output"
if ! grep -Fq "Temurin-${expected}+" <<<"$output"; then
  echo "Expected Temurin ${expected} (backend/.sdkmanrc)." >&2
  exit 1
fi
echo "Java runtime matches Temurin ${expected}."
