---
id: TASK-BE-405
title: "per-tenant rate limit + unbounded-query caps (M7, ADR-MONO-030 Step 4 facet e)"
status: done
service: gateway-service, product-service, order-service
tags: [code, test, multi-tenant, gateway, rate-limit, m7]
analysis_model: "Opus 4.8"
impl_model: "Opus 4.8"
created: 2026-06-18
---

# TASK-BE-405 — per-tenant rate limit + unbounded-query caps (M7)

## Goal

Realize the **M7** invariant (`rules/traits/multi-tenant.md` M7 — per-tenant rate limit / quota /
backpressure) for ecommerce, the **last remaining facet** of ADR-MONO-030 Step 4 (facet e). M1-M7
were already ACCEPTED wholesale by ADR-030 D2; this task is the **realization** of M7 (no new ADR).

**Scope (user-decided 2026-06-18):**
- **In**: (a) gateway API rate-limit keyed by **`(tenant_id, route_id)`** tuple (M7 line 84 —
  one tenant's burst must not affect another tenant's latency); (b) **unbounded-query caps** —
  audit tenant-scoped list endpoints and enforce a max page size (M7 line 86 — a single tenant
  must not exhaust shared DBMS resources via a `LIMIT`-less / oversized list).
- **Out (deferred)**: resource-count quotas (max products/sellers per tenant), subscription-tier-
  derived limits, settlement-tier coupling — these are marketplace-economics features, not M7.

## Design decisions (recorded)

- **Enforcement layer = gateway** (M2 layer-1 edge). ecommerce `gateway-service` is **Spring Cloud
  Gateway** (reactive/WebFlux) with a Redis-backed `RequestRateLimiter` already wired, currently
  keyed IP-only (`ipKeyResolver` in `RateLimiterConfig`). Replace/augment with a **tenant-aware
  `KeyResolver`** that keys by `(tenant_id, route_id)`. The gateway already extracts the
  `tenant_id` claim and propagates `X-Tenant-Id` (ADR-030 Step 2 increment A, entitlement-trust
  gate) — key off that. **Reference pattern**: iam-platform `gateway-service`
  `TokenBucketRateLimiter` (Redis, per-tenant JWT-claim key) — mirror its tenant-key extraction;
  prefer extending the existing ecommerce `RequestRateLimiter`/`RateLimiterConfig` (least-change)
  with a tenant `KeyResolver` over introducing a parallel limiter.
- **Limit source = config default + optional per-tenant override** (user-decided). A default
  replenish/burst per route in config; an optional per-tenant override map (keyed by `tenant_id`)
  that falls back to the default when no override exists. No coupling to `tenant_domain_subscription`
  (entitlement plane stays decoupled).
- **Degrade (D8 net-zero) = fall-open / default-tenant**. No `tenant_id` claim (standalone / no
  IAM) → resolve to the default tenant (`'ecommerce'`) single bucket — single-store behaves as
  today. If Redis is unavailable, **fail-open** (do not block traffic) — mirror the
  scm/wms/fan `FailOpenRateLimiter` precedent. Rate-limiting is additive, never a hard dependency.
- **429** `TOO_MANY_REQUESTS` on breach.

## Scope (work items)

1. **gateway-service — tenant-aware rate-limit** (`apps/gateway-service`):
   - Add a `tenantRouteKeyResolver` (`KeyResolver`) returning `"<tenant_id>:<routeId>"`
     (tenant from the established `tenant_id` claim / `X-Tenant-Id`; default `'ecommerce'` when
     absent). Wire it into the route `RequestRateLimiter` filters (replace `ipKeyResolver` or
     compose — confirm the current `application.yml` route filter wiring and `RateLimiterConfig`).
   - Config: default replenishRate/burstCapacity per route + an optional per-tenant override map
     (e.g. `ecommerce.ratelimit.overrides.<tenant_id>.<routeId>`). Resolve override → default.
   - Fail-open on Redis error (FailOpenRateLimiter precedent); 429 on legitimate breach.
   - Verify the existing IP-based limit's fate: either replace with tenant-keyed or layer both
     (document the choice; the M7 requirement is the tenant tuple key).
2. **Unbounded-query caps** (`product-service`, `order-service`, and any other ecommerce service
   with a tenant-scoped list/search endpoint — audit them):
   - Enforce a max page size on list endpoints (clamp an oversized `size`/`limit` to a configured
     max, or reject with 400). No `LIMIT`-less query reachable by a tenant. Document the max.
   - Keep behavior backward-compatible for normal page sizes (only oversized requests are
     clamped/rejected).
