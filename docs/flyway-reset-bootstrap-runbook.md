# Flyway Reset Bootstrap Runbook

This runbook resets an environment and rebuilds schema from the new bootstrap migration:

- `src/main/resources/db/migration/V1__bootstrap_schema.sql`
- `src/main/resources/db/migration/R__create_v_auction_house_prices.sql`

## Preconditions

- Maintenance window approved (staging/production).
- Database credentials available in environment variables.
- Application artifact for this branch built and ready.

## 1) Backup current database state

Use `scripts/db/reset-and-bootstrap.sh` to create:

- full data dump
- schema-only dump
- `flyway_schema_history` dump

Example:

```bash
BACKUP_DIR=backups/production \
DB_HOST=<host> \
DB_PORT=3306 \
DB_NAME=<db_name> \
DB_USER=<user> \
DB_PASSWORD=<password> \
ALLOW_PRODUCTION_RESET=true \
./scripts/db/reset-and-bootstrap.sh production
```

## 2) Reset database

The script drops and recreates the schema. This is destructive by design.

For local:

```bash
./scripts/db/reset-and-bootstrap.sh local
```

For staging:

```bash
DB_HOST=<staging-host> DB_NAME=<staging-db> DB_USER=<user> DB_PASSWORD=<password> ./scripts/db/reset-and-bootstrap.sh staging
```

For production:

```bash
DB_HOST=<prod-host> DB_NAME=<prod-db> DB_USER=<user> DB_PASSWORD=<password> ALLOW_PRODUCTION_RESET=true ./scripts/db/reset-and-bootstrap.sh production
```

## 3) Bootstrap with Flyway

Start the application once the schema is empty:

```bash
BLIZZARD_CLIENT_ID=<id> BLIZZARD_CLIENT_SECRET=<secret> WAE_BLIZZARD_REGIONS=Europe ./mvnw spring-boot:run
```

Flyway creates `flyway_schema_history`, applies `V1__bootstrap_schema.sql`, then applies repeatable objects.

## 4) Post-bootstrap verification

Run these checks:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SHOW TABLES;
SHOW CREATE VIEW v_auction_house_prices;
```

Application checks:

- `GET /health` returns `204`.
- Scheduler starts and no migration/DDL errors appear in logs.

## 5) Rollback

If bootstrapping fails, restore the full dump from backup and redeploy the previous working artifact.
