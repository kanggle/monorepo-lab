---
id: TASK-BE-403
title: "review-service tenant_id propagation (ADR-MONO-030 Step 4, facet c)"
status: done
service: review-service
tags: [code, test, migration, multi-tenant, event-driven]
analysis_model: "Opus 4.8"
impl_model: "Sonnet 4.6"
created: 2026-06-18
---

# TASK-BE-403 — review-service tenant_id propagation

## Goal

Bring `review-service` into the multi-tenant marketplace SaaS model (ADR-MONO-030 Step 4,
facet c) so every review row carries `tenant_id` and review domain events thread it in their
envelope — matching the pattern already proven in user-service (V4 / TASK-BE-367),
notification-service (V5 / TASK-BE-370), promotion-service (V6 / TASK-BE-368), shipping-service
(V7 / TASK-BE-369), and **payment-service (V5 / TASK-BE-400)**.

review-service is a **clean pattern-repeat** of payment-service: PostgreSQL + Spring Data JPA +
Flyway + shared `libs/java-messaging` outbox. Use `apps/payment-service` (TASK-BE-400) as the
reference implementation throughout.

## Scope

1. **Flyway migration V5** — add `tenant_id VARCHAR(64) NOT NULL DEFAULT 'ecommerce'` to the
   `reviews` table; three-step (ADD nullable → UPDATE existing rows to `'ecommerce'` → SET NOT
   NULL). Create `idx_reviews_tenant_id` index. (Existing migrations: V1 create_reviews_table,
   V2 fix_soft_delete, V3 add_product_name, V4 create_outbox → next is **V5**; verify the exact
   existing versions in `src/main/resources/db/migration` before authoring.)

2. **TenantContext** — port `TenantContext` (ThreadLocal, default `'ecommerce'`, never returns
   null — D8 net-zero) from the payment-service pattern into the review-service domain layer.

3. **TenantContextFilter** — servlet filter that reads the `X-Tenant-Id` gateway header into
   `TenantContext` for the duration of each request and clears it in a `finally` block.

4. **Domain model (`Review`)** — add `tenantId` field; update the creation factory and any
   `reconstitute(…)`/mapper path to include it.

5. **Persistence adapter** — stamp `tenant_id` on `ReviewJpaEntity`; repository read methods
   scope by `tenant_id` from `TenantContext` (e.g. `findByProductIdAndTenantId`,
   `findByIdAndTenantId`); add tenant-scoped repository methods as needed. Reads that today key
   only by product/user must add the tenant filter (M2 row filter, M3 404-over-403 — a review
   in another tenant resolves to not-found, not forbidden).

6. **Outbox event envelope** — add a `tenant_id` top-level field to the review domain events
   (`ReviewCreated`, `ReviewUpdated`, `ReviewDeleted`); populate from the stored review's
   `tenantId`. Additive — existing consumers that do not read `tenant_id` are unaffected.

7. **Spec updates**:
   - ADD a `## Multi-Tenancy` section to `specs/services/review-service/architecture.md` (M1-M7
     applicability table matching sibling services such as payment-service).
   - UPDATE `specs/features/multi-tenancy-and-marketplace.md` — in the progress table (the row
     currently `cart / review / search / auth / web-store | 미완 (in-migration)`), **split that
     row**: add a done row `review-service | V5 | TASK-BE-403`, and **reclassify the remaining
     entries honestly** so the in-migration list reflects reality:
     - `search-service` → remains in-migration (ES index-filter shape, TASK-BE-404).
     - `cart` → **N/A — 서비스 미존재**(클라이언트/order-service 미분리), 추후 cart-service 추출 시.
     - `auth-service` → **N/A — 폐기됨**(TASK-BE-132, 빌드 제외; iam-platform 대체).
     - `web-store` → **N/A — 프런트(Next.js)**, relational 영속 없음; `X-Tenant-Id` 헤더 전파만
       (gateway→BFF, 이미 라이브) — 마이그레이션 대상 아님.
   - UPDATE `specs/contracts/events/<review-events file>` (find the review events contract) — add
     the `tenant_id` field to the event envelope (additive). If no review-events contract file
     exists, note that in the task and skip (do not invent a contract).

8. **Tests** — unit tests (no Testcontainers; ITs run in CI only) for:
   - `TenantContext` (set / currentTenant / clear / default `'ecommerce'`)
   - `Review` domain model with `tenantId`
   - `ReviewJpaEntity` ↔ mapper with `tenantId`
   - outbox writer emits `tenant_id` in the serialized envelope
   - `TenantContextFilter` reads header and clears in `finally`

## Acceptance Criteria

- AC-1: `reviews` table has `tenant_id VARCHAR(64) NOT NULL`; all pre-existing rows map to
  `'ecommerce'` (default-tenant D8 net-zero).
- AC-2: `./gradlew :projects:ecommerce-microservices-platform:apps:review-service:test` passes
  with all new tests GREEN (unit scope; Testcontainers ITs are CI-only on this host).
- AC-3: `ReviewCreated`/`ReviewUpdated`/`ReviewDeleted` serialized envelopes include `"tenant_id"`
  at the top level; existing fields unchanged.
- AC-4: `TenantContext.currentTenant()` returns `'ecommerce'` when no header is set (standalone/D8).
- AC-5: review reads are tenant-scoped (a review under tenant A is not returned/visible under a
  tenant-B context; cross-tenant fetch → 404-over-403, M3).
- AC-6: `review-service` row updated to done in `specs/features/multi-tenancy-and-marketplace.md`;
  cart/auth/web-store reclassified N/A with reason (see Scope 7).
- AC-7: `specs/services/review-service/architecture.md` has a `## Multi-Tenancy` section.

## Related Specs

- `specs/features/multi-tenancy-and-marketplace.md` — M1-M7, in-migration table
- `specs/services/review-service/architecture.md` — service architecture (no MT section yet)
- `rules/traits/multi-tenant.md` — M1-M7 trait definitions
- `apps/payment-service` (TASK-BE-400) — reference implementation (clean twin)

## Related Contracts

- review events contract under `specs/contracts/events/` (additive `tenant_id` field) — locate
  the actual file; if absent, skip and note it.

## Edge Cases

- **Standalone / no IAM**: `X-Tenant-Id` absent → `TenantContext.currentTenant()` = `'ecommerce'`;
  service behaves byte-identically to pre-multi-tenant single-store.
- **Event-consumer threads** (if review-service consumes any inbound events, e.g. product events):
  no HTTP request → no filter → `TenantContext` unset → default tenant. For single-tenant-today
  this is correct; add a TODO for deriving tenant from the inbound event's `tenant_id` once those
  events carry it.
- **Existing outbox rows** without `tenant_id` in serialized JSON: delivered as-is; consumers must
  tolerate a missing/`null` `tenant_id` (additive-contract rule).

## Failure Scenarios

- **Migration V5 version clash**: Flyway refuses to start → confirm the exact next version from the
  migration dir before naming the file.
- **`tenant_id` null on insert**: mitigated by `DEFAULT 'ecommerce'` in V5 and the `TenantContext`
  invariant that `currentTenant()` never returns null.
- **Serialization test regression**: additive `tenant_id` must not break existing
  `assertThat(envelope).contains(...)`-style assertions (no exact-JSON-equality assertion should
  exist; if one does, update it to include `tenant_id`).
