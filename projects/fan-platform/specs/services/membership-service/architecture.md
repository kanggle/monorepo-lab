# membership-service — Architecture

This document declares the internal architecture of `fan-platform/apps/membership-service`.
All implementation tasks targeting this service must follow this declaration,
`platform/architecture-decision-rule.md`, and the relevant trait rules
(`rules/traits/transactional.md`, `multi-tenant.md`, `integration-heavy.md`).

> **Authoring status.** This spec is the source of truth produced by
> **TASK-FAN-BE-008** (SPEC/design increment). Implementation =
> **TASK-FAN-BE-009** (skeleton + domain + PG mock + endpoints + outbox + infra);
> community-service `HttpMembershipChecker` adapter swap = **TASK-FAN-BE-010**.
> No production code, build files, or ADR are created by FAN-BE-008 — fan-platform
> has no ADR directory; architectural decisions live in this `architecture.md`
> (same convention as community/artist/gateway), and PROJECT.md § Service Map v2
> is the forward-declaration authority, so no new ADR is required (HARDSTOP-09
> satisfied by spec-before-impl).

---

## Identity

| Field | Value |
|---|---|
| Service name | `membership-service` |
| Project | `fan-platform` |
| Service Type | `rest-api` (single — see Service Type Composition below) |
| Architecture Style | **Layered + 명시적 상태 기계** |
| Domain | fan-platform |
| Primary language / stack | Java 21, Spring Boot 3.4, Spring Web (Servlet), Spring Data JPA, Spring Kafka, Spring Security OAuth2 Resource Server |
| Bounded Context | `membership` |
| Deployable unit | `apps/membership-service/` |
| Data store | Postgres 16 (database `fanplatform_membership`) |
| Cache | none (no read-cache case in v1 — access-check is a single indexed point read; Redis is deliberately omitted) |
| Event publication | Kafka via outbox (`fan.membership.*` lifecycle events — `fan.membership.activated.v1`, `fan.membership.canceled.v1`) |
| Event consumption | none (single-type rest-api) |

### Service Type Composition

`membership-service` is a single-type `rest-api` service per
`platform/service-types/INDEX.md`. Synchronous HTTP over memberships
(subscribe / cancel / list / detail) backed by an internal subscription
state machine (ACTIVE/CANCELED) and a Kafka outbox publisher. It additionally
exposes one **internal** (workload-identity-authenticated, non-gateway) access-check
endpoint consumed by community-service. No inbound event-consumer surface — the
outbox is publication-only.

---

## Architecture Style Rationale

membership-service has clearly delineated layers (controller → use case → domain →
infrastructure) with a small domain centered on a single `Membership`
(subscription) aggregate plus a PG mock boundary. Hexagonal ports/adapters add
value when there are many cross-cutting infrastructure boundaries;
membership-service has only Postgres + Kafka + GAP IdP + a single
`PaymentGatewayPort`. **Layered** keeps the file count low and matches the
sibling `community-service` / `artist-service` convention directly (fan-platform
uses Layered, NOT Hexagonal).

The **명시적 상태 기계** addition is the only architectural deviation from a plain
CRUD layered service. Subscription transitions (`subscribe → ACTIVE → cancel →
CANCELED`) flow through `MembershipStateMachine`, which holds the allowed
transition set as the only source of truth. Direct setter mutation of
`Membership.status` is impossible — every transition is audited and (for
ACTIVE / CANCELED) emits a lifecycle event via the outbox. **Expiry is NOT a
stored transition** (see § State Machine) — it is computed at read time, mirroring
the delegation `isActiveAt` precedent.

---

## Package Layout

