#!/usr/bin/env bash
set -euo pipefail

required_vars=(
  APP_NAME
)

for var_name in "${required_vars[@]}"; do
  if [[ -z "${!var_name:-}" ]]; then
    echo "Missing required environment variable: $var_name" >&2
    exit 1
  fi
done

ENV_FILE_PATH="${ENV_FILE_PATH:-/opt/${APP_NAME}/config/app.env}"

echo "== docker ps =="
docker ps -a --filter "name=^/${APP_NAME}$" --no-trunc

container_id="$(docker ps -aq --filter "name=^/${APP_NAME}$" | head -n 1)"
if [[ -z "$container_id" ]]; then
  echo "CONTAINER_STATE=missing"
  exit 1
fi

container_state="$(docker inspect --format '{{.State.Status}}' "$container_id")"
container_exit_code="$(docker inspect --format '{{.State.ExitCode}}' "$container_id")"

echo "CONTAINER_ID=$container_id"
echo "CONTAINER_STATE=$container_state"
echo "CONTAINER_EXIT_CODE=$container_exit_code"

echo "== container logs =="
docker logs --tail 200 "$APP_NAME" || true

echo "== listeners =="
ss -lntp || true

echo "== env file =="
if [[ -f "$ENV_FILE_PATH" ]]; then
  echo "ENV_FILE_PATH=$ENV_FILE_PATH"
  echo "ENV_KEYS_START"
  awk -F= '!/^[[:space:]]*($|#)/ && NF {print $1}' "$ENV_FILE_PATH"
  echo "ENV_KEYS_END"
else
  echo "ENV_FILE_MISSING=$ENV_FILE_PATH"
fi

if [[ "$container_state" != "running" ]]; then
  exit 1
fi
