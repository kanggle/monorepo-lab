# membership-service — Data Model

> Postgres 16. Multi-tenant by row (`tenant_id` column on every table).
> Schema is owned by Flyway (`spring.jpa.hibernate.ddl-auto=validate`).
> Source of truth: `src/main/resources/db/migration/membership/V1__init.sql`
> (authored by TASK-FAN-BE-009 — this document is the design spec).

---

## ER Overview

```
memberships  (one windowed subscription per (account, tenant) lifecycle row)
   │
   └── (logical) accountId → IAM account; no cross-service FK

idempotency_keys  (subscribe idempotency — (tenant_id, account_id, idempotency_key))
outbox            (libs:java-messaging — at-least-once relay)
processed_events  (libs:java-messaging — inbox dedupe; required by Hibernate
                   schema-validation since java-messaging entities are
                   @EntityScan'd whenever the lib is on the classpath)
```

---

## Tables

### `memberships`

| Column | Type | Notes |
|---|---|---|
| `id` | VARCHAR(36) PK | UUID v7 string |
| `tenant_id` | VARCHAR(64) NOT NULL | row-level isolation (`fan-platform`) |
| `account_id` | VARCHAR(36) NOT NULL | the fan = IAM `sub` claim (logical FK) |
| `tier` | VARCHAR(20) NOT NULL CHECK | `MEMBERS_ONLY` / `PREMIUM` (`ck_membership_tier`) |
| `status` | VARCHAR(20) NOT NULL CHECK | `ACTIVE` / `CANCELED` (`ck_membership_status`) |
| `valid_from` | TIMESTAMPTZ NOT NULL | window start (subscribe time) |
| `valid_to` | TIMESTAMPTZ NOT NULL | window end = `valid_from + plan_months` |
| `plan_months` | INT NOT NULL CHECK (>= 1) | drives `valid_to` |
| `payment_ref` | VARCHAR(80) NOT NULL | PG mock authorization reference |
| `created_at` | TIMESTAMPTZ NOT NULL | UTC |
| `canceled_at` | TIMESTAMPTZ | nullable; set on CANCELED transition |
| `version` | BIGINT NOT NULL | optimistic lock |

Indexes:
- `idx_memberships_tenant_account_status (tenant_id, account_id, status)` — access-check point lookup + the caller's membership listing.
- `idx_memberships_tenant_account_validto (tenant_id, account_id, valid_to DESC)` — newest-window-first listing.

> **CHECK allow-list note** (feedback_spring_boot_diagnostic_patterns §16):
> the `tier` / `status` CHECK constraints fix the value set at the DB level. Adding
> a future tier/status value (e.g. a stored `EXPIRED`) requires a `V2` migration
> to extend the allow-list — a Docker-free `:check` slice will NOT catch a violation,
> so the Testcontainers IT is the authoritative gate.

### `idempotency_keys`

| Column | Type | Notes |
|---|---|---|
| `tenant_id` | VARCHAR(64) PK part 1 | |
| `account_id` | VARCHAR(36) PK part 2 | |
| `idempotency_key` | VARCHAR(80) PK part 3 | client-supplied `Idempotency-Key` header |
| `request_fingerprint` | VARCHAR(128) NOT NULL | hash of the subscribe payload — mismatch on replay → 409 `IDEMPOTENCY_KEY_CONFLICT` |
| `membership_id` | VARCHAR(36) NOT NULL | the membership produced by the first successful subscribe |
| `created_at` | TIMESTAMPTZ NOT NULL | |

Composite PK = (`tenant_id`, `account_id`, `idempotency_key`). A replay with the
same key + same fingerprint returns the stored `membership_id` result (idempotent);
same key + different fingerprint → 409.

### `outbox` / `processed_events`

Schemas are inherited verbatim from `libs:java-messaging`. See
`libs/java-messaging/src/main/java/com/example/messaging/outbox/` and
the membership-service `V1__init.sql`.

---

## Logical FK / referential integrity

The membership-service does NOT enforce FK constraints across services:
- `account_id` is a IAM account UUID (`sub` claim) — a logical reference only.
- No `FOREIGN KEY` constraint is declared cross-service — keeping schemas
  independently deployable (matches the community-service convention).

Within the service there is a single root table (`memberships`) plus the
idempotency + outbox/inbox infrastructure tables; there are no parent/child domain
relations to enforce.

---

## Multi-tenant rules (rules/traits/multi-tenant.md M2)

- **Every** table has `tenant_id`.
- **Every** index that supports query workload starts with `tenant_id`.
- **Every** repository method derived or queried via JPQL filters on `tenant_id`.
- Cross-tenant reads return empty results (NOT 403) so existence is not leaked;
  the access-check endpoint returns `allowed=false` for a cross-tenant lookup.

---

## Subscription windowing & expiry

- `valid_from` = subscribe time (`ClockPort.now()` truncated to micros).
- `valid_to` = `valid_from + plan_months` (whole months).
- **Expiry is read-time** — there is no stored `EXPIRED` status and no scheduled
  job that flips rows. A membership with `now > valid_to` stays `status=ACTIVE`
  in storage but is treated as inactive by § Access Semantics (architecture.md).
  This mirrors the delegation `isActiveAt` precedent and avoids a scheduler in v1.

> **Timestamp precision** (feedback_spring_boot_diagnostic_patterns §15):
> `valid_from` / `valid_to` use `ClockPort.now().truncatedTo(MICROS)` so the
> in-memory subscribe response equals the DB re-read (Postgres TIMESTAMPTZ stores
> microsecond precision; nanosecond `Instant.now()` would round-trip differently
> and fail an IT that compares the response body to a DB re-read).

---

## Optimistic locking

`memberships` carries `@Version BIGINT version`. Concurrent edits (e.g. two
cancel requests racing) raise `ObjectOptimisticLockingFailureException` → 409
`CONFLICT` (see `presentation/advice/GlobalExceptionHandler`). A re-cancel of an
already-CANCELED membership is an idempotent no-op handled before any write, so it
does not contend on the version.

---

## Storage growth (rough estimate)

| Table | Average row size | Growth driver |
|---|---|---|
| `memberships` | ~250 B | one lifecycle row per (account, subscription) — bounded by paying users × resubscribes |
| `idempotency_keys` | ~250 B | one per subscribe attempt; pruned after a retention window (v2) |
| `outbox` | ~1 KB | drained continuously; archive table v2 |

A rough estimate: even at 1M MAU with a low single-digit-% paid conversion and
occasional resubscribes, `memberships` stays in the low millions of rows over
years — a single Postgres table with the two tenant-leading indexes is ample.