```
com.example.fanplatform.membership/
├── MembershipServiceApplication.java
├── presentation/
│   ├── controller/
│   │   ├── MembershipController.java        ← /api/fan/memberships (subscribe / cancel / list / detail)
│   │   └── InternalAccessController.java     ← /internal/membership/access (workload-identity only)
│   ├── dto/                                  ← request/response envelopes
│   ├── advice/
│   │   └── GlobalExceptionHandler.java       ← envelope mapping
│   └── filter/
│       └── TenantClaimEnforcer.java          ← service-level fail-closed (defense-in-depth)
├── application/
│   ├── ActorContext.java                     ← caller value object (accountId = sub)
│   ├── SubscribeUseCase.java                 ← Idempotency-Key + PG mock authorize → ACTIVE
│   ├── CancelMembershipUseCase.java          ← ACTIVE → CANCELED (idempotent)
│   ├── ListMembershipsUseCase.java
│   ├── GetMembershipUseCase.java
│   ├── CheckAccessUseCase.java               ← hasAccess(accountId, tier, tenantId) computation
│   └── event/
│       └── MembershipEventPublisher.java     ← outbox writer (libs:java-messaging)
├── domain/
│   ├── membership/
│   │   ├── Membership.java                   ← @Entity (JPA) — subscription aggregate
│   │   ├── MembershipRepository.java         ← port
│   │   ├── MembershipTier.java               ← MEMBERS_ONLY / PREMIUM (+ tierGrants rule)
│   │   └── status/                           ← MembershipStatus, MembershipStateMachine
│   ├── payment/
│   │   └── PaymentGatewayPort.java           ← port (authorize)
│   ├── access/
│   │   └── AccessPolicy.java                 ← tierGrants + windowed-active evaluation
│   └── tenant/TenantContext.java
└── infrastructure/
    ├── config/JpaConfig.java + ClockConfig.java
    ├── jpa/                                   ← Spring Data adapter for MembershipRepository
    ├── outbox/MembershipOutboxPollingScheduler.java
    ├── payment/MockPaymentGatewayAdapter.java ← deterministic PG mock (NO real PG)
    └── security/                              ← service-level OAuth2 + tenant validators + workload-identity decoder for /internal/**
```

### Allowed dependencies

- `spring-boot-starter-{web,data-jpa,validation,actuator,security,oauth2-resource-server}`
- `spring-kafka`
- `flyway-core`, `flyway-database-postgresql`, `org.postgresql:postgresql`
- `io.micrometer:micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`
- shared libs: `libs:java-common`, `libs:java-web`, `libs:java-messaging`, `libs:java-observability`, `libs:java-security`

### Forbidden dependencies

- H2 / any in-memory DB (`platform/testing-strategy.md` — Postgres only).
- `spring-cloud-starter-gateway` (membership-service is a downstream service, not an edge gateway).
- Direct Kafka usage outside the outbox path. Producers MUST go through `MembershipEventPublisher` → outbox → `MembershipOutboxPollingScheduler`.
- Cross-service repository imports (membership-service does not reach into community-service / artist-service tables).
- Any real external payment-gateway SDK — the PG integration is a deterministic mock in v1 (see § PG Mock Boundary). A real PG adapter is a future increment that re-implements `PaymentGatewayPort`.
- `spring-boot-starter-data-redis` — no cache case in v1.

### Boundary rules

- `presentation/` MUST NOT call `infrastructure/` directly. All infrastructure access flows through `application/` use cases that depend on domain ports.
- `domain/` MUST NOT depend on Spring or Jakarta annotations beyond `jakarta.persistence` (JPA) — chosen as a pragmatic exception so the `Membership` entity doubles as a JPA-mapped object (matches the community-service convention). No Spring framework imports inside `domain/`.
- `application/event/MembershipEventPublisher` is the ONLY producer path. Any new event MUST extend the publisher; never call `OutboxWriter` directly from a use case or controller.
- `PaymentGatewayPort` is the ONLY boundary to payment authorization. Use cases depend on the port; the deterministic mock adapter lives in `infrastructure/payment/`.
- `infrastructure/security/` re-validates `tenant_id` for end-user routes even though the gateway already does — fail-closed defense-in-depth (see § Tenant Isolation). The `/internal/**` route is a separate security chain authenticated by workload-identity JWT (see § Internal Access-Check Contract).

---

## Domain Model

`Membership` is the single aggregate — a windowed subscription held by one fan
account.

