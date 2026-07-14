# artist-service — Data Model

> Persistent schema declared by Flyway in
> `apps/artist-service/src/main/resources/db/migration/artist/V1__init.sql`.
> JPA entities under `adapter/out/persistence/` mirror this schema; the domain
> aggregates (`domain/{artist,group,fandom}`) are framework-free.

---

## Tables

### `artists` — aggregate root

| Column | Type | Notes |
|---|---|---|
| `id` | VARCHAR(36) PK | UUID v7 (TASK-MONO-025 머지 완료, BaseEventPublisher) |
| `tenant_id` | VARCHAR(64) NOT NULL | `fan-platform` v1; isolation key |
| `artist_type` | VARCHAR(20) NOT NULL | `SOLO` / `GROUP_MEMBER` |
| `status` | VARCHAR(20) NOT NULL | `DRAFT` / `PUBLISHED` / `ARCHIVED` |
| `stage_name` | VARCHAR(120) NOT NULL | UNIQUE within tenant |
| `real_name` | VARCHAR(120) | optional |
| `debut_date` | DATE | optional |
| `agency` | VARCHAR(120) | optional |
| `bio` | TEXT | optional |
| `profile_image_ref` | VARCHAR(500) | media URL only (F5 — no binary in DB) |
| `created_at` | TIMESTAMPTZ NOT NULL | |
| `updated_at` | TIMESTAMPTZ NOT NULL | |
| `published_at` | TIMESTAMPTZ | set on DRAFT → PUBLISHED |
| `archived_at` | TIMESTAMPTZ | set on → ARCHIVED |
| `version` | BIGINT NOT NULL DEFAULT 0 | optimistic lock |

**Constraints**:
- `ck_artists_artist_type CHECK (artist_type IN ('SOLO','GROUP_MEMBER'))`
- `ck_artists_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'))`
- `uq_artists_tenant_stage_name UNIQUE (tenant_id, stage_name)` ← edge case guard

**Indexes**:
- `idx_artists_tenant_status_stage_name (tenant_id, status, stage_name)` — directory listing
- `idx_artists_tenant_type_status (tenant_id, artist_type, status)` — type filter

### `artist_groups`

| Column | Type | Notes |
|---|---|---|
| `id` | VARCHAR(36) PK | |
| `tenant_id` | VARCHAR(64) NOT NULL | |
| `name` | VARCHAR(120) NOT NULL | UNIQUE within tenant |
| `debut_date` | DATE | |
| `agency` | VARCHAR(120) | |
| `profile_image_ref` | VARCHAR(500) | |
| `status` | VARCHAR(20) NOT NULL | `ACTIVE` / `ARCHIVED` |
| `created_at` / `updated_at` / `archived_at` | TIMESTAMPTZ | |
| `version` | BIGINT | optimistic lock |

**Constraints**:
- `uq_artist_groups_tenant_name UNIQUE (tenant_id, name)`

### `group_memberships` — N:M with re-join support

| Column | Type | Notes |
|---|---|---|
| `group_id` | VARCHAR(36) NOT NULL | PK part 1 |
| `artist_id` | VARCHAR(36) NOT NULL | PK part 2 |
| `joined_at` | TIMESTAMPTZ NOT NULL | PK part 3 (lets the same artist re-join later) |
| `tenant_id` | VARCHAR(64) NOT NULL | |
| `role` | VARCHAR(20) NOT NULL | `LEADER` / `MEMBER` / `FORMER_MEMBER` |
| `left_at` | TIMESTAMPTZ | NULL for active memberships |

**Indexes**:
- `idx_group_memberships_artist (tenant_id, artist_id)` — reverse lookup, tenant_id-prefixed per F7 / M2
- `idx_group_memberships_tenant_group (tenant_id, group_id)`

Active membership = `left_at IS NULL AND role <> 'FORMER_MEMBER'`.

### `fandoms` — 1:1 with artist

| Column | Type | Notes |
|---|---|---|
| `artist_id` | VARCHAR(36) PK | also FK semantics (logical, no DB FK) |
| `tenant_id` | VARCHAR(64) NOT NULL | |
| `fandom_name` | VARCHAR(120) NOT NULL | |
| `color_hex` | VARCHAR(7) | `#RRGGBB` regex CHECK |
| `founded_at` | DATE | |
| `slogan` | VARCHAR(200) | |
| `created_at` / `updated_at` | TIMESTAMPTZ | |
| `version` | BIGINT | |

### `outbox` + `processed_events`

Legacy v1 tables. `libs/java-messaging` no longer maps them: TASK-MONO-312 deleted
`OutboxJpaEntity` and TASK-MONO-406 deleted `ProcessedEventJpaEntity` (the library
now ships **no** `@Entity` at all). Both tables survive in the schema only because
applied Flyway migrations are immutable — nothing maps them, so `ddl-auto=validate`
ignores them. The live outbox is `artist_outbox`, mapped by this service's own
`ArtistOutboxJpaEntity` (which extends the library `@MappedSuperclass`
`OutboxRowEntity`).

---

## Domain ↔ JPA mapping

The domain aggregates are pure POJOs. The persistence adapters
(`adapter/out/persistence/`) translate to/from JPA entities:

| Domain | JPA entity | Adapter |
|---|---|---|
| `Artist` | `ArtistJpaEntity` | `ArtistRepositoryAdapter` |
| `ArtistGroup` | `ArtistGroupJpaEntity` | `ArtistGroupRepositoryAdapter` |
| `GroupMembership` | `GroupMembershipJpaEntity` (`@EmbeddedId GroupMembershipKey`) | `ArtistGroupRepositoryAdapter` |
| `Fandom` | `FandomJpaEntity` | `FandomRepositoryAdapter` |

`Optional<Artist> findById(ArtistId, String tenantId)` is the only allowed
read path from application code — every query is tenant-scoped.

---

## Cross-aggregate references

| Reference | Direction | Resolution |
|---|---|---|
| community-service.posts.author_account_id (when author is artist) | community → artist | logical FK; no DB FK (cross-service); resolved via HTTP at v2 |
| community-service.follows.artist_account_id | community → artist | logical FK; v1 emits `artist.archived` so consumer can react |
| group_memberships.artist_id | within service | logical FK; checked in application service via `ArtistRepository.existsInStatus` |
| fandoms.artist_id | within service | logical FK + 1:1; checked at application service |

No physical `FOREIGN KEY` constraints across services. Within the service we
prefer logical FKs (CHECK + application-side validation) over hard FKs to keep
migrations flexible.

---

## Tenant isolation

Every table carries `tenant_id`. Every query filters by `tenant_id` (M2 of
`rules/traits/multi-tenant.md`). The `effective tenant` is resolved from the
JWT `tenant_id` claim (`*` wildcard maps to `fan-platform`). Cross-tenant
reads return `Optional.empty()` → 404, never 403 (existence non-disclosure).
