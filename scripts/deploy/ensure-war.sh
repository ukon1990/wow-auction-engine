#!/usr/bin/env bash
set -euo pipefail

war_ready=false

if [[ -n "${WORKFLOW_RUN_ID:-}" ]]; then
  api_url="https://api.github.com/repos/${GITHUB_REPOSITORY}/actions/runs/${WORKFLOW_RUN_ID}/artifacts"
  artifact_id="$(curl -fsSL \
    -H "Authorization: Bearer ${GITHUB_TOKEN}" \
    -H "Accept: application/vnd.github+json" \
    "$api_url" | python -c "import json,sys; data=json.load(sys.stdin); arts=[a for a in data['artifacts'] if a['name']=='backend-war' and not a['expired']]; print(arts[0]['id'] if arts else '')")"

  if [[ -n "$artifact_id" ]]; then
    curl -fsSL \
      -H "Authorization: Bearer ${GITHUB_TOKEN}" \
      -H "Accept: application/vnd.github+json" \
      "https://api.github.com/repos/${GITHUB_REPOSITORY}/actions/artifacts/${artifact_id}/zip" \
      -o /tmp/backend-war.zip
    rm -rf /tmp/backend-war
    mkdir -p /tmp/backend-war
    unzip -q /tmp/backend-war.zip -d /tmp/backend-war
    war_path="$(find /tmp/backend-war -maxdepth 2 -name '*.war' | head -n 1)"
    if [[ -n "$war_path" ]]; then
      cp "$war_path" ./app.war
      war_ready=true
      echo "Using backend-war artifact from workflow run ${WORKFLOW_RUN_ID}"
    else
      echo "backend-war artifact from workflow run ${WORKFLOW_RUN_ID} did not contain a WAR file; falling back to local package build."
    fi
  else
    echo "No backend-war artifact found for workflow run ${WORKFLOW_RUN_ID}; falling back to local package build."
  fi
fi

if [[ "$war_ready" != "true" ]]; then
  echo "Building WAR locally without tests or lint."
  chmod +x ./mvnw
  ./mvnw -B -ntp "-Dkotlin.compiler.daemon=false" -DskipTests package
  war_path="$(find target -maxdepth 1 -name '*.war' | head -n 1)"
  if [[ -z "$war_path" ]]; then
    echo "Local build did not produce a war file" >&2
    exit 1
  fi
  cp "$war_path" ./app.war
fi

if [[ ! -f ./app.war ]]; then
  echo "WAR file was not prepared" >&2
  exit 1
fi
