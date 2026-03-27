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
- DynamoDB Local for local development through AWSpring + AWS SDK v2
- Testcontainers + LocalStack for tests

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
- `WAE_BLIZZARD_REGION`

For local development, AWS settings default to obvious dummy values:

- `WAE_AWS_REGION=eu-west-1`
- `AWS_ACCESS_KEY=local-dev-key`
- `AWS_SECRET_KEY=local-dev-secret`

You only need to export those if you want to override the defaults.

Use `Europe` for `WAE_BLIZZARD_REGION` unless you are intentionally changing regions. Supported values come from the `Region` enum:

- `Europe`
- `NorthAmerica`
- `Korea`
- `Taiwan`

### 3. Start local databases

The default local config expects:

- MariaDB on `localhost:59000`
- DynamoDB Local on `localhost:58000`

Start both with:

```bash
docker compose -f docker-compose-db.yml up -d
```

The MariaDB container creates the `dbo` database automatically from [`docker/initdb/01-init-schema.sql`](/Users/jonas/Dev/Hobby/wow-auction-engine/docker/initdb/01-init-schema.sql).

### 4. Run the application

```bash
./mvnw spring-boot:run
```

When the app starts successfully:

- the HTTP server is available on `http://localhost:8080`
- the health endpoint is `http://localhost:8080/health`
- scheduled jobs are enabled
- Hibernate updates the MariaDB schema automatically on startup

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
| `WAE_BLIZZARD_REGION` | Yes | `Europe` | Must match the app enum values, not `eu`. |
| `WAE_AWS_REGION` | No | `eu-west-1` | Optional locally; defaults to `eu-west-1`. |
| `AWS_ACCESS_KEY` | No | `local-dev-key` | Optional locally; defaults to a dummy value. |
| `AWS_SECRET_KEY` | No | `local-dev-secret` | Optional locally; defaults to a dummy value. |

### Production-only overrides

These are only needed for the `production` Spring profile because [`src/main/resources/application.production.yml`](/Users/jonas/Dev/Hobby/wow-auction-engine/src/main/resources/application.production.yml) overrides the datasource credentials:

- `AUCTION_ENGINE_DB_USERNAME`
- `AUCTION_ENGINE_DB_PASSWORD`

## Local Configuration Defaults

The default local datasource configuration lives in [`src/main/resources/application.yml`](/Users/jonas/Dev/Hobby/wow-auction-engine/src/main/resources/application.yml):

- MariaDB URL: `jdbc:mariadb://localhost:59000/dbo`
- MariaDB username: `root`
- MariaDB password: `root`
- DynamoDB Local endpoint: `http://localhost:58000`
- AWS region: `eu-west-1`
- AWS access key: `local-dev-key`
- AWS secret key: `local-dev-secret`

That means a new developer normally does not need to set any database environment variables for local work.

## Running Tests

Run the full test suite with:

```bash
./mvnw test
```

Useful detail for onboarding:

- tests run with the `test` Spring profile
- Blizzard credentials are stubbed in [`src/main/resources/application.test.yml`](/Users/jonas/Dev/Hobby/wow-auction-engine/src/main/resources/application.test.yml)
- MariaDB runs through Testcontainers
- DynamoDB is provided through LocalStack in tests
- Docker Desktop or another working Docker daemon must be running for tests to pass

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

Format Kotlin sources:

```bash
./mvnw ktlint:format
```

## Project Structure

Main application code lives under [`src/main/kotlin/net/jonasmf/auctionengine`](/Users/jonas/Dev/Hobby/wow-auction-engine/src/main/kotlin/net/jonasmf/auctionengine):

- `config/`: Spring configuration and external service wiring
- `integration/`: Blizzard API integrations
- `service/`: application logic and scheduled jobs
- `repository/`: MariaDB and DynamoDB repositories
- `dbo/` and `dto/`: persistence and transfer models
- `utility/`: shared helpers

Resources:

- [`src/main/resources/application.yml`](/Users/jonas/Dev/Hobby/wow-auction-engine/src/main/resources/application.yml)
- [`src/main/resources/application.test.yml`](/Users/jonas/Dev/Hobby/wow-auction-engine/src/main/resources/application.test.yml)
- [`src/main/resources/application.production.yml`](/Users/jonas/Dev/Hobby/wow-auction-engine/src/main/resources/application.production.yml)

## Troubleshooting

### Application fails on missing placeholders

Make sure you loaded `.env.local` into the same shell where you run `./mvnw spring-boot:run`.

### MariaDB connection refused

Start the local containers:

```bash
docker compose -f docker-compose-db.yml up -d
```

### Tests fail before Spring starts

Check that Docker Desktop or your Docker daemon is running. The tests depend on Testcontainers and LocalStack, not on your manually started compose services.

### AWS SDK deprecation warning

The application should not initialize AWS SDK for Java 1.x anymore. If you see a startup warning mentioning `AWS SDK for Java 1.x`, an old dependency has been reintroduced.

Current local AWS integrations are:

- DynamoDB Local through AWSpring `DynamoDbOperations` on AWS SDK v2
- S3 uploads/downloads through the AWS SDK for Kotlin `S3Client`
