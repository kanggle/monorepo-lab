---
name: migration-strategy
description: Flyway migration management
category: database
---

# Skill: Migration Strategy

Patterns for Flyway migration management across services.

Prerequisite: read `specs/platform/coding-rules.md` (Database section) before using this skill.

---

## Flyway Configuration

Each service has its own database and independent migration history.

```yaml
# application.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
```

Standalone profile disables Flyway (H2 uses JPA auto-DDL):

```yaml
# application-standalone.yml
spring:
  flyway:
    enabled: false
  jpa:
    hibernate:
      ddl-auto: update
```

---

## Migration Structure Per Service

```
apps/auth-service/src/main/resources/db/migration/
├── V1__create_users_table.sql
├── V2__add_email_index.sql
├── V3__create_refresh_tokens_table.sql
└── V4__create_audit_log_table.sql

apps/order-service/src/main/resources/db/migration/
├── V1__create_orders_table.sql
├── V2__create_order_items_table.sql
├── V3__add_shipping_address.sql
└── V5__create_outbox_table.sql
```

---

## Writing Migrations

### Table Creation

```sql
CREATE TABLE users (
    id            UUID         NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX idx_users_email ON users (email);
```

### Adding Columns

```sql
-- Nullable column — safe for existing data
ALTER TABLE orders ADD COLUMN payment_id VARCHAR(255);

-- NOT NULL with default — safe on PostgreSQL 11+
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```

### Adding Constraints

```sql
ALTER TABLE payments ADD CONSTRAINT uq_payments_order_id UNIQUE (order_id);
```

---

## Rules

- One migration file per logical change.
- Never modify an already-applied migration.
- Migrations must be PostgreSQL-compatible (not H2).
- Always include indexes for new foreign keys.
- Test migrations with Testcontainers integration tests.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Editing applied migration | Flyway checksum mismatch — create new version |
| Large data migration in DDL file | Split DDL and DML into separate versions |
| Missing `NOT NULL` on required columns | Add constraint at creation time |
| No integration test after migration change | Run `*IntegrationTest` to verify schema |
