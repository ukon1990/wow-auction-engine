#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FRONTEND_DIR="${ROOT_DIR}/frontend"
DIST_FILE="${FRONTEND_DIR}/app-dist.tar.gz"
REGIONS_FILE="${ROOT_DIR}/infra/regions.json"
RESTART_SCRIPT="${ROOT_DIR}/scripts/deploy/restart-container.sh"

cleanup() {
  rm -f "$DIST_FILE"
}
trap cleanup EXIT

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Missing required command: $name" >&2
    exit 1
  fi
}

json_value() {
  local expression="$1"
  python3 -c "import json; data=json.load(open('${REGIONS_FILE}', encoding='utf-8')); print(${expression})"
}

base64_one_line() {
  if base64 --help 2>&1 | grep -q -- '-w'; then
    base64 -w 0 "$1"
  else
    base64 < "$1" | tr -d '\n'
  fi
}

wait_for_ssm_command() {
  local aws_region="$1"
  local command_id="$2"

  for _ in $(seq 1 60); do
    status="$(aws ssm list-command-invocations \
      --region "$aws_region" \
      --command-id "$command_id" \
      --details \
      --query 'CommandInvocations[0].Status' \
      --output text)"

    if [[ "$status" == "Success" ]]; then
      return 0
    fi

    if [[ "$status" == "Cancelled" || "$status" == "Failed" || "$status" == "TimedOut" ]]; then
      aws ssm list-command-invocations \
        --region "$aws_region" \
        --command-id "$command_id" \
        --details
      return 1
    fi

    sleep 10
  done

  echo "SSM command ${command_id} did not complete in time" >&2
  return 1
}

verify_frontend() {
  local base_url="$1"

  for _ in $(seq 1 30); do
    if curl --fail --silent --show-error --connect-timeout 5 --max-time 10 "${base_url}/" >/dev/null; then
      return 0
    fi
    sleep 10
  done

  echo "Frontend health check failed for ${base_url}/" >&2
  return 1
}

cd "$ROOT_DIR"

require_command aws
require_command bun
require_command curl
require_command docker
require_command git
require_command python3
require_command tar

if ! git diff --quiet || ! git diff --cached --quiet || [[ -n "$(git ls-files --others --exclude-standard)" ]]; then
  echo "Refusing to deploy from a dirty git worktree. Commit or stash changes first." >&2
  git status --short >&2
  exit 1
fi

if [[ ! -f "$REGIONS_FILE" ]]; then
  echo "Missing regions config: $REGIONS_FILE" >&2
  exit 1
fi

if [[ ! -f "$RESTART_SCRIPT" ]]; then
  echo "Missing restart script: $RESTART_SCRIPT" >&2
  exit 1
fi

IMAGE_TAG="$(git rev-parse HEAD)"
APP_NAME="$(json_value "data['app_name']")"
ENVIRONMENT="$(json_value "data['environment']")"
PROJECT_TAG="$(json_value "data['project_tag']")"
SHORT_SHA="${IMAGE_TAG:0:12}"

echo "Preparing local frontend production deploy"
echo "  app: ${APP_NAME}"
echo "  environment: ${ENVIRONMENT}"
echo "  project: ${PROJECT_TAG}"
echo "  image tag: ${IMAGE_TAG}"

echo "Checking AWS identity"
aws sts get-caller-identity --output table

echo "Building frontend artifact with pipeline-equivalent steps"
(
  cd "$FRONTEND_DIR"
  bun install --frozen-lockfile
  bun run format:check
  bun run lint
  bun run test:ci
  bun run build
  tar -czf app-dist.tar.gz -C dist .
)

echo "Building ARM64 frontend Docker image"
docker buildx build \
  --platform linux/arm64 \
  --file frontend/Dockerfile \
  --tag "localbuild:frontend-${IMAGE_TAG}" \
  --load \
  .

regions_json="$(python3 -c "import json; data=json.load(open('${REGIONS_FILE}', encoding='utf-8')); print(json.dumps([r for r in data['regions'] if r.get('enabled', False)]))")"
region_count="$(python3 -c "import json; print(len(json.loads('''${regions_json}''')))")"

if [[ "$region_count" == "0" ]]; then
  echo "No enabled regions found in ${REGIONS_FILE}" >&2
  exit 1
fi

