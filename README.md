# WoW Auction Engine

Backend service for ingesting, processing, and serving World of Warcraft auction-house data.

## What This Project Does

`auction-engine` is a Kotlin + Spring Boot application that:

- retrieves auction and realm data from Blizzard APIs
- processes and aggregates auction statistics
- stores data in MariaDB
- uses DynamoDB for additional local and AWS-backed storage flows
- exposes a health endpoint at `GET /health`
- runs scheduled background sync jobs on startup

## Stack

- Java 25
- Kotlin 2.3
- Spring Boot 3.5
- Maven Wrapper (`./mvnw`)
- MariaDB
- Floci for local AWS emulation (DynamoDB and S3)
- Testcontainers for tests

## New Developer Quick Start

### 1. Install prerequisites

- Java 25
- Docker

You do not need a separate Maven install. Use the checked-in Maven wrapper.

### 2. Create local environment variables

Copy the template and fill in the Blizzard credentials:

```bash
cp .env.example .env.local
```

Then load it into your shell:

```bash
set -a
source .env.local
set +a
```

Required for local startup:

- `BLIZZARD_CLIENT_ID`
- `BLIZZARD_CLIENT_SECRET`
- `WAE_BLIZZARD_REGIONS`

For local development, AWS settings default to obvious dummy values:

- `WAE_AWS_REGION=eu-west-1`
- `AWS_ACCESS_KEY=local-dev-key`
- `AWS_SECRET_KEY=local-dev-secret`

You only need to export those if you want to override the defaults.

Use `Europe` for `WAE_BLIZZARD_REGIONS` unless you are intentionally changing regions. The canonical format is a comma-separated list such as `Europe` or `Korea,Taiwan`. Supported values come from the `Region` enum:

- `Europe`
- `NorthAmerica`
- `Korea`
- `Taiwan`

### 3. Start local databases

The default local config expects:

- MariaDB on `localhost:59000`
- Floci on `localhost:4566`

Start both with:

```bash
docker compose -f docker-compose-db.yml up -d
```

Floci persistence uses a Docker-managed named volume. If you previously ran an older setup that bind-mounted `./docker/floci`, recreate the container once so it stops using the old host directory:

```bash
docker compose -f docker-compose-db.yml down -v
docker compose -f docker-compose-db.yml up -d
```

The MariaDB container creates the `dbo` database automatically from [`docker/initdb/01-init-schema.sql`](docker/initdb/01-init-schema.sql).

### 4. Run the application

```bash
./mvnw spring-boot:run
```

When the app starts successfully:

- the HTTP server is available on `http://localhost:8080`
- the health endpoint is `http://localhost:8080/health`
- scheduled jobs are enabled
- Hibernate updates the MariaDB schema automatically on startup

## Health Checks

`GET /health` is a liveness-style endpoint for the web process and scheduler progress.

- returns `204 No Content` when the app is up and no scheduler batch appears stuck
- returns `503 Service Unavailable` when a scheduled update batch has stopped making progress longer than the configured threshold
- returns a bare `503` to callers; detailed unhealthy reasons are only written to server logs

The default stalled-update threshold is `PT20M` and is configured in [`src/main/resources/application.yml`](src/main/resources/application.yml) as `app.health.stuck-update-threshold`.

This endpoint is intentionally stricter than a pure "process is running" check. A JVM that is alive but stuck in GC, CPU saturation, or a blocked update path should eventually fail `/health`.

### 5. Stop local databases

```bash
docker compose -f docker-compose-db.yml down
```

## Environment Variables

### Local development

| Variable | Required | Example | Notes |
| --- | --- | --- | --- |
| `BLIZZARD_CLIENT_ID` | Yes | `your-blizzard-client-id` | Used to fetch OAuth tokens from Blizzard. |
| `BLIZZARD_CLIENT_SECRET` | Yes | `your-blizzard-client-secret` | Used together with the client ID. |
| `WAE_BLIZZARD_REGIONS` | Yes | `Europe` or `Korea,Taiwan` | Comma-separated app enum values, not `eu`/`kr`. |
| `WAE_BLIZZARD_REGION` | No | `Europe` | Deprecated compatibility fallback for single-region setups. |
| `WAE_AWS_REGION` | No | `eu-west-1` | Optional locally; defaults to `eu-west-1`. |
| `AWS_ACCESS_KEY` | No | `local-dev-key` | Optional locally; defaults to a dummy value. |
| `AWS_SECRET_KEY` | No | `local-dev-secret` | Optional locally; defaults to a dummy value. |

