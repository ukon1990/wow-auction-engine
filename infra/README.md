# AWS Deployment

This repository includes a minimal single-instance regional deployment path for the WoW Auction Engine.

## Layout

- `infra/regions.json`: enabled regions and per-region overrides
- `infra/cloudformation/app-region.yaml`: reusable per-region CloudFormation stack
- `scripts/deploy/sync-ssm-parameters.sh`: writes runtime configuration to SSM Parameter Store
- `scripts/deploy/restart-container.sh`: restarts the app container on the EC2 instance through SSM
- `.github/workflows/deploy-production.yml`: orchestrates production deployment from `master`

## Required GitHub Secrets and Variables

Repository or environment secrets:

- `AWS_DEPLOY_ROLE_ARN`
- `PROD_BLIZZARD_CLIENT_ID`
- `PROD_BLIZZARD_CLIENT_SECRET`
- `PROD_AUCTION_ENGINE_DB_URL`
- `PROD_AUCTION_ENGINE_DB_USERNAME`
- `PROD_AUCTION_ENGINE_DB_PASSWORD`

Optional secrets:

- `PROD_AWS_ACCESS_KEY`
- `PROD_AWS_SECRET_KEY`
- `PROD_WAE_DYNAMODB_ENDPOINT`
- `PROD_WAE_S3_ENDPOINT`
- `PROD_JAVA_TOOL_OPTIONS`

The runtime configuration is written to SSM under:

`/<app-name>/<environment>/<aws-region>/...`

Example:

`/wow-auction-engine/prod/eu-west-1/BLIZZARD_CLIENT_ID`
