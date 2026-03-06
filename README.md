# WoW Auction Engine

Backend service for ingesting, processing, and serving World of Warcraft auction-house data.

## What This Project Does

`auction-engine` is a Kotlin + Spring Boot application that:

- retrieves auction and realm data from Blizzard APIs
- processes and aggregates auction statistics
- persists data in MariaDB (and supports DynamoDB integrations)
- exposes operational endpoints such as `GET /health`
- runs scheduled background sync jobs

## Tech Stack

- Java 21
- Kotlin 2.2
- Spring Boot 3.5
- Spring Data JPA + MariaDB
- AWS SDK (Kotlin + Java), Spring Cloud AWS
- Maven + ktlint
- Testcontainers for integration tests

## Quick Start

### Prerequisites

- JDK 21
- Maven 3.9+
- Docker (optional, for local MariaDB)

### 1) Start a local MariaDB instance

The default local config expects MariaDB on `localhost:54000`.

Option A: use the provided compose file:

```bash
docker compose -f docker-compose-db.yml up -d
```

Option B: use your own MariaDB instance and update `spring.datasource.*` in `application.yml`.

### 2) Configure environment variables

Set these before running:

- `BLIZZARD_CLIENT_ID`
- `BLIZZARD_CLIENT_SECRET`
- `AWS_ACCESS_KEY`
- `AWS_SECRET_KEY`

Optional production DB credentials:

- `AUCTION_ENGINE_DB_USERNAME`
- `AUCTION_ENGINE_DB_PASSWORD`

### 3) Run the application

```bash
mvn spring-boot:run
```

The service starts with scheduling enabled and health available at:

- `http://localhost:8080/health`

## Testing and Quality

Run tests:

```bash
mvn test
```

Run full verification (includes ktlint check in `verify` phase):

```bash
mvn verify
```

Auto-format Kotlin with ktlint:

```bash
mvn ktlint:format
```

## Configuration Profiles

- `application.yml`: default/local configuration
- `application-test.yml`: test configuration with dummy credentials and mocked Blizzard endpoints
- `application.production.yml`: production datasource overrides

## Project Structure

Main code is under `src/main/kotlin/net/jonasmf/auctionengine`:

- `service/`: scheduled jobs and business logic
- `integration/`: Blizzard API integration
- `repository/`: JPA, JDBC, and DynamoDB data access
- `dbo/` and `dto/`: persistence models and transfer objects
- `utility/`: processing helpers and shared utilities

Resources:

- `src/main/resources/application*.yml`
- `src/main/resources/original-db/original-db-dll.sql`

## Current Status

This README is a starter guide. As the API surface grows, add:

- endpoint catalog
- architecture diagram
- deployment instructions
- observability/runbook notes