3. **Spec updates**:
   - `specs/features/multi-tenancy-and-marketplace.md` — the M7 row/§: mark **realized
     (TASK-BE-405)**, document the `(tenant_id, route)` rate-limit + config-default+override +
     fall-open + page-size-cap design. If §2.3 (M3-M7) lists M7 as out-of-scope/deferred, update
     it to realized.
   - `specs/services/gateway-service/architecture.md` — document the per-tenant rate-limit filter,
     the key shape, the override config, and the fail-open/default-tenant degrade.
   - `specs/services/product-service/architecture.md` + `order-service/architecture.md` — note the
     list-endpoint max-page-size cap (M7).
4. **Tests** (unit; Testcontainers/Redis ITs run in CI only on this host — ensure they compile):
   - `tenantRouteKeyResolver` returns `<tenant>:<route>`; defaults to `'ecommerce'` when no claim.
   - per-tenant override resolution (override present → override; absent → default).
   - page-size clamp/reject on oversized list requests (unit/web-slice level, per service).
   - (IT) cross-tenant rate-limit isolation — tenant A's burst does not consume tenant B's bucket
     (compile here; CI executes against Redis).

## Acceptance Criteria

- AC-1: gateway rate-limit is keyed by `(tenant_id, route_id)`; tenant A and tenant B have
  independent buckets (A's burst → A's 429, B unaffected).
- AC-2: limits come from a config default per route + an optional per-tenant override; breach →
  429 `TOO_MANY_REQUESTS`.
- AC-3: no `tenant_id` claim → default-tenant (`'ecommerce'`) bucket (D8 net-zero); Redis
  unavailable → **fail-open** (traffic not blocked).
- AC-4: every tenant-scoped list endpoint enforces a max page size (no unbounded/`LIMIT`-less list
  reachable by a tenant); normal page sizes behave unchanged.
- AC-5: builds + unit tests GREEN —
  `./gradlew :projects:ecommerce-microservices-platform:apps:gateway-service:test`
  `:projects:ecommerce-microservices-platform:apps:product-service:test`
  `:projects:ecommerce-microservices-platform:apps:order-service:test`.
- AC-6: `multi-tenancy-and-marketplace.md` M7 marked realized; `gateway-service/architecture.md`
  documents the rate-limit design; product/order architecture note the page-size cap.
- AC-7: a cross-tenant rate-limit isolation IT exists and compiles (CI executes it).

## Related Specs

- `rules/traits/multi-tenant.md` M7 (the invariant being realized)
- `specs/features/multi-tenancy-and-marketplace.md` §2.3 (M3-M7), §7 (deferred list — remove M7)
- `specs/services/gateway-service/architecture.md`
- `docs/adr/ADR-MONO-030-...md` §3.4 Step 4 facet e (mark REALIZED after merge)
- **Reference**: `projects/iam-platform/apps/gateway-service/.../ratelimit/TokenBucketRateLimiter.java`
  (per-tenant Redis token bucket); `projects/scm-platform/apps/gateway-service/.../config/RateLimitConfig.java`
  + `FailOpenRateLimiter` (fail-open precedent)

## Related Contracts

- No external API/event contract change (rate-limit is an edge concern; 429 is a standard HTTP
  status). Document the 429 + `Retry-After` (if added) behavior in the gateway architecture spec.

## Edge Cases

- **Standalone / no IAM** (no `tenant_id` claim): default-tenant single bucket → single-store
  behavior unchanged (D8).
- **Redis down**: fail-open (do not 503/block) — mirror `FailOpenRateLimiter`.
- **Per-tenant override absent**: fall back to the route default.
- **Oversized page size**: clamp to max (preferred) or 400 — pick one, document it; normal sizes
  unaffected.
- **Internal/system calls bypassing the gateway**: rate-limit is a gateway-edge concern; system/
  saga calls that bypass the gateway are not rate-limited (consistent with the M2 layer-1 home).

## Failure Scenarios

- **Tenant key resolves null** → would collapse all tenants into one bucket: guard with the
  default-tenant fallback (never null key).
- **IP-limit removed without replacement** → brute-force/DoS regression: ensure the tenant-keyed
  limiter (or a composed IP+tenant key) still bounds anonymous/pre-auth routes; document the
  pre-auth vs post-auth keying.
- **Page-size cap too low** → breaks legitimate large reads: choose a generous max (e.g. 100/200)
  and document it.
- **Reactive/WebFlux pitfalls**: the `KeyResolver` runs reactively — resolve tenant from
  `ServerWebExchange` (header/claim), not a ThreadLocal; do not block.
