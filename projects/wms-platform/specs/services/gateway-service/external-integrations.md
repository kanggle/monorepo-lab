# gateway-service — External Integrations

External vendor catalog for `gateway-service`. Required artifact per
`rules/traits/integration-heavy.md` § Required Artifacts (1).

**Zero-state declaration**: `gateway-service` is the **edge gateway** of
the WMS — every external HTTP request passes through it, but the gateway
itself **calls no external vendor** in v1. The OAuth2 Authorization
Server it consults for JWT validation (`iam-platform`) is a sibling
project within the same monorepo deploy, classified as project-internal
infrastructure (see § Internal vs External Boundary). External-bound
traffic is reverse-proxied to downstream sibling services; gateway's own
outbound surface is exclusively to project infrastructure (Redis +
JWKS endpoint).

This file exists to make that boundary explicit and to set the entry
point for the day the OAuth2 AS is swapped for an external SaaS IdP
(Auth0 / Cognito / Okta), at which point this file leaves zero-state.

---

## Catalog Summary

| Vendor | Direction | Protocol | Auth | Required for |
|---|---|---|---|---|
| _(none in v1)_ | — | — | — | — |

Sibling integration surfaces (cross-reference; gateway does not consume them):

- [`../inbound-service/external-integrations.md`](../inbound-service/external-integrations.md) — ERP ASN webhook (HMAC, routed through gateway but verified inside inbound).
- [`../outbound-service/external-integrations.md`](../outbound-service/external-integrations.md) — ERP order webhook + TMS push.
- [`../notification-service/external-integrations.md`](../notification-service/external-integrations.md) — Slack Incoming Webhooks (BE-158).
- [`../inventory-service/external-integrations.md`](../inventory-service/external-integrations.md) — sibling zero-state (BE-156, primary template).

---

## Why Zero Direct Integrations

`gateway-service` is intentionally **stateless and vendor-free**:

- **No business logic, no aggregates, no persistence** — overview § Key invariants #3. The filter pipeline (JWT validate → strip-and-reset identity headers → rate-limit → route) operates entirely on the request itself.
- **Webhooks (ERP) flow through but are verified downstream** — HMAC signature validation lives inside `inbound-service` / `outbound-service`, not in the gateway. The gateway bypass route (`/webhooks/erp/**`) is explicitly carved out of JWT validation per [`architecture.md`](architecture.md) § Routes.
- **Downstream proxy is sibling-only** — 5 service routes all target internal `<service-name>:8080` hostnames; no external HTTP target.

The vendor surface that *does* exist (Slack / TMS / ERP) is owned by the downstream services per Hexagonal port/adapter separation. Routing the traffic through gateway is plumbing, not integration.

---

## Internal vs External Boundary

| Component | Classified as | Rationale |
|---|---|---|
| Redis (rate-limit counters) | **infrastructure** (project-shared, ephemeral) | No persistent data; outage fails open per `platform/api-gateway-policy.md` |
| OAuth2 Authorization Server / JWKS endpoint | **internal infrastructure (project-cohabited)** | v1 deploy: `iam-platform` sibling project hosts the AS within the same monorepo deploy. Gateway fetches JWKS at boot + on token kid miss. Treated as infrastructure, not vendor, because it shares the deploy lifecycle. **If AS is swapped to external SaaS (Auth0 / Cognito / Okta), this file leaves zero-state** — full I1-I3 timeout / circuit / retry policy required for the JWKS path |
| Downstream 5 sibling services | **internal service routes** | Reverse-proxy target only; per-route circuit / rate-limit live in the route filter config, not as vendor adapters |
| External HTTP clients (browsers, admin UI, ERP) | **callers**, not "external integrations" | Inbound traffic, not outbound vendor calls |

I9 bulkhead targets — dedicated thread pool / connection pool per external HTTP vendor — are **not applicable** to `gateway-service` v1 because there is no outbound HTTP call to bulkhead. The reactive WebFlux client pool is shared across all downstream sibling routes (intentional — one pool for one cluster).

---

## Required-Artifact Compliance Map

`rules/traits/integration-heavy.md § Required Artifacts` (1–6) under zero-state:

| # | Artifact | gateway-service applicability |
|---|---|---|
| 1 | 외부 연동 카탈로그 | **This file** — explicit zero-state catalog above |
| 2 | Circuit/Retry 정책 표 | N/A — no outbound vendor HTTP. Per-route filter config (rate-limit tiers + fail-open) lives in `application.yml` / `application-standalone.yml` and is documented in [`architecture.md`](architecture.md) § Routes |
| 3 | Webhook 인증 규약 | N/A in gateway — ERP webhooks bypass JWT and are HMAC-verified inside the receiving service (inbound / outbound). Gateway only routes |
| 4 | DLQ 재처리 절차 문서 | N/A — gateway is synchronous request-response; no Kafka, no DLQ |
| 5 | Adapter 레이어 구조 | Layered (filter pipeline only); no adapter-out vendor surface |
| 6 | WireMock 테스트 스위트 | N/A — no vendor HTTP path to mock. Integration tests use Testcontainers (downstream sibling stubs) for route + JWT + rate-limit behaviour |

---

## Evolution Paths (Not In v1)

Candidate integrations that would migrate this file out of zero-state:

| Candidate | Trigger | New required content |
|---|---|---|
| **External SaaS IdP swap** (Auth0 / Cognito / Okta / Azure AD) | Decision to retire `iam-platform` AS in favor of a managed IdP | JWKS endpoint timeout (I1), circuit breaker (I2), retry-with-jitter (I3), kid-cache eviction policy, signing key rotation contract |
| **SAML / SCIM federation** | Operator base demands enterprise SSO + auto-provisioning beyond OAuth2/OIDC | I6 inbound webhook (SCIM POST), I7 vendor SDK isolation for SAML assertion parser, multi-IdP routing |
| **WAF / DDoS shield** (Cloudflare / AWS Shield in front) | Public deployment with hostile traffic profile | Mostly out-of-process (lives in front of gateway), but gateway grows trust-the-client-IP-from-header logic |
| **API key issuer (vendor API marketplace)** | WMS exposes `/api/v1/**` to external partners with metered access | Per-key rate-limit tier, key issuance / revocation event consumer, billing meter publication |

Until one of these triggers fires, this file remains zero-state.

---

## References

- [`overview.md`](overview.md) — Service identity + Public surface (routes) + Dependent Systems
- [`architecture.md`](architecture.md) — § Routes, § Identity (`### Service Type Composition`), § Service Type
- [`../inbound-service/external-integrations.md`](../inbound-service/external-integrations.md) — sibling non-zero (ERP ASN)
- [`../outbound-service/external-integrations.md`](../outbound-service/external-integrations.md) — sibling non-zero (TMS marquee)
- [`../inventory-service/external-integrations.md`](../inventory-service/external-integrations.md) — sibling zero-state (BE-156)
- [`../notification-service/external-integrations.md`](../notification-service/external-integrations.md) — sibling non-zero (Slack, BE-158)
- `../../../../../rules/traits/integration-heavy.md` — Required Artifacts + I1–I10
- `../../../../../platform/api-gateway-policy.md` — gateway routing tier, fail-open contract
- `../../../../../platform/security-rules.md` — JWT validation rules, Secret Manager
- `../../../../../platform/observability.md` — required gateway metrics
