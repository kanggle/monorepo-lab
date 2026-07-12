# gateway-service — Overview

> 1-pager: responsibilities, public surface (routes), key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `gateway-service` |
| Project | `wms-platform` |
| Service Type | `rest-api` (edge gateway role) |
| Architecture Style | **Layered** — no domain aggregates; filter pipeline only, see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, **Spring Cloud Gateway (reactive WebFlux)**, Redis 7 (rate-limit counters, ephemeral) |
| Deployable unit | `apps/gateway-service/` |
| Bounded Context | n/a — service contains no domain logic |
| Persistent stores | none (stateless); Redis for ephemeral rate-limit counters only |
| Event publication | none |

## Responsibilities

- **Single external entry point** — all WMS `/api/v1/**` traffic routes through this service per [`platform/api-gateway-policy.md`](../../../../../platform/api-gateway-policy.md).
- **JWT validation** — OAuth2 Resource Server; validates `aud=wms-platform` + `tenant_id` claim. The tenant gate (`TenantClaimValidator`) is now **entitlement-trust dual-accept** (ADR-MONO-019 § D5): a token passes when **either** the legacy strict `tenant_id == wms` matches **or** the IAM-signed `entitled_domains` claim contains `wms`; rejection (`tenant_mismatch` → 403 `TENANT_FORBIDDEN`) requires **both** to fail. wms keeps strict legacy equality (no `*` wildcard); `entitled_domains` is read only from an RS256/JWKS-verified token so it is unforgeable. While IAM has not populated the claim it is absent → legacy-only (production net-zero); the legacy branch is removed in step 4 once IAM populates it (separate follow-up).
- **Identity header pipeline** — strip client-supplied headers (`X-Account-Id`, `X-Tenant-Id`, `X-Roles`); re-set from verified JWT claims.
  > **Implementation status (TASK-BE-502).** The **strip** half is enforced: `IdentityHeaderStripFilter` removes all three (plus `X-User-Id`, `X-User-Email`, `X-User-Role`, `X-Actor-Id`, `X-Account-Type`). The **re-set** half is deliberately *not* implemented for these three: no wms service reads them (0 readers in `apps/*/src/main`), and `X-Account-Id` / `X-Roles` have no defined claim mapping anywhere. Injecting a header nobody reads invites the next author to trust it, so wms supplies only what it can vouch for (`JwtHeaderEnrichmentFilter` sets `X-User-Id`, `X-Actor-Id`, `X-User-Email`, `X-User-Role` from verified claims). When a reader appears, add the enrichment in the same change — and settle what "re-set" means for `X-Account-Id` / `X-Roles` then. Stripping without re-setting is safe in both directions; the reverse is not.
- **Per-(account, routeId) rate limiting** — Redis-backed token bucket keyed on the JWT `sub` (IP fallback when unauthenticated); webhook tier higher than admin tier.
- **Webhook bypass** — `/webhooks/erp/**` routes use HMAC signature only (no JWT filter), routed to `inbound-service` / `outbound-service`.
- **Error envelope normalize** — all gateway-level errors (401 / 403 / 429 / 503) emit platform envelope.
- **Trace propagation** — generate / echo `X-Request-Id` + OTel trace context.

## Public surface

| External path | Auth | Downstream |
|---|---|---|
| `/api/v1/master/**` | JWT + ROLE | `master-service:8080` |
| `/api/v1/inventory/**` | JWT + ROLE | `inventory-service:8080` |
| `/api/v1/inbound/**` | JWT + ROLE | `inbound-service:8080` |
| `/api/v1/outbound/**` | JWT + ROLE | `outbound-service:8080` |
| `/api/v1/admin/**` | JWT + ROLE_ADMIN | `admin-service:8080` |
| `/webhooks/erp/asn` | HMAC (gateway bypass) | `inbound-service:8080` |
| `/webhooks/erp/order` | HMAC (gateway bypass) | `outbound-service:8080` |
| `/actuator/health`, `/actuator/info` | none (local) | self |

Rate-limit tiers (per `(account, routeId)`): standard 100 rps, admin 60 rps, webhook 300 rps (webhooks are HMAC-only so they key on IP). Redis 장애 시 **fail-open** (per `platform/api-gateway-policy.md`).

## Key invariants

1. **JWT validation 통과 없이는 `/api/v1/**` 요청 downstream 도달 금지** — webhook routes 만 예외 (HMAC).
2. **Client-supplied identity headers 모두 strip 후 forwarding** — security invariant; downstream service 는 gateway 가 set 한 header 만 신뢰.
3. **No business logic, no aggregates, no persistence** — stateless.
4. **Fail-open rate limit** — Redis outage 시 throw 금지; 통과 + WARN + 메트릭 발행 (`platform/api-gateway-policy.md`).
5. **All non-matched paths return 404** — transparent proxy fallthrough 금지.
6. **Error responses match platform envelope** — `{ code, message, timestamp }` 형식, 직접 envelope 작성 금지.

## Owned Data

- None (stateless). Redis 상태는 ephemeral rate-limit counters only.

## Published Interfaces

- None (routing only — downstream contracts live in each service's spec).

## Dependent Systems

- `master-service`, `inventory-service`, `inbound-service`, `outbound-service`, `admin-service` — route targets
- Redis — rate-limit store
- OAuth2 Authorization Server (JWT public keys / JWKS endpoint)

## Out of scope (v1)

- `notification-service` 는 REST surface 0 — gateway route 없음.
- Business logic of any kind.
- Domain state persistence.
- Direct TMS / ERP calls — `outbound-service` / `inbound-service` 가 adapter 소유.
- 다중 IdP 통합 (SAML / SCIM) — OAuth2 / JWT 만 지원.
