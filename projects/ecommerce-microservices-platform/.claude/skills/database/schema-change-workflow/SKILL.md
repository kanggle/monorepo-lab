---
name: schema-change-workflow
description: Database schema changes with Flyway
category: database
---

# Skill: Schema Change Workflow

Patterns for safe database schema changes using Flyway migrations.

Prerequisite: read `specs/platform/coding-rules.md` (Database section) before using this skill.

---

## Migration File Convention

Location: `apps/{service}/src/main/resources/db/migration/`

Naming: `V{N}__{description}.sql` (double underscore)

```
V1__create_users_table.sql
V2__add_email_index.sql
V3__create_refresh_tokens_table.sql
V4__add_user_status_column.sql
```

---

## Creating a New Migration

1. Check the latest version number in `db/migration/`.
2. Create the next version file.
3. Write idempotent-safe SQL (avoid `IF NOT EXISTS` — Flyway tracks versions).
4. Test locally with the service's integration tests.

```sql
-- V5__create_outbox_table.sql
CREATE TABLE outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(100)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         TEXT            NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_outbox_status_created ON outbox (status, created_at);
```

---

## Safe Schema Change Rules

| Change Type | Safe? | Notes |
|---|---|---|
| Add column (nullable) | Yes | No lock, no data rewrite |
| Add column (NOT NULL + default) | Yes | PostgreSQL 11+ avoids full rewrite |
| Add index | Yes | Use `CREATE INDEX CONCURRENTLY` for large tables |
| Drop column | Caution | Remove all code references first |
| Rename column | No | Use add → migrate → drop instead |
| Change column type | No | Use add → migrate → drop instead |
| Drop table | Caution | Ensure no references remain |

---

## Adding Indexes

Always add indexes for:
- Foreign key columns
- Columns used in `WHERE` clauses
- Columns used in `ORDER BY` with pagination
- Unique constraints

```sql
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_orders_user_status ON orders (user_id, status);
CREATE UNIQUE INDEX uq_payments_order_id ON payments (order_id);
```

---

## Standalone Profile (H2)

For `standalone` profile, H2 auto-creates tables from JPA entities. Flyway migrations target PostgreSQL syntax. Ensure JPA entities match the migration schema.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Editing an already-applied migration | Flyway checksum will fail — create a new version instead |
| Missing index on foreign key | Always index FK columns |
| `NOT NULL` column without default on existing table | Add as nullable first, backfill, then add constraint |
| Using H2-specific SQL in migrations | Migrations target PostgreSQL only |
| Skipping version numbers | Use sequential numbers — gaps cause confusion |
