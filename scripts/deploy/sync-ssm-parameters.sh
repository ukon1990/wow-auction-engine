#!/usr/bin/env bash
set -euo pipefail

required_vars=(
  APP_NAME
  ENVIRONMENT
  AWS_REGION
  BLIZZARD_REGION
  BLIZZARD_CLIENT_ID
  BLIZZARD_CLIENT_SECRET
  AUCTION_ENGINE_DB_URL
  AUCTION_ENGINE_DB_USERNAME
  AUCTION_ENGINE_DB_PASSWORD
)

for var_name in "${required_vars[@]}"; do
  if [[ -z "${!var_name:-}" ]]; then
    echo "Missing required environment variable: $var_name" >&2
    exit 1
  fi
done

parameter_prefix="/${APP_NAME}/${ENVIRONMENT}/${AWS_REGION}"

put_string() {
  local name="$1"
  local value="$2"
  aws ssm put-parameter \
    --region "$AWS_REGION" \
    --name "${parameter_prefix}/${name}" \
    --type String \
    --value "$value" \
    --overwrite >/dev/null
}

put_secure_string() {
  local name="$1"
  local value="$2"
  aws ssm put-parameter \
    --region "$AWS_REGION" \
    --name "${parameter_prefix}/${name}" \
    --type SecureString \
    --value "$value" \
    --overwrite >/dev/null
}

put_optional_string() {
  local name="$1"
  local value="${2:-}"
  if [[ -n "$value" ]]; then
    put_string "$name" "$value"
  fi
}

put_optional_secure_string() {
  local name="$1"
  local value="${2:-}"
  if [[ -n "$value" ]]; then
    put_secure_string "$name" "$value"
  fi
}

put_string "SPRING_PROFILES_ACTIVE" "production"
put_string "WAE_AWS_REGION" "$AWS_REGION"
put_string "WAE_BLIZZARD_REGION" "$BLIZZARD_REGION"
put_string "AUCTION_ENGINE_DB_URL" "$AUCTION_ENGINE_DB_URL"
put_string "JVM_MAX_RAM_PERCENTAGE" "${JVM_MAX_RAM_PERCENTAGE:-65.0}"
put_optional_string "JAVA_TOOL_OPTIONS" "${JAVA_TOOL_OPTIONS:-}"
put_optional_string "WAE_DYNAMODB_ENDPOINT" "${WAE_DYNAMODB_ENDPOINT:-}"
put_optional_string "WAE_S3_ENDPOINT" "${WAE_S3_ENDPOINT:-}"

put_secure_string "BLIZZARD_CLIENT_ID" "$BLIZZARD_CLIENT_ID"
put_secure_string "BLIZZARD_CLIENT_SECRET" "$BLIZZARD_CLIENT_SECRET"
put_secure_string "AUCTION_ENGINE_DB_USERNAME" "$AUCTION_ENGINE_DB_USERNAME"
put_secure_string "AUCTION_ENGINE_DB_PASSWORD" "$AUCTION_ENGINE_DB_PASSWORD"
put_optional_secure_string "AWS_ACCESS_KEY" "${AWS_ACCESS_KEY:-}"
put_optional_secure_string "AWS_SECRET_KEY" "${AWS_SECRET_KEY:-}"

echo "Synchronized runtime parameters under ${parameter_prefix}"