### Production-only overrides

These are only needed for the `production` Spring profile because [`src/main/resources/application.production.yml`](src/main/resources/application.production.yml) overrides the datasource credentials:

- `AUCTION_ENGINE_DB_USERNAME`
- `AUCTION_ENGINE_DB_PASSWORD`

## Local Configuration Defaults

The default local datasource configuration lives in [`src/main/resources/application.yml`](src/main/resources/application.yml):

- MariaDB URL: `jdbc:mariadb://localhost:59000/dbo`
- MariaDB username: `root`
- MariaDB password: `root`
- DynamoDB endpoint: `http://localhost:4566`
- S3 endpoint: `http://localhost:4566`
- AWS region: `eu-west-1`
- AWS access key: `local-dev-key`
- AWS secret key: `local-dev-secret`

Other local runtime defaults:

- scheduler enabled: `app.scheduling.enabled=true`
- stalled update threshold for `/health`: `app.health.stuck-update-threshold=PT20M`

That means a new developer normally does not need to set any database environment variables for local work.

## Region Model

The app deployment region and the S3 bucket region are related but not identical configuration concerns.

- `WAE_AWS_REGION` is the region where the application instance runs.
- `WAE_BLIZZARD_REGIONS` is the Blizzard data scope that instance processes.
- S3 bucket name and bucket AWS region are resolved internally from app config per Blizzard region.

Current production layout:

- Europe deployment: `eu-west-1`, updates `Europe`, writes to `wah-data-eu` in `eu-west-1`
- North America deployment: `us-west-1`, updates `NorthAmerica`, writes to `wah-data-us` in `us-west-1`
- Asia deployment: `ap-northeast-2`, updates `Korea,Taiwan`, writes to `wah-data-as` in `ap-northeast-2`

## Running Tests

Run the full test suite with:

```bash
./mvnw test
```

Useful detail for onboarding:

- tests run with the `test` Spring profile
- Blizzard credentials are stubbed in [`src/main/resources/application.test.yml`](src/main/resources/application.test.yml)
- MariaDB runs through Testcontainers
- DynamoDB and S3 are provided through Floci-backed Testcontainers in integration tests
- Docker Desktop or another working Docker daemon must be running for tests to pass
- `WAE_BLIZZARD_REGION` is still accepted as a fallback, but new config and deployment work should use `WAE_BLIZZARD_REGIONS`

## Profession/Recipe Test Fixtures

Profession and recipe integration fixtures now use a structured layout under [`src/test/resources/blizzard`](src/test/resources/blizzard):

- [`src/test/resources/blizzard/profession/index-response.json`](src/test/resources/blizzard/profession/index-response.json)
- [`src/test/resources/blizzard/profession/details`](src/test/resources/blizzard/profession/details) for `GET /profession/{id}`
- [`src/test/resources/blizzard/profession/skill-tier`](src/test/resources/blizzard/profession/skill-tier) for `GET /profession/{id}/skill-tier/{skillTierId}`
- [`src/test/resources/blizzard/recipe/details`](src/test/resources/blizzard/recipe/details) for `GET /recipe/{id}`
- [`src/test/resources/blizzard/profession-recipe-sample-manifest.json`](src/test/resources/blizzard/profession-recipe-sample-manifest.json) describing sampled tiers and recipe IDs

Current sample policy:

- professions: Blacksmithing, Enchanting, Herbalism, Fishing
- tiers: Midnight + one older tier per profession
- recipe sampling: 5-10 recipes per selected skill tier (default 6)

Refresh the checked-in Blizzard fixtures from the project root with:

```bash
./mvnw exec:exec@refresh-fixtures
```

Pass script flags through Maven with `-Drefresh.fixtures.args=...`, for example:

```bash
./mvnw exec:exec@refresh-fixtures '-Drefresh.fixtures.args=--dry-run --profession-id 164'
```

