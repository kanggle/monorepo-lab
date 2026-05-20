# Task ID

TASK-PC-FE-011

# Title

MVP "Operator Overview" cross-domain dashboard — first concrete `§ 2.4.9.X` composition route on top of `console-bff` skeleton (ADR-MONO-017 § 3.3 #3 / § D8; BE composition use-case + 5-domain `RestClient` fan-out + FE `features/operator-overview/` + `(console)/dashboards/overview` route; reuse-only / zero retrofit sixth confirmation)

# Status

ready

# Owner

backend-engineer + frontend-engineer (joint scope — BE composition use-case + FE consumer screen)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **depends on (ADR)**: [ADR-MONO-017](../../../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md) ACCEPTED 2026-05-20 (PR #666 `5c711e3b`). MVP "Operator Overview" cross-domain dashboard is ADR-MONO-017 § D8's explicit scope and § 3.3 #3's post-skeleton task.
- **depends on (skeleton)**: [TASK-PC-BE-001](../done/TASK-PC-BE-001-console-bff-skeleton.md) DONE 2026-05-20 (spec PR #668 `7b73728b` + impl PR #669 `13454cb0` + close chore #670 `883384a1`). `OperatorOverviewCompositionUseCase` placeholder + `DomainReadPort` + `CredentialSelectionPort` + degrade-policy primitives + 3 mandatory metric eager registrations are the building blocks this task fills in.
- **depends on (5 producer surfaces — consume-only, byte-unchanged)**:
  - GAP [`admin-api.md`](../../../global-account-platform/specs/contracts/http/admin-api.md) § Accounts (bound by § 2.4.1 / FE-002 / FE-005)
  - wms [`admin-service-api.md`](../../../wms-platform/specs/contracts/http/admin-service-api.md) § 1.1 Dashboard / Read-Model (bound by § 2.4.5 / FE-007)
  - scm [`gateway-public-routes.md`](../../../scm-platform/specs/contracts/http/gateway-public-routes.md) § *platform-console operator read consumer* (bound by § 2.4.6 / FE-008)
  - finance [`account-api.md`](../../../finance-platform/specs/contracts/http/account-api.md) § Balances (bound by § 2.4.7 / FE-009)
  - erp [`masterdata-api.md`](../../../erp-platform/specs/contracts/http/masterdata-api.md) § Departments (bound by § 2.4.8 / FE-010)
- **depends on (`console-web` server-side helpers)**: `getAccessToken()` + `getOperatorToken()` + `getActiveTenant()` from `shared/lib/session` (already shipped by FE-001 / FE-002a).
- **prerequisite for**: Phase 7 subsequent dashboards (domain health, throughput, …) — separate future tasks under additive `§ 2.4.9.2`, `§ 2.4.9.3`, … per ADR-MONO-017 § D8.
- **precedent**: [TASK-PC-FE-005](../done/TASK-PC-FE-005-console-operator-overview-dashboards-slice.md) (ADR-MONO-015 D1-B implementation — composed-overview pattern for GAP-only 3 leg). This task **generalises** that pattern across 5 domains via the new BFF; the GAP-only client-side composition stays for `features/dashboards`, this task adds the BFF-routed cross-domain composition for `features/operator-overview`.

---

# Goal

The first concrete `§ 2.4.9.X` composition route — `GET /api/console/dashboards/operator-overview` — that fans out across all 5 backend domains' existing read endpoints and renders the result in a new `features/operator-overview/` console section. This is **Phase 7 MVP** per ADR-MONO-017 § D8: validates D1-D7 against real cross-domain fan-out before any subsequent Phase 7 dashboard is added.

The composition route is the first **owned** BFF surface (versus the skeleton's `GET /actuator/health` which is operational only); it exercises the per-domain credential dispatcher (D4), the degrade-policy primitives (D5), the tenant pass-through (D6), and the observability metric emission (D7) end-to-end. The post-merge state proves the BFF architecture's MVP shape works and unblocks any further Phase 7 dashboard work.

# Scope

## In Scope

**Spec PR (this task md + § 2.4.9.1 + tests/no-drift fixture):**

1. **`console-integration-contract.md` § 2.4.9.1** authoring (already drafted in this spec PR) — surface table + composed producer table (5 rows) + response schema + error envelope + auth flow + resilience + observability label values + implementation guidance + hard invariants.
2. **Task md + `INDEX.md`** ready entry update.

**Impl PR (BE — `console-bff` composition use-case + 5-domain RestClient + controller):**

3. **5-domain `DomainReadPort` adapters** in `console-bff/adapter/outbound/http/`:
   - `GapAccountsReadAdapter` — `GET /api/admin/accounts?page=0&size=1` via `RestClient` with `getOperatorToken()` credential (via `CredentialSelectionPort.selectFor(GAP)`)
   - `WmsInventoryReadAdapter` — `GET /api/v1/admin/dashboard/inventory` with GAP OIDC token (via `selectFor(WMS)`)
   - `ScmInventoryReadAdapter` — `GET /api/scm/inventory/visibility` with GAP OIDC token (via `selectFor(SCM)`)
   - `FinanceBalanceReadAdapter` — `GET /api/finance/accounts/{operatorDefaultAccountId}/balances` with GAP OIDC token; if no default account id is resolvable from request context → returns `forbidden / MISSING_PREREQUISITE` per § 2.4.9.1 impl guidance (option b — MVP-correct minimal path)
   - `ErpDepartmentsReadAdapter` — `GET /api/erp/masterdata/departments?active=true&page=0&size=1` with GAP OIDC token (via `selectFor(ERP)`)

4. **`RestClientConfig` 5 named `RestClient` beans** — one per outbound domain, with:
   - Resilience4j circuit-breaker + retry + timeout per `(domain, route)` key (from `libs/java-web` primitives)
   - OTel `traceparent` propagation interceptor (already on the base `RestClient.Builder` from skeleton)
   - Per-domain base URL from `application.yml` (`consolebff.outbound.<domain>.base-url`, env-overridable e.g. `CONSOLE_BFF_OUTBOUND_WMS_BASE_URL`)
   - Hard-coded outbound timeout budget (configurable, default 2s per leg, composition total ~5s — within `platform/service-types/rest-api.md` "no long-running synchronous endpoints > 5s" rule)

5. **`OperatorOverviewCompositionUseCase` body** (replacing the skeleton placeholder):
   - Reads `OperatorCredentialContext` (operator token + tenant id) — fail-closed on absence
   - Fires 5 outbound legs in **parallel** via Java 21 `Executors.newVirtualThreadPerTaskExecutor()` or `CompletableFuture.allOf(...)` with the composition total timeout
   - Maps each `DomainReadPort.call(...)` result to a `LegOutcome` (`ok` / `degraded` / `forbidden`) using existing `DegradePolicy` primitive
   - Emits per-leg metrics (`bff_fanout_latency_seconds`, `bff_fanout_errors_total` on failure, `bff_aggregation_degrade_count_total` on degrade/forbidden)
   - Returns composed response envelope (fixed 5-card order: gap, wms, scm, finance, erp)

6. **Controller** `OperatorOverviewController` (`@RestController` in `adapter/inbound/web/`):
   - `@GetMapping("/api/console/dashboards/operator-overview")`
   - Returns the composed envelope `ResponseEntity<OperatorOverviewResponse>` (200 always — all-down still 200 with all-degraded envelope per § 2.4.9.1)
   - Inbound validation: `Authorization` bearer (Spring Security handles), `X-Operator-Token` non-blank, `X-Tenant-Id` non-blank — if absent return appropriate envelope code via `MissingTenantException` / `MissingCredentialException` (already wired)

7. **`SecurityConfig` update**: composition route is `authenticated()` (no `permitAll` matcher — by default `anyRequest().authenticated()` catches it).

8. **Tests (BE)**:
   - Domain unit: `OperatorOverviewCompositionUseCase` test with 5 mocked `DomainReadPort` beans; assert 5-row dispatch + per-leg outcome shape × 6 combinations (all-ok / one-degraded / one-forbidden / all-degraded / all-forbidden / mixed)
   - Application unit: `LegOutcome` factory test (`ok(data)`, `degraded(reason)`, `forbidden(reason)`)
   - Slice (`@WebMvcTest(OperatorOverviewController.class)`): inbound validation (200 happy / 400 missing tenant / 401 missing token) + response envelope shape
   - IT (`@Tag("integration")`, `@SpringBootTest`): full Spring context + WireMock × 5 producers + per-domain credential dispatch assertion (correct token per outbound leg) + `/api/console/dashboards/operator-overview` happy-path + per-card degrade simulation (WireMock 503 / 403 / timeout) + composed envelope structure + 3 metric families emit at `/actuator/prometheus`

**Impl PR (FE — `console-web` operator-overview screen):**

9. **`features/operator-overview/`** (`console-web/src/`):
   - `api/operator-overview-types.ts` — TypeScript types matching § 2.4.9.1 response schema (5-card envelope)
   - `api/operator-overview-api.ts` — typed fetcher (`fetchOperatorOverview()`), server-side only
   - `components/OperatorOverviewScreen.tsx` (server component) — main container, renders `<DomainCard>` × 5 in fixed order + `<OverviewDegradeBanner>` if all-down
   - `components/DomainCard.tsx` (server component) — per-domain card with 3 rendering branches (`ok` data summary / `degraded` placeholder + retry / `forbidden` placeholder)
   - `components/OverviewDegradeBanner.tsx` (server component) — banner if all 5 cards are non-ok
   - `hooks/use-operator-overview.ts` — React Query hook for client-side retry (server-component first hydration + client-side `<RetryButton>` triggers refetch)

10. **`(console)/dashboards/overview/page.tsx`** — Next.js App Router server route, calls server-side `fetchOperatorOverview()` and renders `<OperatorOverviewScreen>`.

11. **`api/console/dashboards/operator-overview/route.ts`** — Next.js App Router server route that proxies to `console-bff`, forwards `Authorization` + `X-Operator-Token` + `X-Tenant-Id` from server-side session (`getAccessToken()` + `getOperatorToken()` + `getActiveTenant()` from `shared/lib/session`).

12. **In-console navigation entry** — `<MainNav>` (or equivalent) gets "Operator Overview" entry with route `/dashboards/overview`.

13. **Tests (FE)**:
   - Unit: `operator-overview-api.ts` (types + happy fetch + error fetch via vi.mocked); `OperatorOverviewScreen` snapshot/structure (5 cards in fixed order); per-card rendering branches (`ok` / `degraded` / `forbidden` shapes); `OverviewDegradeBanner` rendering on all-down; `use-operator-overview` retry behavior
   - No-drift: `parity-verification.test.ts` (existing) ensures § 3 parity matrix count stays exactly 16 (operator-overview is additive § 2.4.9.X, NOT a § 3 row)

## Out of Scope

- **Subsequent Phase 7 dashboards** (domain health, throughput, …) — separate future tasks per ADR-MONO-017 § D8.
- **`operatorDefaultAccountId` GAP registry surface enhancement** — option (a) in § 2.4.9.1 impl guidance; deferred to a separate spec-first GAP change. MVP uses option (b): finance card renders `forbidden / MISSING_PREREQUISITE` when no default account is resolvable.
- **Auto-refresh / polling** — operator-initiated retry only (per § 2.4.4 / § 2.4.9 invariant — bounded fan-out, meta-audit-respecting). No `setInterval`.
- **Client-side cross-domain composition** — D2.C rejection. `console-web` calls the BFF route server-side; existing per-domain `features/{accounts,wms-ops,scm-ops,finance-ops,erp-ops}` routes are **NOT relocated** through the BFF.
- **Mutation surface** — `Idempotency-Key` / `X-Operator-Reason` / POST/PUT/PATCH/DELETE on this or any future § 2.4.9.X route. ADR-MONO-017 § 2.4.9 hard invariant.
- **New observability stack** — Vector + VictoriaMetrics reuse only (ADR-MONO-006 / § 2.4.9 D7.A).
- **Producer spec/contract changes** to any of the 5 backend domains — zero retrofit sixth confirmation.
- **GraphQL / gRPC / aggregating producer endpoints** — D1.B/C/D + D3.B rejections.

# Acceptance Criteria

**Spec PR**:

- **AC-1 (contract)**: `console-integration-contract.md` § 2.4.9.1 exists with surface table (`GET /api/console/dashboards/operator-overview`) + composed producer table (exactly **5 rows** = gap/wms/scm/finance/erp + each row's authoritative producer endpoint + per-row credential per § 2.4.9 D4 verbatim) + response schema (5-card envelope with fixed order + per-card `status` + optional `data`/`reason`) + error envelope (400 NO_ACTIVE_TENANT / 401 TOKEN_INVALID; never 503) + auth flow restatement + resilience restatement + observability label values + impl guidance + hard invariants section. § 2.4.1-2.4.8 + § 2.4.9 (excluding the new § 2.4.9.1) + § 2.5 + § 2.6 + § 3 parity matrix (count=**16**) byte-unchanged.
- **AC-2 (no orphan refs)**: every cross-ref link in § 2.4.9.1 resolves to a file that exists at the path; `validate-rules` (skill) green.
- **AC-3 (task md)**: this task file (`TASK-PC-FE-011-mvp-operator-overview.md`) exists in `tasks/ready/` with all 7 required sections + Verification + DoD; `INDEX.md` ready section has the entry.

**Impl PR (BE)**:

- **AC-4 (build)**: `./gradlew :projects:platform-console:apps:console-bff:check` exit 0 + `:integrationTest` GREEN in CI.
- **AC-5 (controller + route)**: `GET /api/console/dashboards/operator-overview` returns 200 with the composed envelope (fixed 5-card order: gap, wms, scm, finance, erp).
- **AC-6 (composition use-case)**: `OperatorOverviewCompositionUseCase` body fires 5 parallel outbound legs, collects per-leg outcomes, emits per-leg metrics, returns envelope. All-down → 200 with all-degraded/forbidden envelope (never 503).
- **AC-7 (per-domain credential dispatch verified end-to-end)**: IT asserts each of the 5 outbound legs carries the correct credential per § 2.4.9 D4 — GAP leg's `Authorization` = operator token, other 4 legs' `Authorization` = GAP OIDC access token. No fallback path exercised.
- **AC-8 (tenant pass-through)**: every outbound leg includes `X-Tenant-Id` (forwarded verbatim from inbound). Absent inbound → `400 NO_ACTIVE_TENANT` before any outbound (IT assertion).
- **AC-9 (per-leg degrade)**: WireMock-simulated 503 on one leg → that card's `status="degraded", reason="DOWNSTREAM_ERROR"`; other cards still `ok`. WireMock-simulated 403 on one leg → that card's `status="forbidden", reason="PERMISSION_DENIED"`. All-down → all-degraded envelope, status 200.
- **AC-10 (401 cross-leg)**: WireMock-simulated 401 on one outbound leg → composition response is `401 TOKEN_INVALID` (per § 2.4.4 D3 / § 2.4.9.1 — auth is not a per-card degrade).
- **AC-11 (finance MISSING_PREREQUISITE)**: when `operatorDefaultAccountId` is not resolvable (per MVP option b), finance card renders `forbidden / MISSING_PREREQUISITE`; other 4 cards proceed normally.
- **AC-12 (observability emit)**: `/actuator/prometheus` shows `bff_fanout_latency_seconds{domain,route="operator-overview"}` with samples for all 5 domains + `bff_fanout_errors_total{...,code=...}` increments on simulated failures + `bff_aggregation_degrade_count_total{dashboard="operator-overview",degraded_domain=...}` increments per degraded/forbidden card.
- **AC-13 (no producer retrofit)**: zero diff in `projects/{global-account-platform,wms-platform,scm-platform,finance-platform,erp-platform}/specs/contracts/` AND `projects/{...}-platform/apps/` (PR diff inspection).
- **AC-14 (hard invariants byte-unchanged)**: ADR-MONO-017 D1-D8 + console-integration-contract.md § 2.4.1-2.4.8 + § 2.4.9 (excluding new § 2.4.9.1) + § 2.5 + § 2.6 + § 3 (count=16) byte-unchanged.
- **AC-15 (no mutation scaffolding)**: production code (`src/main/`) grep returns zero `Idempotency-Key` / `X-Operator-Reason` string literals (Javadoc absence-assertion exempt — same as TASK-PC-BE-001).
- **AC-16 (no credential fallback path)**: production code grep for fallback patterns (`orElse(getAccessToken())`, `catch.*getOperatorToken`, etc.) returns zero matches.

**Impl PR (FE)**:

- **AC-17 (build)**: `pnpm build` exit 0 + `pnpm test` (vitest) green + `pnpm lint` 0.
- **AC-18 (Next.js route + screen)**: `(console)/dashboards/overview/page.tsx` server-component renders `<OperatorOverviewScreen>` with 5 `<DomainCard>` children in fixed order (gap, wms, scm, finance, erp).
- **AC-19 (server-side proxy)**: `api/console/dashboards/operator-overview/route.ts` forwards `Authorization` + `X-Operator-Token` + `X-Tenant-Id` from `shared/lib/session` to `console-bff` URL (env `CONSOLE_BFF_URL`, default `http://console-bff.local`). Browser never sees the headers.
- **AC-20 (per-card UI branches)**: `<DomainCard>` renders 3 distinct shapes (`ok` / `degraded` / `forbidden`) per § 2.4.9.1; `<OverviewDegradeBanner>` renders only when all 5 cards are non-ok; vitest snapshots cover each branch.
- **AC-21 (in-console nav)**: navigation entry "Operator Overview" → `/dashboards/overview` added.
- **AC-22 (§ 3 parity unchanged)**: `parity-verification.test.ts` no-drift guard passes — attestation-marker count = 16, operator-overview is § 2.4.9.X additive (NOT a § 3 row).
- **AC-23 (no producer-side change)**: zero diff in any backend service code (`projects/{global-account-platform,wms-platform,scm-platform,finance-platform,erp-platform}/`).
- **AC-24 (server-component-first)**: page + screen + cards are server components by default; only `<RetryButton>` / `useOperatorOverview()` hook are client-side (`'use client'`).

# Related Specs

- `rules/common.md` + `rules/domains/saas.md` (declared domain) + `rules/traits/{multi-tenant,integration-heavy,audit-heavy}.md` (declared traits).
- [`platform/service-types/rest-api.md`](../../../../platform/service-types/rest-api.md) (mandatory rest-api requirements — contract first, JWT bearer, observability; idempotency/pagination N/A for read-only composition).
- [`platform/service-types/frontend-app.md`](../../../../platform/service-types/frontend-app.md) (mandatory frontend-app requirements — HttpOnly cookie auth, typed API client, server-component first, perf/a11y budgets).
- [`platform/architecture-decision-rule.md`](../../../../platform/architecture-decision-rule.md), [`platform/testing-strategy.md`](../../../../platform/testing-strategy.md), [`platform/hardstop-rules.md`](../../../../platform/hardstop-rules.md).
- [`docs/adr/ADR-MONO-017`](../../../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md) (governing ADR; § D8 MVP scope; § 3.3 #3 post-skeleton task; D1-D8 HARD INVARIANT byte-unchanged).
- [`docs/adr/ADR-MONO-013`](../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md) § D5 / § D6 Phase 7 / § 3.3 (parent prescription).
- [`docs/adr/ADR-MONO-015`](../../../../docs/adr/ADR-MONO-015-platform-console-dashboards-model.md) D1-B (Composed-overview pattern this task generalises across 5 domains).
- [`docs/adr/ADR-MONO-014`](../../../../docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md) (operator token — GAP leg credential).
- [`docs/adr/ADR-MONO-006`](../../../../docs/adr/ADR-MONO-006-observability-stack.md) (Vector + VictoriaMetrics — D7.A reuse base).
- [`docs/adr/ADR-MONO-001`](../../../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) Option C (hostname-based routing; `console-bff.local` internal-only).
- [`projects/platform-console/PROJECT.md`](../../PROJECT.md) (classification + Service Map v1 — already updated by TASK-PC-BE-001).
- [`projects/platform-console/specs/services/console-bff/architecture.md`](../../specs/services/console-bff/architecture.md) (BFF architecture — Hexagonal, D1-D8 reflected; this task fills in composition use-case body + outbound `RestClient` instantiation).
- [`projects/platform-console/specs/services/console-web/architecture.md`](../../specs/services/console-web/architecture.md) (Layered by Feature — `features/operator-overview/` is the new feature module).
- precedent: [`projects/platform-console/tasks/done/TASK-PC-FE-005-console-operator-overview-dashboards-slice.md`](../done/TASK-PC-FE-005-console-operator-overview-dashboards-slice.md) (GAP-only Composed-overview slice; generalised here).

# Related Skills

- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
- `.claude/skills/backend/springboot-api/SKILL.md`
- `.claude/skills/backend/jwt-auth/SKILL.md` (per-domain credential dispatch)
- `.claude/skills/cross-cutting/observability-setup/SKILL.md` (per-leg metric emit)
- `.claude/skills/backend/testing-backend/SKILL.md` (WireMock × 5 IT pattern)
- `.claude/skills/frontend/layered-by-feature/SKILL.md` (features/operator-overview/ module)
- `.claude/skills/frontend/server-components/SKILL.md` (server-first rendering)
- `.claude/skills/frontend/testing-frontend/SKILL.md` (vitest snapshot + branch coverage)

# Related Contracts

- **Authored in this spec PR**: `console-integration-contract.md` § 2.4.9.1 (BFF surface — first concrete composition route).
- **Byte-unchanged enforce**: § 2.4.1-2.4.8 + § 2.4.9 (excluding § 2.4.9.1) + § 2.5 + § 2.6 + § 3 parity matrix (count=16). PR diff inspection verifies.
- **Producer-side contract changes = 0**: 5 producer specs (`{global-account-platform,wms-platform,scm-platform,finance-platform,erp-platform}/specs/contracts/`) byte-unchanged (zero retrofit, sixth confirmation).

# Target Service

- BE: `console-bff` (composition use-case + 5 RestClient adapters + controller)
- FE: `console-web` (`features/operator-overview/` + route + nav)

# Architecture

Follow:

- `projects/platform-console/specs/services/console-bff/architecture.md` (Hexagonal — application use-case + outbound port + adapter; D1-D8 byte-unchanged)
- `projects/platform-console/specs/services/console-web/architecture.md` (Layered by Feature — `features/operator-overview/` is the new feature)

# Implementation Notes

**Spec PR first, impl PR second** — HARDSTOP-09 (architecture/contract decisions precede code). Impl PR base = spec PR-merged main.

**3-PR pattern** (similar to FE-010 / FE-007/008 sequence):
1. spec PR — this task md + `§ 2.4.9.1` authoring + `INDEX.md` ready entry.
2. impl PR — BE + FE in one PR (joint scope, single dependency edge). Or split BE/FE if scope feels too large. Decision: **single impl PR** because the 5-domain outbound mappings are tightly coupled between BE and FE response schema; splitting risks contract drift.
3. close chore PR — `review/ → done/` + INDEX move.

**Parallel fan-out (BE)**: use Java 21 virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) or `CompletableFuture.allOf(...)` for the 5 parallel outbound legs. Composition total timeout = 5s (each leg 2s budget + 1s coordination overhead). Verify via IT that one slow leg doesn't extend the composition beyond the budget (slow leg → `degraded` with `reason="TIMEOUT"`).

**`operatorDefaultAccountId` resolution (MVP option b)**: when no default account id is available (inbound has none; no GAP registry surface defines one yet), finance leg returns `forbidden / MISSING_PREREQUISITE`. This is **honest constraint surfacing**, not a feature gap — finance v1 has no list/search GET (per § 2.4.7), so no account list to summarize. Document this in the FE `<DomainCard>` rendering for finance card on `forbidden/MISSING_PREREQUISITE`: "Configure a default finance account in operator profile" actionable hint.

**`asOf` field**: server-side `Instant.now()` at composition request entry (NOT per-leg response). Operators see one timestamp for the whole envelope.

**Per-leg credential dispatch — extend [`CredentialSelectionPort`](../../apps/console-bff/src/main/java/com/kanggle/platformconsole/bff/domain/credential/CredentialSelectionPort.java) 사용**: skeleton의 `CredentialSelectionAdapter` 가 이미 5-row sealed switch 구현. 본 task는 그것을 호출 (`port.selectFor(DomainTarget.GAP)` 등). 새 dispatch logic 추가 금지 — skeleton의 SealedSwitch 가 contract authority.

**FE server-component first**: `<OperatorOverviewScreen>`, `<DomainCard>`, `<OverviewDegradeBanner>` 모두 server components. `<RetryButton>` (client component) 만 `'use client'` 표기 + React Query `useOperatorOverview()` hook 호출.

**Branch naming**: `task/pc-fe-011-operator-overview-spec` (spec PR) + `task/pc-fe-011-operator-overview-impl` (impl PR). 어디에도 `master` substring 금지.

**Lessons from TASK-PC-BE-001 (10-cycle diagnostic chain — apply preemptively)**:
- IT assertion에 AssertJ `.as("non-200 body:\n%s", body)` annotation 디폴트 적용 (assertion strengthen only — never weaken).
- `application-test.yml` 의 `server.error.include-stacktrace: always` + `org.springframework.security: DEBUG` 유지 (skeleton에서 이미 적용됨).
- 새 outbound `RestClient` 추가 시 explicit bean 정의 — autoconfig 의존 안 함 ([[feedback-spring-boot-diagnostic-patterns]] § 7 패턴).

# Edge Cases

- **Inbound `X-Tenant-Id` 부재** → fail-closed `400 NO_ACTIVE_TENANT` before any outbound (mirror skeleton + § 2.4.9).
- **Inbound `Authorization` bearer 부재** → `401 TOKEN_INVALID` from Spring Security default filter (entrypoint = our `onAuthenticationFailure`).
- **Inbound `X-Operator-Token` 부재** → composition use-case 가 GAP leg dispatch 시 `MissingCredentialException` throw → composition-level `401 TOKEN_INVALID`.
- **5 legs all fail (e.g. all 503)** → 200 with all-degraded envelope (never 503; D5.B rejection).
- **One leg 401** → composition-level `401` (per § 2.4.4 D3 cross-leg discipline + § 2.4.9.1).
- **One leg 403** → per-card `forbidden / PERMISSION_DENIED` or `TENANT_FORBIDDEN`; other cards proceed.
- **One leg timeout** → per-card `degraded / TIMEOUT`; circuit-breaker opens for that leg after threshold.
- **One leg circuit_open** → per-card `degraded / CIRCUIT_OPEN`.
- **Finance leg `operatorDefaultAccountId` not resolvable** → finance card `forbidden / MISSING_PREREQUISITE` (MVP option b); other 4 cards proceed.
- **wms `X-Read-Model-Lag-Seconds` header on wms response** → optional card-level hint surfaced (not in MVP envelope schema — defer to subsequent dashboard or wms-specific UI).
- **scm response carries `meta.warning: "Not for procurement decisions (S5)"`** → optional card-level S5 hint surfaced per § 2.4.6 invariant.
- **Operator switches tenant mid-request** → composition uses inbound tenant snapshot; next request reflects new tenant (no in-flight tenant switch).

# Failure Scenarios

- **architecture.md / § 2.4.9 hard invariant 위반** → AC-14 reject. Mitigation: spec PR diff inspection + impl PR `git diff origin/main` 5 producer + `apps/console-web` files = 0.
- **Credential fallback path 도입** → AC-16 + ADR-MONO-017 D4.A 위반 (HARD INVARIANT). Mitigation: code review grep for fallback patterns; skeleton의 `CredentialSelectionAdapter` 재사용 강제 (새 dispatch logic 작성 금지).
- **Mutation scaffolding 도입** (`Idempotency-Key` / `X-Operator-Reason` literal in `src/main/`) → AC-15 + § 2.4.9 invariant 위반. Mitigation: grep enforcement.
- **Producer spec/contract 수정** → AC-13 + zero retrofit 위반. Mitigation: PR diff inspection.
- **§ 3 parity 카운트 변동** → AC-22 위반. Mitigation: FE-006 `parity-verification.test.ts` no-drift guard + spec PR diff에서 § 3 section 미수정 확인.
- **All-down → 503** → AC-9 위반 (D5.B rejection 복원). Mitigation: composition use-case unit test가 명시 assert "all-down → 200 with all-degraded envelope".
- **Auto-refresh / polling 도입** → out-of-scope; § 2.4.4 / § 2.4.9 invariant 위반. Mitigation: code review에서 `setInterval` / `useInterval` / `refetchInterval` 부재 확인.
- **Per-leg parallelism 부재 (sequential fan-out)** → composition latency budget 위반. Mitigation: IT가 한 leg 의 latency 가 늘면 다른 legs 가 영향 받지 않음을 측정 (parallel timing test).

# Test Requirements

**BE**:
- Domain unit: `OperatorOverviewCompositionUseCase` × 6 outcome combinations; `LegOutcome` factory; `DegradePolicy` rules.
- Application unit: composition use-case 가 mocked 5 ports로 parallel 호출 시 단일 outcome 합성.
- Slice (`@WebMvcTest`): `OperatorOverviewController` happy/400/401; `GlobalExceptionHandler` envelope shape.
- IT (`@Tag("integration")` + WireMock × 5): full Spring context + 5 producer stubs + per-domain credential dispatch verified per outbound + 5-card envelope shape + per-leg degrade × 6 combinations + 3 metric family emit on `/actuator/prometheus` + composition timeout enforcement (slow leg degrades, others stay ok).

**FE**:
- vitest unit: `operator-overview-api.ts` (happy / error fetch); `OperatorOverviewScreen` structure (5 cards, fixed order); `DomainCard` × 3 branches (`ok` / `degraded` / `forbidden`) snapshots; `OverviewDegradeBanner` rendering on all-down; `use-operator-overview` retry hook behavior.
- no-drift: `parity-verification.test.ts` 가 attestation-marker count = 16 unchanged 검증.

**CI**:
- `platform-console-bff-integration-tests` job (existing, from TASK-PC-BE-001) → 새 IT 추가됨에 따라 자동 cover.
- `platform-console-frontend-*` job (existing) → vitest + lint + build 자동 cover.

# Definition of Done

- [ ] **spec PR**: § 2.4.9.1 신규 sub-section + 이 task md + `INDEX.md` ready entry, all reviewed/merged.
- [ ] **impl PR**: BE composition use-case body + 5 outbound `DomainReadPort` adapter + controller + `RestClientConfig` 5 RestClient beans + FE `features/operator-overview/` + `(console)/dashboards/overview` 라우트 + server-side proxy route + in-console nav entry + 24 AC verified.
- [ ] Tests added (BE: domain unit + slice + IT × 6 combinations) + FE (vitest unit + snapshot + no-drift) + all PASS locally + CI green.
- [ ] Hard invariants byte-unchanged: ADR-MONO-017 D1-D8 + § 2.4.1-2.4.8 + § 2.4.9 (excluding § 2.4.9.1) + § 2.5 + § 2.6 + § 3 count=16.
- [ ] Producer spec/contract change = 0; producer code change = 0; `apps/console-web` of other features = 0.
- [ ] Specs updated first (HARDSTOP-09): spec PR가 impl PR 전에 main 머지.
- [ ] Close chore PR가 `review/ → done/` 이동 + INDEX update + BE-299 re-stage check verified + BE-303 객관 머지 검증 후 시작.
- [ ] Ready for review.

# Verification

1. `console-integration-contract.md` § 2.4.9.1 신규 sub-section 존재 + 5-row composed producer table + response schema + hard invariants + § 2.4.1-2.4.9 (excluding new § 2.4.9.1) + § 2.5/6 + § 3 byte-unchanged (PR diff inspection).
2. 5 producer spec 디렉토리 변경 0 + 5 producer service apps/ 변경 0 (PR diff).
3. `./gradlew :projects:platform-console:apps:console-bff:check` exit 0 + `:integrationTest` GREEN.
4. `pnpm -F console-web test` + `pnpm -F console-web build` + `pnpm -F console-web lint` exit 0.
5. IT assertions: 5-card envelope (fixed order gap/wms/scm/finance/erp) + per-domain credential dispatch (correct token per outbound) + per-leg degrade × 6 combinations + 401 cross-leg + 400 NO_ACTIVE_TENANT + 3 metric family emit.
6. `parity-verification.test.ts` 가 § 3 count = 16 unchanged 검증.
7. Production code grep: `Idempotency-Key` / `X-Operator-Reason` 0 string literals; credential fallback path 0.
8. self-CI run에서 `platform-console-bff-*` + `platform-console-frontend-*` job GREEN + 다른 service jobs baseline 회귀 0.
9. self-review APPROVED (review-checklist Spec / Arch / Quality / Security / Testing).