| Field | Type | Notes |
|---|---|---|
| `id` | string (UUID v7) | aggregate id |
| `tenantId` | string | row-level isolation; always `fan-platform` in this project |
| `accountId` | string | the fan = GAP `sub` claim |
| `tier` | `MembershipTier` | `MEMBERS_ONLY` \| `PREMIUM` |
| `status` | `MembershipStatus` | `ACTIVE` \| `CANCELED` (state machine) |
| `validFrom` | timestamptz | window start (subscribe time) |
| `validTo` | timestamptz | window end = `validFrom + planMonths` |
| `planMonths` | int | drives `validTo`; ≥ 1 |
| `paymentRef` | string | PG mock authorization reference |
| `createdAt` | timestamptz | |
| `canceledAt` | timestamptz? | set on CANCELED transition; null while ACTIVE |
| `version` | long | optimistic lock (transactional.md T5) |

### Tier hierarchy (PREMIUM ⊇ MEMBERS_ONLY)

A `PREMIUM` subscription grants access to `MEMBERS_ONLY` content as well — PREMIUM
is the strict superset. The rule is centralized in `AccessPolicy.tierGrants`:

| Held tier (`heldTier`) | Grants `MEMBERS_ONLY` | Grants `PREMIUM` |
|---|---|---|
| `MEMBERS_ONLY` | ✓ | ✗ |
| `PREMIUM` | ✓ | ✓ |

`tierGrants(heldTier, requiredTier)` returns:
- `heldTier == PREMIUM` → true for any `requiredTier`;
- `heldTier == MEMBERS_ONLY` → true only when `requiredTier == MEMBERS_ONLY`.

---

## State Machine (transactional.md T4)

`MembershipStatus` ∈ { ACTIVE, CANCELED }.

| From | Event | To | Notes |
|---|---|---|---|
| (none) | `subscribe` (PG mock authorize **succeeds**) | ACTIVE | row created `ACTIVE` with window `[validFrom, validTo]`; emits `fan.membership.activated.v1` |
| (none) | `subscribe` (PG mock authorize **declines**) | (no row) | NO row created; API returns 422 `PAYMENT_DECLINED` |
| ACTIVE | `cancel` | CANCELED | terminal; sets `canceledAt`; emits `fan.membership.canceled.v1` |
| CANCELED | `cancel` | CANCELED | **idempotent no-op** — returns the membership, emits NO new event |

- **No `PENDING_PAYMENT` stored state.** The PG mock authorize is *synchronous*:
  on success the row is created directly in `ACTIVE`; on decline no row is created
  and the subscribe API returns 422 `PAYMENT_DECLINED`. There is therefore no
  intermediate persisted payment state to reconcile.
- `CANCELED` is terminal — every transition out of CANCELED other than the
  idempotent self-`cancel` is forbidden.
- **Expiry is read-time, NOT a stored transition.** When `now ∉ [validFrom,
  validTo]` the membership is *not active for access purposes*, but its stored
  `status` stays `ACTIVE` through its natural lifecycle (it is never auto-flipped
  to a stored `EXPIRED`). This mirrors the delegation / `isActiveAt` precedent and
  avoids requiring a scheduler in this increment. The only stored terminal state
  is `CANCELED` (an explicit user action).
- Forbidden / illegal transitions throw `InvalidStateTransitionException`
  (HTTP 422). In practice the only reachable transition besides `subscribe` is
  `cancel`, and re-cancel is an idempotent no-op (NOT an error).

---

## Access Semantics (the core contract)

`hasAccess(accountId, requiredTier, tenantId)` returns **true** iff there EXISTS a
`Membership` for `(accountId, tenantId)` such that ALL of:

1. `status == ACTIVE`, AND
2. `now ∈ [validFrom, validTo]` (read-time window check — `now` from `ClockPort`,
   truncated to micros to match DB round-trip precision), AND
3. `AccessPolicy.tierGrants(membership.tier, requiredTier) == true`.

Otherwise it returns **false**. Semantics by case:

| Held | Requested | Window | Result |
|---|---|---|---|
| PREMIUM ACTIVE | MEMBERS_ONLY | in window | allowed |
| PREMIUM ACTIVE | PREMIUM | in window | allowed |
| MEMBERS_ONLY ACTIVE | MEMBERS_ONLY | in window | allowed |
| MEMBERS_ONLY ACTIVE | PREMIUM | in window | **deny** (tier insufficient) |
| any ACTIVE | any | `now > validTo` (expired) | **deny** (read-time inactive) |
| any CANCELED | any | any | **deny** |
| (no membership row) | any | — | **deny** |
| cross-tenant (`tenantId` mismatch) | any | — | **deny** (see § Tenant Isolation) |

