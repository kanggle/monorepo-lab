# TASK-BE-369 — shipping-service row-level tenant_id (ADR-030 Step 4)

**Status:** review
**Domain:** ecommerce · **Service:** shipping-service · **Type:** backend tenant isolation
**Parent:** ADR-MONO-030 Step 4 (per-service tenant_id migration) · ADR-MONO-031 Phase 4a (shipping console-absorption precondition)

## Goal

Give `shipping-service` row-level multi-tenant isolation (`tenant_id`), replicating the
established BE-357 (product/order) + BE-367 (user) + BE-368 (promotion) pattern. This is the
**tenant-isolation precondition** that ADR-031 §2.4.10 makes normative before the console may
absorb the shipping operator surface (Phase 4b, separate console task).

**Authz model = promotions-exact (BE-368), NOT products/orders (BE-366).** shipping operator
endpoints stay under `/api/shippings/**` and **keep their internal `validateAdminRole(X-User-Role==ADMIN)`
checks** — exactly like promotion-service. **No gateway route change. No controller split. No
`/api/admin/shippings/**`.** The console will call `/api/shippings` with a domain-facing operator
token (gateway `JwtHeaderEnrichmentFilter` injects `X-User-Role`). Do **not** remove the role checks.

## Scope

Mirror the M1–M6 tenant-isolation mechanics. Reference implementations (read them first):
- **promotion-service** (`apps/promotion-service`) — closest sibling: same DDD `interfaces/rest/filter`
  package convention, **outbox**-based event publishing, kept internal `validateAdminRole`. Primary template.
