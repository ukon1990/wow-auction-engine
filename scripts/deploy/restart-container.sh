#!/usr/bin/env bash
set -euo pipefail

required_vars=(
  APP_NAME
  AWS_REGION
  IMAGE_URI
  ENV_FILE_PATH
  CONTAINER_PORT
)

for var_name in "${required_vars[@]}"; do
  if [[ -z "${!var_name:-}" ]]; then
    echo "Missing required environment variable: $var_name" >&2
    exit 1
  fi
done

LOG_GROUP="${LOG_GROUP:-/aws/ec2/${APP_NAME}}"
HOST_PORT="${HOST_PORT:-$CONTAINER_PORT}"
REGISTRY="${IMAGE_URI%%/*}"

aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY"

docker pull "$IMAGE_URI"
docker rm -f "$APP_NAME" >/dev/null 2>&1 || true

docker run -d \
  --name "$APP_NAME" \
  --restart unless-stopped \
  --env-file "$ENV_FILE_PATH" \
  -p "${HOST_PORT}:${CONTAINER_PORT}" \
  --log-driver awslogs \
  --log-opt awslogs-region="$AWS_REGION" \
  --log-opt awslogs-group="$LOG_GROUP" \
  --log-opt awslogs-stream="$APP_NAME" \
  "$IMAGE_URI"