for index in $(seq 0 $((region_count - 1))); do
  region_json="$(python3 -c "import json; print(json.dumps(json.loads('''${regions_json}''')[${index}]))")"
  aws_region="$(python3 -c "import json; print(json.loads('''${region_json}''')['aws_region'])")"
  stack_name="$(python3 -c "import json; print(json.loads('''${region_json}''')['stack_name'])")"
  container_port="$(python3 -c "import json; print(json.loads('''${region_json}''')['container_port'])")"

  echo "Deploying frontend to ${aws_region} (${stack_name})"

  account_id="$(aws sts get-caller-identity --query Account --output text)"
  repository_uri="${account_id}.dkr.ecr.${aws_region}.amazonaws.com/${APP_NAME}-frontend"
  image_uri="${repository_uri}:${IMAGE_TAG}"
  frontend_name="${APP_NAME}-frontend"
  backend_name="${APP_NAME}-backend"
  network_name="${APP_NAME}-network"
  log_group="/aws/ec2/${APP_NAME}/${ENVIRONMENT}/${aws_region}"

  echo "Logging in to ECR ${repository_uri}"
  aws ecr get-login-password --region "$aws_region" \
    | docker login --username AWS --password-stdin "${account_id}.dkr.ecr.${aws_region}.amazonaws.com"

  echo "Pushing ${image_uri} and ${repository_uri}:master"
  docker tag "localbuild:frontend-${IMAGE_TAG}" "$image_uri"
  docker tag "localbuild:frontend-${IMAGE_TAG}" "${repository_uri}:master"
  docker push "$image_uri"
  docker push "${repository_uri}:master"

  instance_id="$(aws cloudformation describe-stacks \
    --region "$aws_region" \
    --stack-name "$stack_name" \
    --query "Stacks[0].Outputs[?OutputKey=='AppInstanceId'].OutputValue" \
    --output text)"
  base_url="$(aws cloudformation describe-stacks \
    --region "$aws_region" \
    --stack-name "$stack_name" \
    --query "Stacks[0].Outputs[?OutputKey=='AppBaseUrl'].OutputValue" \
    --output text)"

  if [[ -z "$instance_id" || "$instance_id" == "None" ]]; then
    echo "Could not resolve AppInstanceId from ${stack_name} in ${aws_region}" >&2
    exit 1
  fi
  if [[ -z "$base_url" || "$base_url" == "None" ]]; then
    echo "Could not resolve AppBaseUrl from ${stack_name} in ${aws_region}" >&2
    exit 1
  fi

  script_b64="$(base64_one_line "$RESTART_SCRIPT")"
  export SCRIPT_B64="$script_b64"
  export APP_NAME="$APP_NAME"
  export AWS_REGION="$aws_region"
  export BACKEND_IMAGE_URI=""
  export FRONTEND_IMAGE_URI="$image_uri"
  export CONTAINER_PORT="$container_port"
  export ENVIRONMENT="$ENVIRONMENT"
  export RUN_BACKEND_ROLLOUT="false"
  export RUN_FRONTEND_ROLLOUT="true"

  python3 - <<'PY' > /tmp/wae-local-frontend-ssm-commands.json
import json
import os

app_name = os.environ["APP_NAME"]
aws_region = os.environ["AWS_REGION"]
frontend_image_uri = os.environ["FRONTEND_IMAGE_URI"]
container_port = os.environ["CONTAINER_PORT"]
environment = os.environ["ENVIRONMENT"]
script_b64 = os.environ["SCRIPT_B64"]

backend_name = f"{app_name}-backend"
frontend_name = f"{app_name}-frontend"
network_name = f"{app_name}-network"

commands = [
    f"sudo /opt/{app_name}/bin/write-env-file.sh",
    f"echo {script_b64} | base64 -d | sudo tee /opt/{app_name}/bin/restart-container.sh > /dev/null",
    f"sudo chmod +x /opt/{app_name}/bin/restart-container.sh",
    f"sudo docker network create {network_name} >/dev/null 2>&1 || true",
    "sudo "
    f"APP_NAME={frontend_name} "
    f"AWS_REGION={aws_region} "
    f"IMAGE_URI={frontend_image_uri} "
    "CONTAINER_PORT=4000 "
    "HOST_PORT=80 "
    f"NETWORK_NAME={network_name} "
    f"BACKEND_ORIGIN=http://{backend_name}:{container_port} "
    f"LOG_GROUP=/aws/ec2/{app_name}/{environment}/{aws_region} "
    f"/opt/{app_name}/bin/restart-container.sh",
]

print(json.dumps({"commands": commands}))
PY

  echo "Restarting frontend container on ${instance_id} through SSM"
  command_id="$(aws ssm send-command \
    --region "$aws_region" \
    --instance-ids "$instance_id" \
    --document-name "AWS-RunShellScript" \
    --comment "Local frontend deploy ${APP_NAME}:${IMAGE_TAG}" \
    --parameters file:///tmp/wae-local-frontend-ssm-commands.json \
    --query 'Command.CommandId' \
    --output text)"

  wait_for_ssm_command "$aws_region" "$command_id"

  echo "Verifying frontend ${base_url}/"
  verify_frontend "$base_url"

  echo "Frontend deploy succeeded for ${aws_region}: ${base_url}/"
done

echo "Local frontend production deploy complete (${SHORT_SHA})"