- **order-service** (`apps/order-service`) — for the **system/batch path tenant-agnostic** split
  (`findByIdAcrossTenants` reasoning: globally-unique-id paths preserve the row's own tenant).
- **user-service** (`apps/user-service`) — TenantContext shape.

### M1 — Flyway migration `V7__add_tenant_id.sql`
- Table `shippings`: add `tenant_id VARCHAR(64)` **3-step** (ADD nullable → `UPDATE ... SET tenant_id='ecommerce' WHERE tenant_id IS NULL` → `ALTER ... SET NOT NULL`).
- Index `idx_shippings_tenant_status (tenant_id, status, updated_at)` — backs the admin list (`findByStatus`/`findAll` ordered by `createdAt`/`updatedAt`).
- **`shipping_status_history` gets NO `tenant_id` column.** It is loaded **only** via the parent
  `shippings` EAGER `@OneToMany` (`ShippingJpaEntity.statusHistory`), never queried independently —
  parent-scoping already isolates it. Add a SQL comment documenting this reasoning. (Contrast user-service,
  which added tenant_id to all 3 tables because each is queried independently.)

### M2 — TenantContext + filter
- `com.example.shipping.domain.tenant.TenantContext` — framework-free `ThreadLocal<String>`,
  `DEFAULT_TENANT_ID = "ecommerce"`, `set(String)` (blank→default), `currentTenant()`, `clear()`.
- `com.example.shipping.interfaces.rest.filter.TenantContextFilter` — `OncePerRequestFilter`,
  `@Order(Ordered.HIGHEST_PRECEDENCE)`, reads gateway-injected `X-Tenant-Id` (blank→default,
  net-zero / ADR-030 D8), `TenantContext.clear()` in `finally`. Copy promotion-service's filter verbatim, rename package.

### M3 — entity + persistence isolation
- `ShippingJpaEntity`: `@Column(name="tenant_id", nullable=false, updatable=false, length=64)`;
  `create(...)` gains a `tenantId` param; mapper carries it. Domain `Shipping` carries `tenantId`
  (add to `create`, `reconstitute`, getter); `ShippingJpaMapper` maps both directions.
- **Admin / operator paths → scoped `WHERE tenant_id = :tenant`** (from `TenantContext.currentTenant()`):
  - `ShippingRepository.findAll(pageQuery)` and `findByStatus(status, pageQuery)` (back the operator list EP).
  - The admin **mutation** lookups: `ShippingCommandService.updateStatus` (`findById`) and
    `RefreshTrackingService.refreshFromCarrier` lookup — must be **tenant-scoped**: a cross-tenant
    `shippingId` returns **404 `ShippingNotFoundException`** (M3 cross-tenant-read-is-not-found).
    Add a scoped finder (e.g. `findByIdForTenant` / repository method taking the current tenant) — do
    NOT reuse the system-agnostic `findById` for operator mutations.
- **System / consumer / batch paths → tenant-AGNOSTIC** (preserve the row's own tenant, mirror
  order-service `findByIdAcrossTenants`):
  - `findByOrderId(orderId)` (consumer `getShippingByOrderId` + `markShippedByOrderId` wms-confirm return leg),
  - `existsByOrderId(orderId)` (createShipping idempotency dedup),
  - `findInFlightWithTracking(limit)` (auto-collect tracking sweep — `AutoCollectTrackingService`),
  - the carrier-webhook lookup path.
  These keys are globally unique / batch; they must NOT be tenant-filtered (would break cross-cutting
  system flows). Document each agnostic method with a one-line rationale comment.

### M4 — tenant binding on shipment creation
- `OrderConfirmedEventConsumer` creates the Shipping (`createShipping`). Bind tenant **from the
  OrderConfirmed event envelope** (order-service now emits `tenant_id` post-BE-357). `CreateShippingCommand`
  + `Shipping.create(...)` gain `tenantId`; consumer reads `event` envelope `tenant_id` (absent/blank →
  `DEFAULT_TENANT_ID`, defensive — mirror user-service `UserSignedUp` handling). The consumer is not an
  HTTP request, so do **not** rely on `TenantContext`; pass the tenant explicitly.
- `markShippedByOrderId` (wms return leg) and the carrier webhook are system paths that locate an
  **existing** row by orderId/unique key — the row already carries its tenant; no binding needed.

### M5 — event envelope tenant_id
- `SpringShippingEventPublisher.publishShippingStatusChanged(...)` → add `tenant_id` to the
  `ShippingStatusChangedMessage` envelope (top-level, beside `event_id`/`source`, mirror promotion outbox).
  Source the tenant from the **shipping row** (`saved.getTenantId()`); thread it through the publisher
  signature + both call sites (`updateStatus`, `markShippedByOrderId`).
- `publishFulfillmentRequested(...)` (FulfillmentRequested → wms) → carry `tenant_id` in the envelope
  too (from the order/shipping tenant). Keep wms-shaped payload otherwise unchanged.

### M6 — cross-tenant-leak integration test
- `MultiTenantIsolationIntegrationTest` (Testcontainers, `@Tag("integration")`): seed two shipments in
  distinct tenants; assert (a) admin list under tenant A excludes tenant B's row; (b) admin
  `updateStatus`/`refreshTracking` on tenant B's `shippingId` under tenant A context → 404; (c) the
  system `markShippedByOrderId` / auto-collect sweep still sees the row regardless of request tenant
  (tenant-agnostic), and the persisted row keeps its original tenant. Must **compile**; it will not run
  locally (host Docker blocker — engine 29.1.3/API 1.52 vs Testcontainers 1.20.4) — CI Linux is authority.

### Out of scope / unchanged
- Gateway `TenantClaimValidator` / `JwtHeaderEnrichmentFilter` — already landed (BE-357), unchanged.
- `PROJECT.md` `multi-tenant` trait — already present, unchanged.
- `seller_id` — **N/A** for shipping (no seller dimension).
- No gateway route, no controller path change, no removal of `validateAdminRole`.

## Acceptance Criteria
- `./gradlew :shipping-service:check` GREEN (Docker-free; `@Tag("integration")` excluded by default —
  use `--rerun-tasks` if a Mockito constructor-arg change leaves a stale `:test` cache).
- All existing unit/slice tests updated for the new `tenantId` constructor/command/event params and pass.
- Migration is forward-only 3-step; `shipping_status_history` deliberately has no tenant column.
- Admin reads + admin mutations tenant-scoped (cross-tenant → 404); system/consumer/batch paths agnostic.
- Events (`ShippingStatusChanged`, `FulfillmentRequested`) carry `tenant_id` in the envelope.
- M6 IT compiles.

## Related Specs
- `docs/adr/ADR-MONO-030-ecommerce-multivendor-saas.md` (Step 4 per-service migration)
- `docs/adr/ADR-MONO-031-ecommerce-admin-console-consolidation.md` (Phase 4 precondition)
- `projects/ecommerce-microservices-platform/specs/services/shipping-service/architecture.md`

## Related Contracts
- No contract change in this task (backend-only). The console binding §2.4.10.3 lands in the Phase 4b
  console task. Event envelope `tenant_id` is additive (mirrors product/order/user/promotion events).

## Edge Cases
- OrderConfirmed envelope missing/blank `tenant_id` → default `ecommerce` (no failure).
- Cross-tenant `shippingId` on operator mutation → 404, not 403 (mirror BE-367 M3).
- Auto-collect sweep / wms return leg under no HTTP tenant context → must still process all tenants'
  in-flight rows (agnostic); persisted row tenant preserved.
- Existing rows backfilled to `ecommerce` (single-tenant history).

## Failure Scenarios
- If a system path is accidentally tenant-scoped → wms return leg / auto-collect silently skips
  other-tenant shipments (data stranded). Guard: explicit agnostic methods + comments + M6 assertion (c).
- If `validateAdminRole` is removed → consumer tokens could drive operator mutations (authz regression).
  Keep the checks (promotions precedent).
- AFTER_COMMIT ThreadLocal-clear race on event publish: shipping uses **outbox** (not direct Kafka in
  the request thread), so the envelope tenant is captured at outbox-write time from the row — no race
  (simpler than user-service's Spring-event workaround).
