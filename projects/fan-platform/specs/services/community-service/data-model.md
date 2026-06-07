# community-service — Data Model

> Postgres 16. Multi-tenant by row (`tenant_id` column on every table).
> Schema is owned by Flyway (`spring.jpa.hibernate.ddl-auto=validate`).
> Source of truth: `src/main/resources/db/migration/community/V1__init.sql`.

---

## ER Overview

```
posts ───────< post_status_history (append-only audit)
   │
   ├──< comments
   ├──< reactions   (composite PK: post_id + reactor_account_id)
   │
follows  (composite PK: fan_account_id + artist_account_id; references logical accounts only)
outbox            (libs:java-messaging — at-least-once relay)
processed_events  (libs:java-messaging — inbox dedupe; required by Hibernate
                   schema-validation since java-messaging entities are
                   @EntityScan'd whenever the lib is on the classpath)
```

---

## Tables

### `posts`

| Column | Type | Notes |
|---|---|---|
| `id` | VARCHAR(36) PK | UUID v7 string |
| `tenant_id` | VARCHAR(64) NOT NULL | row-level isolation |
| `author_account_id` | VARCHAR(36) NOT NULL | logical FK to IAM account |
| `post_type` | VARCHAR(20) NOT NULL CHECK | `ARTIST_POST` / `FAN_POST` |
| `visibility` | VARCHAR(20) NOT NULL CHECK | `PUBLIC` / `MEMBERS_ONLY` / `PREMIUM` |
| `status` | VARCHAR(20) NOT NULL CHECK | `DRAFT` / `PUBLISHED` / `HIDDEN` / `DELETED` |
| `title` | VARCHAR(200) | nullable |
| `body` | TEXT | nullable for media-only posts |
| `media_refs` | JSONB | array of S3/MinIO keys (raw upload v2) |
| `published_at`, `created_at`, `updated_at`, `deleted_at` | TIMESTAMPTZ | UTC |
| `version` | BIGINT NOT NULL | optimistic lock |

Indexes:
- `idx_posts_tenant_status_published (tenant_id, status, published_at DESC)` — feed & status filtering
- `idx_posts_tenant_author (tenant_id, author_account_id, published_at DESC)` — author profile listing

### `post_status_history` (append-only audit)

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `post_id`, `tenant_id` | VARCHAR | tenant-prefixed for multi-tenant queries |
| `from_status`, `to_status` | VARCHAR(20) NOT NULL | |
| `actor_type` | VARCHAR(20) NOT NULL CHECK | `AUTHOR`/`OPERATOR`/`SYSTEM` |
| `actor_account_id` | VARCHAR(36) | nullable for SYSTEM |
| `reason` | VARCHAR(200) | optional human note |
| `occurred_at` | TIMESTAMPTZ NOT NULL | |

Index: `idx_psh_tenant_post_occurred (tenant_id, post_id, occurred_at DESC)`.

App-level convention: only INSERT — no UPDATE / DELETE statement appears anywhere in the codebase. (DB-level triggers or table-level grants enforce this in v2.)

### `comments`

| Column | Type | Notes |
|---|---|---|
| `id` | VARCHAR(36) PK | UUID v7 |
| `tenant_id` | VARCHAR(64) NOT NULL | |
| `post_id` | VARCHAR(36) NOT NULL | logical FK |
| `author_account_id` | VARCHAR(36) NOT NULL | |
| `body` | TEXT NOT NULL | |
| `created_at`, `deleted_at` | TIMESTAMPTZ | soft delete |
| `version` | BIGINT NOT NULL | |

Index: `idx_comments_tenant_post_created (tenant_id, post_id, created_at)`.

### `reactions`

| Column | Type | Notes |
|---|---|---|
| `post_id` | VARCHAR(36) PK part 1 | |
| `reactor_account_id` | VARCHAR(36) PK part 2 | |
| `tenant_id` | VARCHAR(64) NOT NULL | derived from parent post |
| `reaction_type` | VARCHAR(20) NOT NULL CHECK | `LIKE`/`LOVE`/`FIRE`/`SAD` |
| `created_at`, `updated_at` | TIMESTAMPTZ | |

Composite PK = (`post_id`, `reactor_account_id`). Idempotent upsert: a second
call from the same reactor mutates `reaction_type` in place (no DELETE).
Index: `idx_reactions_tenant_post (tenant_id, post_id)`.

### `follows`

| Column | Type | Notes |
|---|---|---|
| `fan_account_id` | VARCHAR(36) PK part 1 | |
| `artist_account_id` | VARCHAR(36) PK part 2 | |
| `tenant_id` | VARCHAR(64) NOT NULL | |
| `created_at` | TIMESTAMPTZ | |

Indexes: `idx_follows_tenant_artist (tenant_id, artist_account_id)` (artist profile follower list), `idx_follows_tenant_fan (tenant_id, fan_account_id)` (fan feed query).

### `outbox` / `processed_events`

Schemas are inherited verbatim from `libs:java-messaging`. See
`libs/java-messaging/src/main/java/com/example/messaging/outbox/` and
`projects/fan-platform/apps/community-service/src/main/resources/db/migration/community/V1__init.sql`.

---

## Logical FK / referential integrity

The community-service does NOT enforce FK constraints across services:
- `author_account_id` / `reactor_account_id` / `fan_account_id` / `artist_account_id` are IAM account UUIDs (TASK-FAN-BE-002 § Hard rules — "artist accounts are IAM accounts").
- The artist-service (TASK-FAN-BE-003) holds artist profile metadata; community-service treats those as logical references only.
- No `FOREIGN KEY` constraint is declared cross-table in v1 — keeping schemas independently deployable.

Within the service, parent/child rows (post → comment / reaction / status_history) ARE conceptually related but the FK is also expressed at the application layer rather than the database layer, matching the IAM reference. This trades referential safety for cross-table-rename flexibility; deletes of a post are status transitions (DELETED) not `DELETE` SQL, so orphans are not produced in normal operation.

---

## Multi-tenant rules (rules/traits/multi-tenant.md M2)

- **Every** table has `tenant_id`.
- **Every** index that supports query workload starts with `tenant_id`.
- **Every** repository method derived or queried via JPQL filters on `tenant_id`.
- Cross-tenant reads return empty results (NOT 403) so existence is not leaked.

---

## Optimistic locking

Both `posts` and `comments` carry `@Version BIGINT version`. Concurrent edits raise `ObjectOptimisticLockingFailureException` → 409 `CONFLICT` (see `presentation/advice/GlobalExceptionHandler`). The `reactions` table is in-place upsert — concurrent flips of the same `(post, reactor)` collapse into a single row whichever update wins last.

---

## Storage growth (rough estimate)

| Table | Average row size | Growth driver |
|---|---|---|
| `posts` | ~1–10 KB (body length) | per artist post / fan post |
| `comments` | ~0.5–2 KB | dominant volume — multiplicative on top of posts |
| `reactions` | ~120 B | bounded by `(post × unique reactor)` |
| `follows` | ~120 B | `(fans × artists)` |
| `post_status_history` | ~150 B | `≤ 5×` post count over lifetime |
| `outbox` | ~1 KB | drained continuously; archive table v2 |

A rough estimate: 1M MAU × 10 posts/year × 50 comments avg ≈ 500M comment rows / year. Postgres + monthly partition (v2) is sufficient at this scale.
