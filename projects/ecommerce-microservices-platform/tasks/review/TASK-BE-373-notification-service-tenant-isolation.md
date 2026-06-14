# TASK-BE-373 — notification-service row-level tenant_id + template-detail gap (ADR-030 Step 4)

**Status:** review
**Domain:** ecommerce · **Service:** notification-service · **Type:** backend tenant isolation + API gap-fill
**Parent:** ADR-MONO-030 Step 4 (per-service tenant_id migration) · ADR-MONO-031 Phase 5a (notification console-absorption precondition)

> **Task number note:** uses **BE-373** (370/371/372 are concurrent iam-platform tasks on the shared global TASK-BE counter — iam merged BE-372 first, so this ecommerce task renumbered 372→373; ecommerce previous was BE-369/shipping).

## Goal

Two things, atomically:
1. **Row-level multi-tenant isolation (`tenant_id`)** for `notification-service`, replicating the BE-357/367/368/369
   pattern (M1–M6). **Authz = promotions-exact**: gateway route `/api/notifications/**` (non-admin) unchanged,
   internal `validateAdminRole(X-User-Role==ADMIN)` on the 3 template endpoints kept. **No gateway change, no
   `/api/admin/...`, no authz-alignment leg.**
2. **Fill the `GET /api/notifications/templates/{templateId}` gap** — the single-template detail endpoint that
   admin-dashboard already calls (with a dev mock fallback) and the Phase 5b console edit page needs.

