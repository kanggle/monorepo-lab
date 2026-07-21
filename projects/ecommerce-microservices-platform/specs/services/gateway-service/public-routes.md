# Gateway Public Routes — ecommerce-microservices-platform

This document declares the concrete public route list and rate-limit tiers for this project's gateway. See [`platform/api-gateway-policy.md`](../../../../../platform/api-gateway-policy.md) for the generic gateway responsibilities and rules.

Default posture: every route requires a valid JWT. Only the routes listed below are public (no authentication). All other routes are rejected by the gateway with `401 UNAUTHORIZED` if the JWT is missing or invalid.

---

## Public Routes (no auth required)

| Method | Path | Rationale |
|---|---|---|
| `GET` | `/api/products/**` | Read-only catalog browsing (anonymous commerce) |
| `GET` | `/api/search/**` | Search / autocomplete for anonymous visitors |
| `GET` | `/api/reviews/products/**` | Public review read; write still requires auth (rule E6) |
| `GET` | `/actuator/health` | Liveness/readiness probes for orchestrator |

> **Authentication is no longer a gateway route.** The `/api/auth/**` routes
> (signup / login / refresh) that once fronted `auth-service` were **removed**
> when auth-service was decommissioned (`TASK-BE-132`); authentication now runs
> through GAP/IAM **OIDC** (`TASK-MONO-027`). Users obtain tokens directly from
> the IdP's `/oauth2/authorize` endpoint (not gateway-proxied); the gateway
> validates the resulting RS256 JWT as an OAuth2 Resource Server. A request to
> any `/api/auth/**` path now matches no route → `404` (it is not a public
> gateway route). The gateway does permit `/oauth/**` (`SecurityConfig`
> `PUBLIC_PATHS`) so unauthenticated OIDC-related callbacks are not rejected by
> the JWT filter.

Changes to this list MUST be updated here before deployment (see `platform/api-gateway-policy.md` → Change Rule).

---

## Rate-Limit Tiers

Per `platform/api-gateway-policy.md` the gateway applies rate limits per `(clientIp, routeId)`. This project chooses concrete values:

| Tier | Applies to | Limit |
|---|---|---|
| **Standard** | Catalog, search, review read, most authenticated reads | 100 req/min per IP |
| **Internal-only** | Service-to-service calls routed internally (not via gateway) | N/A — bypasses gateway |

> The former **Sensitive** tier rate-limited the credential endpoints
> (`/api/auth/login` / `signup` / `refresh`). Those endpoints no longer exist at
> this gateway — authentication moved to GAP/IAM OIDC (`TASK-BE-132` /
> `TASK-MONO-027`), so brute-force protection for credential exchange is now the
> IdP's concern, not this gateway's.

Redis-unavailable failure mode: gateway **fails open** (allow request, log WARN, alert) per platform policy. Rate limiting is a soft protection, not a correctness boundary.

---

## Out of Scope

- Admin endpoints (`/api/admin/**`) are authenticated AND authorized by role (`ROLE_ADMIN`). The role check happens after the JWT filter; it is not a "public" concern.
- Webhook endpoints from PGs (e.g. `/api/webhooks/payment/toss`) are public by IP allow-list, not by this route list. Their security model (signature verification, source-IP validation) is documented in the webhook-owning service's spec.

---

## References

- `platform/api-gateway-policy.md` — generic gateway rules and identity handling
- `rules/domains/ecommerce.md` — domain rules (E1 order lifecycle, E6 review authorship)
- `specs/services/gateway-service/architecture.md` — gateway internal architecture
