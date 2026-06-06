# console-bff — Architecture

This document declares the internal architecture of `platform-console/apps/console-bff`.
All implementation tasks targeting this service must follow this declaration,
[`platform/architecture-decision-rule.md`](../../../../../platform/architecture-decision-rule.md),
and the rule files indexed by `PROJECT.md`'s declared `domain` (`saas`) and
`traits` (`multi-tenant`, `integration-heavy`, `audit-heavy`).

> **Provenance**: Authored by [TASK-PC-BE-001](../../../tasks/ready/) **before**
> implementation (HARDSTOP-09 — architecture decision precedes code). This is
> the **post-ACCEPTED execution** of [ADR-MONO-017](../../../../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md)
> (ACCEPTED 2026-05-20, PR #666 squash `5c711e3b`), the staged sibling of
> ADR-MONO-014 → TASK-BE-298 and ADR-MONO-015 → TASK-PC-FE-005. All eight
> decision axes (**D1**–**D8**) of ADR-MONO-017 carry into this declaration
> **byte-unchanged** (HARDSTOP-04); the spec PR adds the architecture, PROJECT.md
> `service_types += rest-api` row, and the [§ 2.4.9](../../contracts/console-integration-contract.md)
> contract frame, while the impl PR follows this spec to land the Spring Boot
> Hexagonal skeleton + Traefik `console-bff.local` wiring + first operational
> endpoint (`GET /actuator/health`). The first MVP cross-domain dashboard
> ("Operator Overview", ADR-MONO-017 § D8) is a **further** post-skeleton task
> (`TASK-PC-FE-011`, mirroring the ADR-MONO-015 → TASK-PC-FE-005 sequencing).
>
> **Status: pre-skeleton spec only**. No code exists yet at `apps/console-bff/`.
> Sections below describe the **target v1 skeleton + Phase 7 MVP-ready** shape;
> the impl PR (TASK-PC-BE-001) lands the skeleton, and TASK-PC-FE-011 lands the
> first composition route on top.

---

## Identity

| Field | Value |
|---|---|
| Service name | `console-bff` |
| Project | `platform-console` |
| Service Type | `rest-api` (single — see Service Type Composition below) |
| Architecture Style | **Hexagonal** (Ports & Adapters) |
| Domain | saas |
| Traits | multi-tenant, integration-heavy, audit-heavy |
| Primary language / stack | Java 21, Spring Boot 3.4 (Servlet stack) |
| Bounded Context | Cross-domain composition for the unified operator console — owns no domain state; aggregates 5 backend domains (GAP + wms + scm + finance + erp) into server-side composed read views (ADR-MONO-017 D1) |
| Deployable unit | `apps/console-bff/` |
| Data store | **None** (no persistence — see § Persistence) |
| Event publication | None (no outbox — composition reads only) |
| Event consumption | None (HTTP fan-out only — ADR-MONO-017 D1.A rejection of GraphQL/gRPC) |
| Outbound integration | 5 backend domains over HTTP (per-domain credential selection, ADR-MONO-017 D4 HARD INVARIANT) |

### Service Type Composition

`console-bff` is a single-type **`rest-api`** service per
[`platform/service-types/INDEX.md`](../../../../../platform/service-types/INDEX.md)
and inherits the mandatory requirements declared in
[`platform/service-types/rest-api.md`](../../../../../platform/service-types/rest-api.md)
verbatim — contract first, gateway routing exception (see § Edge Routing),
versioning, error envelope, JWT bearer validation, idempotency on mutating
endpoints (BFF has **none** at v1), pagination on list endpoints, observability.

The BFF is **not** an `event-consumer` even though it fans out across 5 domains
that publish events; per `platform/service-types/INDEX.md` ("REST service that
also fans out → `rest-api`"), the BFF's fan-out is a server-side composition of
**existing** read APIs, not a Kafka subscription. **Producer-immutability**
(ADR-MONO-017 § 3.1 / D3.A) means no domain adds an aggregating endpoint for
the BFF — composition is hand-authored route by route inside `console-bff` and
calls the existing GETs that FE-007/008/009/010 already bind.

---

## Architecture Style

**Hexagonal (Ports & Adapters)** — identical reasoning to
`finance-platform/account-service` and `erp-platform/masterdata-service`:
domain logic (composition rules, per-domain credential selection, degrade
policy) is framework-free and surrounded by adapters (inbound HTTP controllers,
outbound HTTP clients to the 5 domains). The BFF's "domain logic" is **composition
discipline** — not business invariants — but the same separation gives the same
test pyramid (domain unit + application use-case unit + slice + Testcontainers IT
that boots WireMock domain stubs).

Reference skill: [`.claude/skills/backend/architecture/hexagonal/SKILL.md`](../../../../../.claude/skills/backend/architecture/hexagonal/SKILL.md)

## Why This Architecture

- **Backend-engineer consistency** — every other Spring Boot backend service in
  the monorepo (`gateway-service`, `account-service`, `masterdata-service`,
  scm `procurement-service`, etc.) is Hexagonal. Reviewers and dispatched
  agents share one mental model; cross-stack reviews stay cheap.
- **Composition is the domain** — D1.A's "REST orchestrator" decision (server-side
  composition with no schema stitching, no producer retrofit) makes the BFF's
  **outbound port surface** the central abstraction (`DomainReadPort<wms>`,
  `DomainReadPort<scm>`, …, plus a `CredentialSelectionPort` enforcing D4). A
  layered/MVC style would scatter that across controllers and infrastructure.
- **Degrade policy is testable in isolation** — D5.A (per-domain CB inherited,
  per-card degrade rendered) is a domain-layer rule the adapters honor; unit
  tests on the composition use-case stub the outbound ports and assert the
  "responsive domains' cards + per-failed-domain degraded card" shape without
  bringing up any HTTP infrastructure.

## Tech Stack

- Java 21, Spring Boot 3.4 (Servlet stack)
- Spring Web (MVC), Spring Security 6 (OAuth2 Resource Server — see § Auth)
- [`libs/java-web`](../../../../../libs/java-web/) — `RestClient` + resilience
  primitives (Resilience4j circuit-breaker / retry / timeout) for the 5 outbound
  domain calls (ADR-MONO-017 D5.A)
- [`libs/java-security`](../../../../../libs/java-security/) — RS256 JWT validation
  primitives, tenant-claim helpers
- Micrometer + OTel for the BFF-specific metrics surface (ADR-MONO-017 D7.A)
- No JPA, no Flyway, no Redis — **stateless** (see § Persistence)

## Persistence

**None at v1.** The BFF holds no database, no cache, no session store. Composition
is request-scoped fan-out across the per-domain read APIs; each operator request
results in N outbound calls (where N = the number of domain legs the composition
route needs), with no BFF-side persistence between requests.

Rationale: ADR-MONO-017 D3.C (pre-aggregate via batch / materialized view) was
explicitly rejected for MVP — the BFF stays **read-only and stateless**, mirroring
ADR-MONO-015 D1-B (Composed-overview pattern). If a future composition's
per-leg fan-out cost becomes a Phase 7 production bottleneck, ADR-MONO-017 D3.B
(aggregating producer endpoints per domain) is the documented escape — not BFF
caching.

> The Phase 0 `platform-console` Local Network Convention (`infra/traefik/`)
> backing-service slot for Redis / DBs is **unused by console-bff**; the slot
> remains reserved at the project level for `console-web` SSR cache, not for
> this service.

---

## Auth Flow (HARD INVARIANT — ADR-MONO-017 D4)

> **The per-domain credential rule defined in
> [`console-integration-contract.md`](../../contracts/console-integration-contract.md)
> §§ 2.4.5 / 2.4.6 / 2.4.7 / 2.4.8 (and the GAP-domain § 2.6 RFC 8693 exchanged
> operator token) is a HARD INVARIANT this BFF inherits byte-unchanged. The
> BFF is the rule's *credential dispatcher*, never its rewriter.** Rejected
> options ADR-MONO-017 D4.B (single unified BFF token) and D4.C (operator-token-only
> across all domains) MUST NOT reappear in this service.

### Inbound (console-web → console-bff)

The BFF is called **server-side from `console-web`'s App-Router server routes**,
not from the browser. `console-web`'s server route already holds both tokens
established at the GAP OIDC login callback (per
[`console-web/architecture.md`](../console-web/architecture.md) § Auth Flow +
[FE-002a](../../../tasks/done/TASK-PC-FE-002a-console-operator-token-exchange-wiring.md)):

- the **GAP OIDC access token** (`getAccessToken()`) used today for the 4 non-GAP
  domains (wms / scm / finance / erp) — §§ 2.4.5–2.4.8 contract,
- the **RFC 8693 exchanged operator token** (`getOperatorToken()`) used today for
  the GAP `admin-api` surface — §§ 2.4.1–2.4.4 contract.

The console-web server route forwards **both** to the BFF on every call:

- `Authorization: Bearer <iam-oidc-access-token>` (treated by Spring Security
  OAuth2 Resource Server as the inbound principal — RS256, JWKS = GAP, standard
  validation: issuer / audience / exp / sig).
- `X-Operator-Token: <rfc8693-operator-token>` (carried request-scoped, not
  parsed by the inbound auth filter; available to outbound clients via a
  request-scoped `OperatorCredentialContext` bean).
- `X-Tenant-Id: <active-tenant>` (operator's selected active tenant from
  `getActiveTenant()`; producer-authoritative downstream — D6.A).

The browser **never** holds either token (HttpOnly cookie discipline preserved
end-to-end — ADR-MONO-017 D2.B rejection).

### Outbound — per-domain credential dispatch (D4.A, byte-verbatim)

For each outbound domain call the BFF picks the credential via the
`CredentialSelectionPort`, identical to the rule already implemented by the
console-web server routes today:

| Outbound domain | Credential | Header on outbound | Selector predicate |
|---|---|---|---|
| IAM (`/api/admin/**`) | RFC 8693 exchanged operator token (§ 2.6) | `Authorization: Bearer <operator-token>` | `domain == IAM` |
| wms (`/api/wms/**`) | GAP OIDC access token | `Authorization: Bearer <iam-oidc-access-token>` | `domain ∈ {wms,scm,finance,erp}` |
| scm (`/api/scm/**`) | GAP OIDC access token | `Authorization: Bearer <iam-oidc-access-token>` | (same) |
| finance (`/api/finance/**`) | GAP OIDC access token | `Authorization: Bearer <iam-oidc-access-token>` | (same) |
| erp (`/api/erp/**`) | GAP OIDC access token | `Authorization: Bearer <iam-oidc-access-token>` | (same) |

The inbound `X-Tenant-Id` is forwarded verbatim on every outbound leg (no
re-derivation — D6.A); each producer's `TenantClaimValidator` gates the call
(`tenant_id ∈ {<domain>,*}` per-domain). Absent inbound token / absent
operator-token-when-GAP-leg-needed / absent `X-Tenant-Id` are fail-closed
before any outbound call (`401 TOKEN_INVALID` / `400 NO_ACTIVE_TENANT`).

### Trust boundary

- The BFF **never** falls back from one credential to another. Missing operator
  token on a GAP leg = `401`, not "try GAP OIDC access token instead" (#569
  invariant preserved).
- The BFF **never** mints its own token (rejected D4.B).
- The BFF **never** rewrites or expands the per-domain producer-side tenant
  enforcement (D6.A — producer authority preserved).

### Logging discipline

Both tokens, all bearer header values, and PII fields surfaced in composed
responses MUST NOT appear in logs (account ids, masked IPs, operator emails,
money minor-units strings, employee/business-partner PII). Inherits the
[`console-web` § 2.6 logging invariant](../../contracts/console-integration-contract.md)
and the per-domain producer logging obligations (e.g. finance F7, erp E7).

---

## Federation Shape — Server-Side Fan-Out Only (D2.A)

`console-web` continues to call **single-domain** section endpoints directly
(the existing FE-001..010 routes are **NOT relocated** through the BFF).
`console-web` calls the **BFF** only for **cross-domain views** (composition
routes defined in [§ 2.4.9](../../contracts/console-integration-contract.md)).
This is the verbatim D2.A choice and the rejected D2.C (BFF subsumes
single-domain sections) MUST NOT be re-litigated without a fresh ADR amendment
(HARDSTOP-04). The 4 merged `features/{wms,scm,finance,erp}-ops` modules are
stable.

---

## Composition Routes — Reuse Existing Per-Domain Reads (D3.A)

Each composition route is **hand-authored** and calls the **existing**
per-domain read APIs verbatim (no `/summary` / `/dashboard-card` retrofit on
any producer — D3.B rejection):

- An "Operator Overview" composition (the MVP, TASK-PC-FE-011, post-skeleton)
  fans out across, e.g., `GET /api/admin/accounts` (count snapshot),
  `GET /api/wms/inventory/health`, `GET /api/scm/...`, `GET /api/finance/accounts/{op-default}`,
  `GET /api/erp/masterdata/departments` (count snapshot). The exact card set is
  defined in TASK-PC-FE-011's spec-first authoring of § 2.4.9; this skeleton
  task does not enumerate it.
- A composition use-case fires the N outbound calls **in parallel** (bounded by
  the inbound request's hard timeout — see § Resilience), collects per-leg
  outcomes (`ok` / `degraded` / `forbidden`), and renders the composed envelope
  the route's response schema dictates.
- Composition use-cases live in `application/composition/` (one per route) and
  depend only on outbound ports — they never reach into HTTP infrastructure
  directly. Adapters wire the `RestClient` instances per domain.

> **§ 3.3 zero-retrofit, fifth confirmation**: no per-domain producer spec or
> implementation change is required for Phase 7 MVP. The BFF skeleton itself
> introduces zero changes to wms / scm / finance / erp / GAP architecture.md
> files or contract files (other than the additive § 2.4.9 in
> `console-integration-contract.md`).

---

## Resilience (D5.A)

The BFF inherits the
[`console-integration-contract.md` § 2.5](../../contracts/console-integration-contract.md)
resilience patterns **per outbound domain** (circuit-breaker + retry + timeout)
via `libs/java-web` Resilience4j primitives.

- **Per-leg circuit-breaker keyed by `(domain, route)`** — a wms outage does not
  open the breaker for scm.
- **Per-leg hard timeout** bounded so the composition's total fan-out latency
  budget is not exceeded (composition latency budget = inbound request timeout
  − fan-out coordination overhead).
- **Aggregation degrade** — partial-failure composition rendering: every
  responsive leg's data + per-failed-leg `{ status: "degraded", domain, reason }`
  card. **All-down still returns 200 with an all-degraded envelope** — the
  composition route never blanks the dashboard (D5.B rejection: "BFF-level
  aggregation timeout (all-or-nothing)").
- **Bounded retry** on outbound legs only — never on the inbound request.

The rejected D5.B (all-or-nothing 503 on any single domain failure) MUST NOT
be reintroduced.

---

## Multi-Tenant Isolation (D6.A — Producer Authority)

- Inbound `X-Tenant-Id` is **forwarded verbatim** on every outbound leg.
- The BFF does **not** re-derive tenancy and does **not** relax tenancy. Each
  producer's `TenantClaimValidator` (`tenant_id ∈ {<domain>,*}`) remains the
  authoritative gate.
- Absent `X-Tenant-Id` on inbound is fail-closed (`400 NO_ACTIVE_TENANT`)
  **before** any outbound call — matches the `console-web` server-route
  behaviour today.
- Cross-tenant attempts are surfaced as the producer's `403 TENANT_FORBIDDEN`
  (per leg), rendered as a per-card "scope denied" placeholder (degrade
  classification `forbidden`, distinct from `degraded`) — never silently
  succeeds, never widens scope.

Rejected D6.B (BFF central tenant gate as primary) is documented as a future
defense-in-depth option, not a v1 path.

---

## Observability (D7.A)

The BFF emits per-outbound-domain attribution metrics + per-domain tracing
spans, reusing Vector + VictoriaMetrics ([ADR-MONO-007](../../../../../docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md))
**without** introducing a new stack.

Mandatory metric set (Micrometer):

- `bff_fanout_latency_seconds{domain,route}` — histogram per outbound leg.
- `bff_fanout_errors_total{domain,route,code}` — counter per outbound leg
  failure classification (`5xx`, `timeout`, `circuit_open`, `tenant_forbidden`,
  `permission_denied`).
- `bff_aggregation_degrade_count_total{dashboard,degraded_domain}` — counter
  whenever a composition response renders a degraded leg.
- Standard `rest-api` request-rate / error-rate / latency metrics from
  `platform/service-types/rest-api.md` (one inbound endpoint = one set).

Tracing:

- The inbound request's OTel trace context propagates to every outbound leg via
  W3C `traceparent` (the per-domain producers already accept it).
- Per-outbound-leg span carries `bff.domain` and `bff.route` attributes for
  per-domain attribution in the trace UI.

Logging (structured JSON, no token / PII):

- `traceId`, `userId` (operator subject, NOT email), `requestId`, `tenantId`,
  `route`, `domains`, per-leg `{ domain, status, latencyMs }` summary.

Rejected D7.B (BFF-level aggregate metrics only) is the "operator cannot diagnose
which leg degraded" anti-pattern; MUST NOT regress.

---

## Edge Routing (Local Network Convention)

`platform-console` is on the shared Traefik stack (`infra/traefik/`,
`TASK-MONO-022`, hostname-based routing, no `PORT_PREFIX`). The BFF gets a
dedicated hostname:

- `console-bff.local` → `apps/console-bff/` Spring Boot, registered via the
  docker-compose `traefik.http.routers.console-bff-*` labels (sibling of the
  existing `console.local` for `console-web`).
- Routing scope is **internal-only** (the BFF is not exposed to operator
  browsers — `console-web`'s server routes call it server-side; see § Auth
  Flow inbound). The Traefik router restricts it to the BFF host header; no
  external CORS surface.
- Backing service `expose:` only — there is no backing service for the BFF
  (stateless, see § Persistence).

The BFF deviates from `rest-api.md` "all external traffic enters through
gateway-service" because the platform-console domain has **no
`gateway-service`** — operator traffic enters at the Traefik-fronted
`console-web` hostname, which is the trust boundary; `console-web`'s
server-side calls into `console-bff` are the only inbound traffic. This is the
identical structural exception `console-web` itself takes.

---

## Test Pyramid (`platform/testing-strategy.md` + `rest-api.md`)

| Layer | Scope | Notes |
|---|---|---|
| Domain unit | `CredentialSelectionPort` selector predicate, degrade-policy rules, tenant pass-through invariant | Pure JUnit, no Spring context |
| Application unit | Per-composition use-case with `@ExtendWith(MockitoExtension.class)` STRICT_STUBS, stubbed outbound ports — assert per-leg success → composed envelope; per-leg `5xx`/`timeout`/`403` → degrade classification | One test per route shape × outcome |
| Slice (`@WebMvcTest`) | Each inbound controller + Spring Security wiring + GlobalExceptionHandler | Stubs the application use-case bean |
| Integration (`@SpringBootTest` + Testcontainers + WireMock) | Boots the full BFF + WireMock per-domain stubs + a fake GAP issuer; asserts per-domain credential dispatch (D4), tenant pass-through (D6), per-leg degrade (D5), zero log leakage of tokens/PII | Per-domain producer is stubbed; **no** Testcontainers per real downstream — composition is HTTP-only |

The skeleton task (TASK-PC-BE-001) lands the test harness + the first `GET
/actuator/health` slice + a smoke IT that boots the application context. The
first composition route's IT (Operator Overview) lands in TASK-PC-FE-011 per
its spec-first `§ 2.4.9` extension.

---

## Out of Scope (this service)

- **Persistent state of any kind** — no JPA / Flyway / Redis / Kafka outbox /
  Kafka consumer. The BFF is stateless across requests (see § Persistence).
- **Mutations / writes** at MVP — every Phase 7 dashboard at MVP is composition
  of **reads**. The first mutation surface, if ever added, requires a fresh
  ADR amendment (it would crack D3.A "reuse existing per-domain reads
  verbatim" and re-open the `Idempotency-Key` + `X-Operator-Reason` policy
  for the BFF layer). Out of scope for TASK-PC-BE-001 + TASK-PC-FE-011.
- **GraphQL / gRPC** — rejected at ADR-MONO-017 D1; never reintroduce without
  amending the ADR.
- **Schema stitching, federation gateways, BFF generic aggregation engines** —
  same (D1.A composition is **hand-authored route by route**).
- **Subsuming the single-domain `features/{wms,scm,finance,erp,accounts,...}`
  routes** — D2.C rejection.
- **Per-domain `/summary` / `/dashboard-card` aggregating producer endpoints** —
  D3.B rejection (zero retrofit § 3.3, fifth confirmation).
- **A new auth boundary / unified BFF token / operator-token-only-across-all-domains** —
  D4.B / D4.C rejections (HARD INVARIANT).
- **BFF-level all-or-nothing aggregation timeout** — D5.B rejection.
- **BFF central tenant gate as primary enforcement** — D6.B rejection.
- **BFF-level aggregate-only metrics (no per-domain attribution)** — D7.B
  rejection.

---

## Forbidden Patterns (recap, normative)

- Direct DB access — forbidden (no DB at v1).
- Long-running synchronous endpoints (> 5 seconds) — forbidden per
  `rest-api.md`. Composition routes that genuinely need long fan-out must be
  decomposed (multiple smaller routes) or pre-aggregated at the producer
  (ADR-MONO-017 D3.B), not held open.
- Caching composed responses cross-request (browser-side or BFF-side) at v1 —
  forbidden (introduces staleness operator-confusing; producer-side caching is
  already in scope per `cross-cutting/caching.md`, and producer rules apply
  there).
- Token fallback (e.g. "try operator token; on 401, retry with GAP OIDC token")
  — forbidden (#569 invariant + D4.A).
- Producer-spec mutation from the BFF side — forbidden (D3.A / § 3.3 zero
  retrofit fifth confirmation).

---

## Change Rule

Changes to this `architecture.md` require either (a) an ADR-MONO-017 amendment
recorded in [ADR-MONO-017](../../../../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md)
§ 6 Status Transition History (any D1–D8 decision change), or (b) an
additive note in this file's `> Provenance` block for non-decision-changing
clarifications. The HARDSTOP-04 + ADR-MONO-013 amendment discipline applies.
