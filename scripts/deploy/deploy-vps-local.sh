#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
K8S_DIR="${ROOT_DIR}/infra/kubernetes/vps"

APP_NAMESPACE="${APP_NAMESPACE:-ee}"
BACKEND_DEPLOYMENT="${BACKEND_DEPLOYMENT:-ee-backend}"
FRONTEND_DEPLOYMENT="${FRONTEND_DEPLOYMENT:-ee-frontend}"
BACKEND_CONTAINER="${BACKEND_CONTAINER:-backend}"
FRONTEND_CONTAINER="${FRONTEND_CONTAINER:-frontend}"
APP_HOST="${APP_HOST:-ee.jonaskf.net}"
HEALTH_PATH="${HEALTH_PATH:-/api/health}"
GHCR_IMAGE_PREFIX="${GHCR_IMAGE_PREFIX:-}"
GHCR_USERNAME="${GHCR_USERNAME:-}"
GHCR_PULL_TOKEN="${GHCR_PULL_TOKEN:-}"
IMAGE_PLATFORM="${IMAGE_PLATFORM:-linux/amd64}"

required_commands=(kubectl docker git curl)
required_env=(
  KUBECONFIG
  GHCR_IMAGE_PREFIX
  BLIZZARD_CLIENT_ID
  BLIZZARD_CLIENT_SECRET
  AUCTION_ENGINE_DB_URL
  AUCTION_ENGINE_DB_USERNAME
  AUCTION_ENGINE_DB_PASSWORD
  WAE_AUTH_SESSION_SECRET
  GHCR_PULL_TOKEN
)

for command_name in "${required_commands[@]}"; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Missing required command: ${command_name}" >&2
    exit 1
  fi
done

for var_name in "${required_env[@]}"; do
  if [[ -z "${!var_name:-}" ]]; then
    echo "Missing required environment variable: ${var_name}" >&2
    exit 1
  fi
done

if [[ ! -r "$KUBECONFIG" ]]; then
  echo "KUBECONFIG does not point to a readable file: ${KUBECONFIG}" >&2
  exit 1
fi

if [[ ! -d "$K8S_DIR" ]]; then
  echo "Kubernetes manifest directory not found: ${K8S_DIR}" >&2
  exit 1
fi

