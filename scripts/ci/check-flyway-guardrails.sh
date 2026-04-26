#!/usr/bin/env bash
set -euo pipefail

echo "Checking Hibernate DDL guardrails..."
if rg --line-number "ddl-auto:\\s*update|hbm2ddl:\\s*|hbm2ddl\\.auto:\\s*update" src/main/resources/application*.yml; then
  echo "Found disallowed Hibernate schema-mutation settings in application config."
  exit 1
fi

echo "Checking migration layout guardrails..."
versioned_count="$(find src/main/resources/db/migration -maxdepth 1 -type f -name 'V*.sql' | wc -l | tr -d ' ')"
if [[ "${versioned_count}" -lt 1 ]]; then
  echo "Expected at least one versioned migration in src/main/resources/db/migration."
  exit 1
fi

if [[ ! -f src/main/resources/db/migration/V1__bootstrap_schema.sql ]]; then
  echo "Missing required bootstrap migration V1__bootstrap_schema.sql."
  exit 1
fi

echo "Flyway guardrails passed."