**Fail-closed.** Any infrastructure error (DB unavailable, query failure) results
in DENY, never ALLOW. This is the same contract the consuming
`MembershipChecker` port declares (see § Internal Access-Check Contract).

---

## PG Mock Boundary

Payment authorization is abstracted behind `PaymentGatewayPort`:

```
boolean/PaymentResult authorize(amount, planMonths, paymentToken, idempotencyKey) → { approved, paymentRef }
```

The v1 adapter `MockPaymentGatewayAdapter` is a **deterministic mock** — there is
**no real external PG integration** in this increment. The mock decision rule is
documented and reproducible so tests are deterministic:

- **Decline rule (documented test boundary):** a request is declined when
  `paymentToken` equals the reserved sentinel `tok_decline` (or is otherwise
  flagged by a documented decline rule, e.g. an amount-based test trigger).
  Decline → `SubscribeUseCase` creates NO membership row and the API returns 422
  `PAYMENT_DECLINED`.
- **Approve (default):** any other token approves and yields a synthetic
  `paymentRef` (e.g. `pgmock_<uuid>`), which is stored on the `Membership`.
- **Idempotency:** subscribe requires an `Idempotency-Key` header (transactional.md
  T1). A replay of the same `(accountId, Idempotency-Key)` returns the same
  membership result (same id / paymentRef) without re-authorizing or creating a
  duplicate row. A conflicting reuse of the key with a different payload returns
  409 `IDEMPOTENCY_KEY_CONFLICT`.

A real PG adapter is a future increment: it re-implements `PaymentGatewayPort`
and is wired via `@ConditionalOnMissingBean` / profile, with the mock retained for
dev + integration tests. The domain and use-case layers are unchanged by that
swap.

---

## Internal Access-Check Contract (the remote counterpart of `MembershipChecker`)

community-service holds the port
`com.example.fanplatform.community.domain.membership.MembershipChecker`:

```java
boolean hasAccess(String accountId, String tier, String tenantId);  // fail-closed
```

membership-service exposes the **remote counterpart** as a single internal
endpoint:

```
GET /internal/membership/access?accountId={accountId}&tier={tier}&tenantId={tenantId}
→ 200 { "allowed": <boolean> }
```

The mapping is **1:1** — the three query parameters map exactly to the three port
parameters, and the boolean `allowed` maps to the port's return value:

