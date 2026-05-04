#!/usr/bin/env bash
set -euo pipefail

required_vars=(
  APP_NAME
  AWS_REGION
  IMAGE_URI
  CONTAINER_PORT
)

for var_name in "${required_vars[@]}"; do
  if [[ -z "${!var_name:-}" ]]; then
    echo "Missing required environment variable: $var_name" >&2
    exit 1
  fi
done

LOG_GROUP="${LOG_GROUP:-/aws/ec2/${APP_NAME}}"
HOST_PORT="${HOST_PORT:-}"
REGISTRY="${IMAGE_URI%%/*}"
NETWORK_NAME="${NETWORK_NAME:-}"

aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY"

docker pull "$IMAGE_URI"
docker rm -f "$APP_NAME" >/dev/null 2>&1 || true

# SSM deploy invokes this script without systemd's env; align with pipeline health URL (same EIP as CF AppBaseUrl).
if [[ -z "${NG_ALLOWED_HOSTS:-}" ]]; then
  imds="http://169.254.169.254"
  if token="$(curl -fsS --max-time 1 -X PUT "$imds/latest/api/token" \
    -H "X-aws-ec2-metadata-token-ttl-seconds: 60" 2>/dev/null)"; then
    pub="$(curl -fsS --max-time 1 -H "X-aws-ec2-metadata-token: $token" \
      "$imds/latest/meta-data/public-ipv4" 2>/dev/null)" || true
    if [[ -n "$pub" ]]; then
      export NG_ALLOWED_HOSTS="$pub"
    fi
  fi
fi

set -- -d --name "$APP_NAME" --restart unless-stopped \
  --log-driver awslogs \
  --log-opt awslogs-region="$AWS_REGION" \
  --log-opt awslogs-group="$LOG_GROUP" \
  --log-opt awslogs-stream="$APP_NAME"

if [[ -n "$HOST_PORT" ]]; then
  set -- "$@" -p "${HOST_PORT}:${CONTAINER_PORT}"
fi

if [[ -n "${ENV_FILE_PATH:-}" ]]; then
  set -- "$@" --env-file "$ENV_FILE_PATH"
fi

if [[ -n "$NETWORK_NAME" ]]; then
  docker network create "$NETWORK_NAME" >/dev/null 2>&1 || true
  set -- "$@" --network "$NETWORK_NAME"
fi

if [[ -n "${BACKEND_ORIGIN:-}" ]]; then
  set -- "$@" -e "BACKEND_ORIGIN=$BACKEND_ORIGIN"
fi

if [[ -n "${NG_ALLOWED_HOSTS:-}" ]]; then
  set -- "$@" -e "NG_ALLOWED_HOSTS=$NG_ALLOWED_HOSTS"
fi

docker run "$@" "$IMAGE_URI"