if [[ -z "$GHCR_USERNAME" ]]; then
  GHCR_USERNAME="$(printf '%s' "${GHCR_IMAGE_PREFIX#ghcr.io/}" | cut -d/ -f1)"
fi

docker_config="${DOCKER_CONFIG:-${HOME}/.docker}/config.json"
if [[ ! -r "$docker_config" ]] || ! grep -q '"ghcr.io"' "$docker_config"; then
  if [[ -z "$GHCR_PULL_TOKEN" ]]; then
    echo "Docker does not appear to be logged in to ghcr.io. Run: docker login ghcr.io" >&2
    exit 1
  fi
  echo "Logging in to ghcr.io as ${GHCR_USERNAME}"
  printf '%s' "$GHCR_PULL_TOKEN" | docker login ghcr.io -u "$GHCR_USERNAME" --password-stdin
fi

short_sha="$(git -C "$ROOT_DIR" rev-parse --short=12 HEAD)"
timestamp="$(date -u +%Y%m%d%H%M%S)"
image_tag="${IMAGE_TAG:-local-${short_sha}-${timestamp}}"
backend_image="${GHCR_IMAGE_PREFIX%/}/backend:${image_tag}"
frontend_image="${GHCR_IMAGE_PREFIX%/}/frontend:${image_tag}"

cleanup() {
  rm -f "${ROOT_DIR}/backend/app.war" "${ROOT_DIR}/frontend/app-dist.tar.gz"
}
trap cleanup EXIT

echo "Preparing deploy artifacts for ${image_tag}"
(
  cd "$ROOT_DIR"
  WORKFLOW_RUN_ID="" bash scripts/deploy/ensure-war.sh
  WORKFLOW_RUN_ID="" bash scripts/deploy/ensure-frontend-dist.sh
)

echo "Checking Kubernetes access"
kubectl -n "$APP_NAMESPACE" get namespace "$APP_NAMESPACE" >/dev/null 2>&1 || true
kubectl cluster-info >/dev/null

echo "Building and pushing backend image: ${backend_image}"
docker build \
  --platform "$IMAGE_PLATFORM" \
  --tag "$backend_image" \
  "${ROOT_DIR}/backend"
docker push "$backend_image"

echo "Building and pushing frontend image: ${frontend_image}"
docker build \
  --platform "$IMAGE_PLATFORM" \
  --file "${ROOT_DIR}/frontend/Dockerfile" \
  --tag "$frontend_image" \
  "$ROOT_DIR"
docker push "$frontend_image"

echo "Applying Kubernetes manifests"
kubectl apply -f "${K8S_DIR}/namespace.yaml"
kubectl apply -f "$K8S_DIR"

if [[ -n "$GHCR_PULL_TOKEN" ]]; then
  echo "Applying GHCR image pull secret"
  kubectl -n "$APP_NAMESPACE" create secret docker-registry ghcr \
    --docker-server=ghcr.io \
    --docker-username="$GHCR_USERNAME" \
    --docker-password="$GHCR_PULL_TOKEN" \
    --docker-email="${GHCR_USERNAME}@users.noreply.github.com" \
    --dry-run=client \
    -o yaml | kubectl apply -f -
fi

secret_args=(
  --from-literal=BLIZZARD_CLIENT_ID="${BLIZZARD_CLIENT_ID}"
  --from-literal=BLIZZARD_CLIENT_SECRET="${BLIZZARD_CLIENT_SECRET}"
  --from-literal=AUCTION_ENGINE_DB_URL="${AUCTION_ENGINE_DB_URL}"
  --from-literal=AUCTION_ENGINE_DB_USERNAME="${AUCTION_ENGINE_DB_USERNAME}"
  --from-literal=AUCTION_ENGINE_DB_PASSWORD="${AUCTION_ENGINE_DB_PASSWORD}"
  --from-literal=WAE_AUTH_SESSION_SECRET="${WAE_AUTH_SESSION_SECRET}"
)

optional_secret_vars=(
  AWS_ACCESS_KEY
  AWS_SECRET_KEY
  WAE_DYNAMODB_ENDPOINT
  WAE_S3_ENDPOINT
  WAE_COGNITO_ISSUER_URI
  WAE_COGNITO_CLIENT_ID
  WAE_COGNITO_HOSTED_UI_BASE_URL
  WAE_COGNITO_USER_POOL_ID
  JAVA_TOOL_OPTIONS
)

for var_name in "${optional_secret_vars[@]}"; do
  if [[ -n "${!var_name:-}" ]]; then
    secret_args+=("--from-literal=${var_name}=${!var_name}")
  fi
done

echo "Applying runtime secret"
kubectl -n "$APP_NAMESPACE" create secret generic wow-auction-engine-secrets \
  "${secret_args[@]}" \
  --dry-run=client \
  -o yaml | kubectl apply -f -

frontend_secret_args=(
  --from-literal=WAE_AUTH_SESSION_SECRET="${WAE_AUTH_SESSION_SECRET}"
)
frontend_optional_secret_vars=(
  WAE_COGNITO_CLIENT_ID
  WAE_COGNITO_HOSTED_UI_BASE_URL
)

for var_name in "${frontend_optional_secret_vars[@]}"; do
  if [[ -n "${!var_name:-}" ]]; then
    frontend_secret_args+=("--from-literal=${var_name}=${!var_name}")
  fi
done

echo "Applying frontend secret"
kubectl -n "$APP_NAMESPACE" create secret generic wow-auction-engine-frontend-secrets \
  "${frontend_secret_args[@]}" \
  --dry-run=client \
  -o yaml | kubectl apply -f -

echo "Updating deployment images"
kubectl -n "$APP_NAMESPACE" set image "deployment/${BACKEND_DEPLOYMENT}" \
  "${BACKEND_CONTAINER}=${backend_image}"
kubectl -n "$APP_NAMESPACE" set image "deployment/${FRONTEND_DEPLOYMENT}" \
  "${FRONTEND_CONTAINER}=${frontend_image}"

echo "Waiting for rollouts"
kubectl -n "$APP_NAMESPACE" rollout status "deployment/${BACKEND_DEPLOYMENT}" --timeout=300s
kubectl -n "$APP_NAMESPACE" rollout status "deployment/${FRONTEND_DEPLOYMENT}" --timeout=180s

echo "Verifying public endpoints"
for _ in $(seq 1 30); do
  if curl --fail --silent --show-error --connect-timeout 5 --max-time 10 "https://${APP_HOST}/" >/dev/null; then
    break
  fi
  sleep 10
done
curl --fail --silent --show-error --connect-timeout 5 --max-time 10 "https://${APP_HOST}/" >/dev/null

for _ in $(seq 1 30); do
  if curl --fail --silent --show-error --connect-timeout 5 --max-time 10 "https://${APP_HOST}${HEALTH_PATH}" >/dev/null; then
    break
  fi
  sleep 10
done
curl --fail --silent --show-error --connect-timeout 5 --max-time 10 "https://${APP_HOST}${HEALTH_PATH}" >/dev/null

echo "VPS deploy succeeded: ${image_tag}"