This is the **tenant-isolation precondition** ADR-031 §2.4.10 makes normative before the console absorbs the
notification operator surface (template management). notification-service is **hexagonal** (`adapter/in`,
`adapter/out`, `application/port`, `domain`) — mirror the M1–M6 mechanics into that layout (not promotion's DDD `interfaces/`).

## Scope

Reference implementations (read first): **shipping-service** (`apps/shipping-service`, just-landed BE-369 — the
freshest M1–M6 template, incl. admin-scoped vs system-agnostic split + cross-tenant 404) and **user-service**
(`apps/user-service` — 3-table tenant migration + `UserSignedUp` consumer envelope-tenant-default pattern).

### M1 — Flyway migration `V5__add_tenant_id.sql`
All **3 tables** get `tenant_id VARCHAR(64)` (3-step ADD nullable → `UPDATE ... SET tenant_id='ecommerce' WHERE tenant_id IS NULL` → `SET NOT NULL`):
- `notifications` — index `(tenant_id, user_id, created_at)` for the consumer list.
- `notification_templates` — **the existing UNIQUE `(type, channel)` constraint must become tenant-scoped
  `UNIQUE (tenant_id, type, channel)`**: DROP the old constraint, ADD the new one. (Multi-tenant: each tenant
  owns its own template per type+channel.) Index/uq accordingly.
- `user_notification_preferences` — PK stays `user_id` (globally unique); add `tenant_id` column + scope queries.
  Index `(tenant_id, user_id)` if helpful.

### M2 — TenantContext + filter (hexagonal placement)
- `com.example.notification.domain.tenant.TenantContext` — framework-free `ThreadLocal<String>`,
  `DEFAULT_TENANT_ID="ecommerce"`, `set` (blank→default), `currentTenant()`, `clear()`.
- `com.example.notification.adapter.in.rest.filter.TenantContextFilter` — `OncePerRequestFilter`,
  `@Order(HIGHEST_PRECEDENCE)`, reads gateway-injected `X-Tenant-Id` (blank→default), `clear()` in finally.
  (Copy shipping-service's filter, rename package.)

### M3 — entities + persistence isolation
- All 3 JPA entities (`NotificationJpaEntity`, `NotificationTemplateJpaEntity`,
  `UserNotificationPreferenceJpaEntity`) gain `@Column(name="tenant_id", nullable=false, updatable=false, length=64)`;
  factory/mapper carry it. Domain models + persistence mappers thread `tenantId`.
- **Admin/operator reads → tenant-scoped** (`WHERE tenant_id = TenantContext.currentTenant()`), cross-tenant
  single read → **404** (`TemplateNotFoundException`):
  - `NotificationTemplateJpaRepository.findAll` (template list), `findById` (template detail/update lookup).
- **Consumer reads → tenant-scoped** (tenant from `TenantContext`, in addition to the existing `user_id` guard):
  - `NotificationJpaRepository.findByUserIdOrderByCreatedAtDesc`, the `findById` notification-detail path,
  - `UserNotificationPreferenceJpaRepository.findById`/`findByUserId`.
- **System / event-driven paths** (run inside Kafka consumers, no HTTP `TenantContext`) → pass the **bound
  event tenant explicitly** (see M4); do NOT read `TenantContext` there:
  - `NotificationTemplateJpaRepository.findByTypeAndChannel` (send-path template resolution) → scope by the
    notification's tenant.
  - `existsByEventId` (dedup) → scope by the event's tenant (event ids are per-tenant).
  - `existsByTypeAndChannel` (admin create dedup) → tenant-scoped (HTTP path, uses TenantContext).
  - `save(...)` (consumers CREATE notifications/preferences) → bind tenant at creation.

### M4 — tenant binding in the 4 event consumers
`OrderPlacedEventConsumer`, `PaymentCompletedEventConsumer`, `ShippingStatusChangedEventConsumer`,
`UserSignedUpEventConsumer` each CREATE a notification. The inbound event envelopes currently have **no**
`tenant_id` field. For each: add a nullable `tenant_id` to the inbound event DTO envelope (defensive — mirror
user-service `UserSignedUp`), and bind tenant = `event.tenantId()` if present else `DEFAULT_TENANT_ID`
("ecommerce"). Thread the resolved tenant into `SendNotificationCommand` (add a `tenantId` field) so the created
`Notification` + the template-resolution + dedup all use that tenant. (Note: shipping's `ShippingStatusChanged`
envelope now carries `tenant_id` after BE-369 — reading it here is the forward-compatible path; the others
default to ecommerce until their producers emit it.)

### M5 — outbound events
**None.** notification-service is a terminal consumer — it publishes no business events (the `KafkaTemplate`
is DLQ-only). No envelope work.

### Gap-fill — `GET /api/notifications/templates/{templateId}`
- Add `@GetMapping("/{templateId}")` to `TemplateController` (admin-guarded — `validateAdminRole`).
- Add `getTemplate(String templateId)` to `ManageTemplateUseCase` + `TemplateService` → `TemplateRepository.findById`
  (**tenant-scoped**; cross-tenant or missing → 404 `TemplateNotFoundException`).
- Response DTO: a **detail** response carrying the full template (`templateId, type, channel, subject, body,
  createdAt, updatedAt`) — `TemplateListResponse.TemplateSummary` omits `body`, so add a `TemplateDetailResponse`
  (or reuse `TemplateResult`). Mirror the field shape admin-dashboard's `getTemplate` expects.

### M6 — cross-tenant-leak integration test
`MultiTenantIsolationIntegrationTest` (Testcontainers, `@Tag("integration")`): seed templates + notifications in
two tenants; assert (a) admin template list/detail under tenant A excludes/404s tenant B's row; (b) the new
`GET /templates/{id}` 404s cross-tenant; (c) a consumer-created notification binds the event tenant and the
send-path template resolution stays within tenant; (d) the `(tenant_id,type,channel)` uniqueness allows the same
(type,channel) in two tenants. Must **compile** (won't run locally — host Docker blocker; CI Linux is authority).

### Out of scope / unchanged
- Gateway route + `TenantClaimValidator`/`JwtHeaderEnrichmentFilter` (BE-357) — unchanged.
- `PROJECT.md` multi-tenant trait — already present.
- `seller_id` — N/A. No removal of `validateAdminRole`.

## Acceptance Criteria
- `./gradlew :notification-service:check` GREEN (Docker-free; `@Tag("integration")` excluded; `--rerun-tasks` if Mockito ctor change leaves stale `:test` cache).
- All existing tests updated for the new `tenantId` ctor/command/event params; pass.
- `GET /templates/{id}` returns the full template (admin), 404 cross-tenant/missing.
- `notification_templates` uniqueness is `(tenant_id, type, channel)`; existing rows backfilled `ecommerce`.
- Admin + consumer reads tenant-scoped; event consumers bind event tenant (default ecommerce); M6 compiles.

## Related Specs
- `docs/adr/ADR-MONO-030-ecommerce-multivendor-saas.md` (Step 4) · `docs/adr/ADR-MONO-031-...consolidation.md` (Phase 5)
- `projects/ecommerce-microservices-platform/specs/services/notification-service/architecture.md`

## Related Contracts
- No contract change here (backend-only). The console binding §2.4.10.4 lands in Phase 5b. The new
  `GET /templates/{id}` is additive; document it in the service's API spec if one is tracked.

## Edge Cases
- Inbound event envelope missing `tenant_id` → default `ecommerce`.
- Same (type, channel) template in two tenants → allowed (tenant-scoped uniqueness).
- Cross-tenant `templateId` on detail/update → 404.
- Consumer dedup (`existsByEventId`) must be tenant-scoped so identical event ids across tenants don't collide
  (in practice ids are uuid-unique, but scope defensively).

## Failure Scenarios
- If the template uniqueness stays global `(type, channel)` → a second tenant cannot create its own ORDER_PLACED/EMAIL template (cross-tenant write block). Guard: M1 drops + re-adds tenant-scoped uq; M6 assertion (d).
- If a consumer reads `TenantContext` (no HTTP context in a Kafka thread) → NPE/blank → wrong-tenant rows. Guard: pass the bound event tenant explicitly through `SendNotificationCommand`.
- If `validateAdminRole` removed → consumer tokens drive template CRUD (authz regression). Keep it.