| MembershipChecker port param | Internal endpoint param | Meaning |
|---|---|---|
| `accountId` (the caller's `sub`) | `accountId` | fan account whose access is being checked |
| `tier` (`MEMBERS_ONLY`\|`PREMIUM`) | `tier` | the **required** tier of the gated content |
| `tenantId` | `tenantId` | tenant scope |
| return `boolean` | `allowed` (boolean) | true = grant, false = deny |

The FAN-BE-010 `HttpMembershipChecker` adapter (replacing the v1
`AlwaysAllowMembershipChecker` via `@ConditionalOnMissingBean(MembershipChecker.class)`)
calls this endpoint and returns `allowed` directly, **fail-closed on any error**
(timeout, 5xx, non-2xx, malformed body → `false`). The endpoint itself also
evaluates the § Access Semantics fail-closed (infra error → `allowed=false`).

**Authentication = workload identity, NOT an end-user token.** The `/internal/**`
surface is authenticated by a **GAP `client_credentials` JWT** per ADR-MONO-005
(workload identity), presented by community-service as the calling service —
NOT by a fan's access token. The internal security chain validates the
client-credentials token (issuer + signature + a recognized internal client/role),
and the route is **not exposed through the gateway** (see § Deploy Dependencies).
A request to `/internal/**` bearing an end-user token, or no token, is rejected
401/403.

---

## Tenant Isolation (multi-tenant.md M2)

Three independent layers enforce the same invariant on the end-user surface
(`/api/fan/memberships/**`):

1. **Gateway** — `fan-platform/apps/gateway-service` rejects tokens whose
   `tenant_id` is not `fan-platform` or `*`. See
   `projects/fan-platform/specs/services/gateway-service/architecture.md`
   § JWT Validation.
2. **Service-level JwtDecoder** (`infrastructure/security`) — same validators
   (`AllowedIssuersValidator` + `TenantClaimValidator`) run against every JWT
   during decoding.
3. **TenantClaimEnforcer filter** — final guard after Spring Security has
   populated the SecurityContext. If `tenant_id` is wrong (or absent), the filter
   writes 403 `TENANT_FORBIDDEN` directly.

Every JPA query is tenant-scoped (`...AndTenantId(...)` derived methods, or
`WHERE m.tenantId = :tenantId` JPQL). Cross-tenant reads return
`Optional.empty()` → 404 (NOT 403) so the service does not leak the existence of
other tenants' memberships.

For the `/internal/membership/access` endpoint, `tenantId` is an explicit
parameter and the query is scoped to it; a membership in a different tenant is
simply not found → `allowed=false` (deny), never leaked.

---

## Outbox + Kafka (event-driven-policy.md, integration-heavy.md I8)

- Business writes (membership row + state transition) and the outbox INSERT share
  one transaction.
- `MembershipOutboxPollingScheduler` (extends `libs:java-messaging`'s
  `OutboxPollingScheduler`) polls PENDING rows and publishes to Kafka with
  `acks=all`, `enable.idempotence=true`. On success `published_at` is set; on
  failure the metric `membership_outbox_publish_failures_total` increments and the
  row is retried on the next tick.
- Topic mapping (`.v1` suffix per `platform/event-driven-policy.md`):
  - `fan.membership.activated` → `fan.membership.activated.v1` (on subscribe → ACTIVE)
  - `fan.membership.canceled` → `fan.membership.canceled.v1` (on cancel → CANCELED)
- **`fan.membership.expired.v1` is forward-declared but NOT emitted in this
  increment.** Because expiry is read-time (§ State Machine), there is no
  scheduler that detects the boundary and no transition to publish. The topic is
  reserved for a future increment that adds a sweeper/scheduler; until then,
  consumers MUST derive "expired" from `validTo` rather than expect an event. This
  gap is recorded honestly (mirrors how community-service records its v2 gaps).
- Envelope shape (from `BaseEventPublisher`): `{ eventId, eventType, source,
  occurredAt, schemaVersion, partitionKey, payload }`. Consumer (planned) =
  notification-service v2 (not built in this increment) — producer-only forward
  interface.

---

## Failure Modes

| Situation | Response |
|---|---|
| Missing/invalid end-user JWT (public routes) | 401 UNAUTHORIZED |
| `tenant_id` is `wms`/other (not `fan-platform`/`*`) | 403 TENANT_FORBIDDEN |
| Membership not found OR cross-tenant OR cross-account | 404 MEMBERSHIP_NOT_FOUND |
| PG mock declines authorization | 422 PAYMENT_DECLINED (no row created) |
| Unknown / invalid `tier` value | 422 MEMBERSHIP_TIER_INVALID |
| Subscribe missing `Idempotency-Key` header | 400 VALIDATION_ERROR |
| Idempotency-Key reused with a different payload | 409 IDEMPOTENCY_KEY_CONFLICT |
| Idempotency-Key reused with identical payload | 200/201 same result (idempotent replay) |
| Cancel of an already-CANCELED membership | 200 idempotent no-op (returns membership) |
| Optimistic-lock conflict on Membership | 409 CONFLICT |
| `/internal/**` called with end-user token or no token | 401 / 403 (workload identity required) |
| Internal access-check infra error (DB down) | `allowed=false` (fail-closed) |
| Postgres down | 5xx (gateway 503 envelope); internal access-check → fail-closed deny |
| Kafka down | outbox PENDING accumulates; metric increments; writes still succeed |

---

## Testing Strategy

- **Unit** — `MembershipStateMachineTest` (subscribe→ACTIVE, ACTIVE→CANCELED,
  idempotent re-cancel, forbidden transitions), `AccessPolicyTest`
  (tierGrants matrix + read-time window evaluation + fail-closed), `MockPaymentGatewayAdapterTest`
  (deterministic approve/decline rule), `SubscribeUseCaseTest` (idempotency,
  decline → no row), `CancelMembershipUseCaseTest`, `CheckAccessUseCaseTest`,
  `MembershipEventPublisherTest`, `TenantClaimValidatorTest`,
  `AllowedIssuersValidatorTest`, `TenantClaimEnforcerTest`.
- **Slice** — `@WebMvcTest` for `MembershipController` (envelope shape, validation,
  auth, Idempotency-Key requirement) and `InternalAccessController` (param binding,
  workload-identity security, `allowed` boolean shape).
- **Integration** (`@Tag("integration")`, Postgres + Kafka Testcontainers,
  WireMock JWKS):
  - `MembershipServiceIntegrationTest` — subscribe (approve) → ACTIVE → cancel
    happy path E2E.
  - `PaymentDeclineIntegrationTest` — decline token → 422 `PAYMENT_DECLINED`, no row.
  - `IdempotentSubscribeIntegrationTest` — duplicate Idempotency-Key → single row,
    identical result.
  - `AccessCheckIntegrationTest` — tier hierarchy (PREMIUM grants MEMBERS_ONLY),
    expired window deny, canceled deny, cross-account/cross-tenant deny,
    DB-down fail-closed.
  - `MultiTenantIsolationTest` — cross-tenant queries return 404; cross-tenant
    access-check returns `allowed=false`.
  - `OutboxRelayIntegrationTest` — subscribe/cancel → outbox row → Kafka topic →
    `published_at` set.
  - `InternalAuthIntegrationTest` — `/internal/membership/access` with a valid
    workload-identity client-credentials token → 200; with an end-user token →
    403; with no token → 401.
  - `MembershipHealthCheckIntegrationTest` — `/actuator/health` returns 200
    unauthenticated.

The default `test` Gradle task excludes `@Tag("integration")`; `integrationTest`
opts in.

---

## Deploy Dependencies (mention-only — implemented by TASK-FAN-BE-009)

This spec does NOT create build or infra files. FAN-BE-009 wires:

- **settings.gradle** — include `projects:fan-platform:apps:membership-service`.
- **package.json** — dev shortcut script (mirrors community/artist entries).
- **Dockerfile** — host-prebuilt jar pattern (`./gradlew bootJar` before
  `docker compose build`; see project memory `env_gap_docker_host_prebuilt_jar_redeploy_trap`).
- **docker-compose** — `membership-service` + Postgres `fanplatform_membership` DB;
  backing services `expose:` only (no host ports). Traefik routes via
  `fan-platform.local`.
- **gateway-service route** — `/api/v1/memberships/**` → `membership-service:8080`
  (RewritePath strips to `/api/fan/memberships/**`). The `/internal/**` surface is
  **NOT gateway-exposed** — it is reachable only on the internal docker network by
  community-service using workload identity. See
  `projects/fan-platform/specs/services/gateway-service/architecture.md`.
- **CI per-service path filter** — pure-positive `code-changed` composition
  (negation prohibited per MONO-074/075); add a `membership-service` entry to the
  positive filter.
- **Flyway** — `V1__init.sql` for the `fanplatform_membership` schema (table
  `memberships` + `outbox` + `processed_events` per `libs:java-messaging`).

---

## References

- `platform/architecture-decision-rule.md`
- `platform/event-driven-policy.md`
- `platform/service-types/rest-api.md`
- `platform/testing-strategy.md`
- `projects/fan-platform/specs/services/community-service/architecture.md` (§ Visibility Tiers + `MembershipChecker` port)
- `projects/fan-platform/specs/services/gateway-service/architecture.md`
- `projects/fan-platform/specs/integration/iam-integration.md`
- `projects/fan-platform/specs/contracts/http/membership-api.md`
- `projects/fan-platform/specs/contracts/events/fan-membership-events.md`
- ADR-MONO-005 (workload identity — GAP `client_credentials` JWT for `/internal/**`)
- `rules/traits/transactional.md` § T1 (idempotency) / T4 (state machine) / T5 (optimistic lock)
- `rules/traits/multi-tenant.md` § M2 (tenant_id everywhere)
- `rules/traits/integration-heavy.md` § I3 / I8 (fail-closed, outbox)