This command requires a local `node` binary. Override it with `-Dnode.executable=/path/to/node` if your shell does not expose `node` on `PATH`.

## Useful Commands

Run the app:

```bash
./mvnw spring-boot:run
```

Run tests:

```bash
./mvnw test
```

Run the verification lifecycle:

```bash
./mvnw verify
```

Enable the repo-managed pre-commit hook:

```bash
git config core.hooksPath .githooks
```

Hooks are opt-in per clone. If `core.hooksPath` is not set, Git will not run the repo-managed hook on commit.

Verify the hook path:

```bash
git config --get core.hooksPath
```

Expected output:

```text
.githooks
```

The pre-commit hook runs `ktlint:format`, re-stages any staged Kotlin autofixes, and then runs `ktlint:check` to block the commit if lint violations remain.

Format Kotlin sources:

```bash
./mvnw ktlint:format
```

## Documentation

- [Production memory footprint and cost action criteria](docs/production-memory-footprint.md)
- [AWS deployment and regional operations](infra/README.md)

## AWS Deployment

This repository includes a GitHub Actions based production deployment flow for AWS.

The deployment path is designed for small regional EC2 instances running Docker, not Kubernetes. The current infrastructure and workflow files are documented in [infra/README.md](infra/README.md).

At a high level:

- pushes to `master` run `Backend PR Checks`
- the backend workflow first classifies changes and skips the expensive verify job when the change is clearly irrelevant
- `Deploy Production` starts after a successful `master` backend run and uses the same conservative change classification
- app-only changes do image build, ECR push, and SSM restart
- infra-affecting changes also run CloudFormation before the app rollout
- clearly irrelevant changes leave the expensive jobs in a real `skipped` state
- manual infrastructure-only syncs can be triggered with `.github/workflows/manual-infra-sync.yml`

Forks can use the same flow, but must create their own AWS IAM role, GitHub secrets, and environment configuration.

## Project Structure

Main application code lives under [`src/main/kotlin/net/jonasmf/auctionengine`](src/main/kotlin/net/jonasmf/auctionengine):

- `config/`: Spring configuration and external service wiring
- `integration/`: Blizzard API integrations
- `service/`: application logic and scheduled jobs
- `repository/`: MariaDB and DynamoDB repositories
- `dbo/` and `dto/`: persistence and transfer models
- `utility/`: shared helpers

Resources:

- [`src/main/resources/application.yml`](src/main/resources/application.yml)
- [`src/main/resources/application.test.yml`](src/main/resources/application.test.yml)
- [`src/main/resources/application.production.yml`](src/main/resources/application.production.yml)

## Troubleshooting

### Application fails on missing placeholders

Make sure you loaded `.env.local` into the same shell where you run `./mvnw spring-boot:run`.

### MariaDB connection refused

Start the local containers:

```bash
docker compose -f docker-compose-db.yml up -d
```

### Floci S3 access denied under `/app/data/s3`

This was caused by an older bind-mounted `./docker/floci` directory with host-only permissions. The current setup uses a named Docker volume instead.

Recreate the Floci container and its volume:

```bash
docker compose -f docker-compose-db.yml down -v
docker compose -f docker-compose-db.yml up -d
```

### Tests fail before Spring starts

Check that Docker Desktop or your Docker daemon is running. The tests depend on Testcontainers and Floci, not on your manually started compose services.

### AWS SDK deprecation warning

The application should not initialize AWS SDK for Java 1.x anymore. If you see a startup warning mentioning `AWS SDK for Java 1.x`, an old dependency has been reintroduced.

Current local AWS integrations are:

- DynamoDB through Floci + AWSpring `DynamoDbOperations`
- S3 through Floci locally and the AWS SDK for Kotlin `S3Client`

### `/health` returns `503`

This now means the app believes a scheduled update batch has stalled longer than the configured threshold, not just that the HTTP server is down.

Typical causes:

- JVM memory pressure or GC thrash
- CPU saturation during auction aggregation
- blocked external I/O such as Blizzard download, S3 upload, or database writes

Start by checking:

- recent application logs for stage markers around auction download, S3 upload, and hourly stats processing
- JVM GC logs if `JAVA_TOOL_OPTIONS` includes `-Xlog:gc*`
- container or host memory pressure if you are running inside Docker or EC2
