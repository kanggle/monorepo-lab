# Task ID

TASK-PC-FE-009

# Title

console-web Phase 5 — finance operations console section (read-only: account + balances + transactions)

# Status

done

# Owner

frontend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- api
- code
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

- **depends on**: **ADR-MONO-013 ACCEPTED** (TASK-MONO-108) § D6 **Phase 5** (finance console section, "built to the proven contract" — governed by ADR-MONO-008, **not re-decided here**); `TASK-PC-FE-007` (wms) + `TASK-PC-FE-008` (scm) — establish the **non-GAP per-domain client + per-domain-credential** pattern this slice **reuses** (do not re-derive it). Both **MERGED** → origin/main (FE-007 #633 `81395376`; FE-008 #637 `c34fc0ac`). The `console-integration-contract.md` **§ 2.4.5** (FE-007 wms — defines the per-domain credential rule) + **§ 2.4.6** (FE-008 scm — proves it generalises, flat-envelope + read-only discipline) are on main; **§ 2.4.7** (this task) **reuses** them for finance (do not redefine).
- **BLOCKED ON cross-project spec-first prerequisite (this is why the task is in `backlog/`, not `ready/`)**: the finance-side reconciliation = **`TASK-FIN-BE-005`** (`finance-platform`, *platform-console operator read-consumer spec reconciliation*). It records `platform-console` (ADR-MONO-013 Model B) as a **sanctioned external operator read consumer** of finance's existing read surface — a **(B) document/accept** of the *already-existing* finance JWT chain (`AllowedIssuersValidator` + `TenantClaimValidator` `tenant_id ∈ {finance,*}` + `X-Token-Type=user`) under the governing ADR-MONO-013 (no finance ADR; finance domain governance stays ADR-MONO-008; the "v1 backend-only / user-flow-client v2-deferred" narrative scopes finance's *own* frontend/user-flow client, not authorized external API consumers). **Authored + linked here**; this task moves `backlog → ready` **only after** `TASK-FIN-BE-005` **and** the FE-007/FE-008 dependency are all merged (spec-first; CLAUDE.md "Specs win over tasks" — mirrors the GAP `TASK-BE-296` ⊃ `FE-001` and scm `TASK-SCM-BE-015` ⊃ `FE-008` gating). The `backlog → ready` move (INDEX criterion: "cross-project prerequisite tasks identified and linked"; this task's AC: "prerequisite authored + linked + merged") is satisfied by FIN-BE-005 merge.
- **part of**: ADR-MONO-013 § D6 **Phase 5** — the finance console section. Phase 4 (FE-007 wms + FE-008 scm) proved the non-GAP contract across two domains; Phase 5 finance inherits it with **zero contract retrofit** (the § 3.3 assumption — Phase 5 is its third confirmation). Phase 6 erp console section is the future analog (separate erp ADR).
- **spec-first**: the console-side contract extension (**new § 2.4.7 finance binding** in `console-integration-contract.md` + `console-web/architecture.md` `features/finance-ops` module) lands before/with the code, **after** the finance-side prerequisite spec change merges. finance `account-api.md` is **unchanged** (cross-reference only — finance owns it).
- **contract-extension → Opus**: ADR-MONO-013 § D6 Phase 5 = "Opus/Sonnet"; this slice **extends the contract** (§ 2.4.7) and depends on a cross-project spec reconciliation → **Opus** (the same reasoning that put FE-007/FE-008 contract extensions on Opus).

# Goal

Build the console's **finance operations section** — ADR-MONO-013 Phase 5. It is a server-side, tenant-scoped, **read-only** section over finance's existing `account-service` read surface:

- **account** — `GET /api/finance/accounts/{id}` (account + balances: status, currency, kycLevel, per-currency `ledger/available/held`).
- **balances** — `GET /api/finance/accounts/{id}/balances` (available/ledger/held per currency).
- **transactions** — `GET /api/finance/accounts/{id}/transactions` (paginated `?page=&size=&type=&status=`; type/status filters; `counterpartyAccountId?`, `reversalOfTransactionId?`).

finance v1 has **no `admin-service`** (deferred to finance v2 — `PROJECT.md` v2 Service Map / ADR-MONO-008 § D3). There is therefore **no operator-mutation parity** for finance at v1; the section is **strictly read-only** (closest to the FE-008 scm precedent). The finance **write/mutation** surface (`POST /accounts`, `/kyc/upgrade`, `/holds`, `/holds/{holdId}/capture`, `/holds/{holdId}/release`, `/transfers`) is domain fund-movement / operator-domain mutation (`Idempotency-Key`, fintech F1) — **not** an operator-parity console surface; explicitly out of scope.

**Honest finance read-surface constraint (recorded, not papered over)**: finance v1 exposes **no account list/search `GET`** — only `GET /accounts/{id}`. The section is therefore **account-id-driven** (operator supplies/selects an `accountId`; no searchable account index at v1). This is the *inverse* of the FE-002 GAP situation (GAP had no GET-by-id and composed a detail view from search; finance has GET-by-id but no list) — do **not** fabricate a non-existent finance list/search endpoint. A list/search surface, if ever needed, is a finance producer-side spec-first change (out of scope here).

Auth follows the FE-007/FE-008 non-GAP pattern: finance account-service = GAP RS256 JWT (ADR-001), `tenant_id ∈ {finance,*}` from the JWT claim — the **GAP OIDC access token** (`getAccessToken()`), **not** the GAP operator-token-exchange (§ 2.6). This **reuses** the § 2.4.5 per-domain-credential rule (no re-derivation), with the **same outcome as wms and scm**.

# Scope

## In Scope

### Cross-project prerequisite (must land first — finance project-internal, separate task)

- A finance-side spec-first change recording `platform-console` as a sanctioned operator GAP-token **read** consumer of the finance read surface, reconciling the finance "v1 backend-only / user-flow-client v2-deferred" narrative with the Model B reality. **Not implemented by this task** — this task is blocked until `TASK-FIN-BE-005` is authored and merged (see Dependency Markers). This task only *consumes* the reconciled finance contract.

### Spec-first (console-side, lands before/with code, same PR — after the prerequisite merges)

- `projects/platform-console/specs/contracts/console-integration-contract.md` — add **§ 2.4.7 "finance operations surface (TASK-PC-FE-009 — cross-reference, not a redefinition)"**:
  - Authoritative producer = finance [`account-api.md`](../../../finance-platform/specs/contracts/http/account-api.md) (the read endpoints: `GET /accounts/{id}`, `/accounts/{id}/balances`, `/accounts/{id}/transactions`) — **unchanged, consumed read-only**. Record the **no-list/search-GET** constraint honestly (account-id-driven).
  - **Read-only binding**: no mutation, no `Idempotency-Key`, no `X-Operator-Reason`, no confirm dialogs (carrying the FE-007 alert-ack or the GAP § 2.4.1 mutation scaffolding here is a defect). finance write actions + the v2 `admin-service` operator surface are explicitly excluded (domain mutations / v2-deferred, not operator parity).
  - **Auth**: reuse the § 2.4.5 per-domain-credential rule — finance = **GAP OIDC access token** (`getAccessToken()`), `tenant_id ∈ {finance,*}` enforced producer-side from the JWT claim; never the § 2.6 operator-token-exchange. Reference (do not restate) § 2.4.5 (wms) + § 2.4.6 (scm) + the finance-side prerequisite (`TASK-FIN-BE-005`, finance `gap-integration.md` § *platform-console Operator Read Consumer*) that sanctions the console consumer. **Tenant model**: finance resolves the tenant from the JWT `tenant_id` claim producer-side — the console does **not** send `X-Tenant-Id` (same divergence as wms/scm).
  - **fintech producer obligations surfacing (finance domain constraint, normative — the finance analog of the scm § 2.4.6 S5 obligation)**:
    - **F5 money shape (contract obligation, not a UX nicety)**: every money value is `{ amount: "<string-integer-minor-units>", currency }` with a per-currency minor-unit scale (KRW=0, USD=2). The console **MUST** render money faithfully from the string minor-units (scale-correct display) and **MUST NOT** coerce it to a float/JS number or lose precision anywhere (parse/store/display). This is a deliberate fintech domain constraint (F5) — the money view-model field is a **required, precision-preserving** element, never a float.
    - **confidential + F7 discipline**: finance is `data_sensitivity: confidential`; producer masks PII / regulated identifiers (F7). The console **MUST NOT** log balances, transactions, account refs, or the token (reinforced no-PII/no-token logging for confidential financial data).
    - **honest regulated-state surfacing**: account status (`PENDING_KYC|ACTIVE|RESTRICTED|FROZEN|CLOSED`), KYC level, transaction status (incl. `FAILED|REVERSED`, sanction-driven), `reversalOfTransactionId` — surfaced **honestly** (a `FROZEN`/`RESTRICTED`/`CLOSED` account or a `FAILED`/`REVERSED` txn is shown as such, never hidden/de-emphasised). Unknown/future enum values degrade to a generic label, never a parser throw (same tolerant-parser discipline as scm node/PO status).
  - Resilience (§ 2.5): finance error envelope = **flat** `{ code, message, details?, timestamp }`, success `{ data, meta: { timestamp } }` (per `account-api.md` / `platform/error-handling.md` fintech) — the **same flat shape as scm but a DISTINCT producer** (the client MUST parse the finance flat shape; do not assume scm/wms parser identity). `401 UNAUTHORIZED` → forced **whole-session GAP re-login** (no partial authed state, consistent with FE-002..008); `403 TENANT_FORBIDDEN`/`FORBIDDEN` → inline "not available / not scoped"; `404 ACCOUNT_NOT_FOUND` → inline actionable (no crash); `503`/timeout → **only the finance section degrades** (shell + GAP/wms/scm sections intact). **finance has no documented `429`/rate-limit response** (`account-api.md` error table has none) — do **not** fabricate a backoff clause; this is an honest difference from the scm § 2.4.6 binding (record it, do not cargo-cult the scm 429 stanza). `IDEMPOTENCY_STORE_UNAVAILABLE` (503) is mutation-only — reads never hit it.
  - § 3 GAP-parity matrix **not** mutated (finance is additive domain scope, not a GAP `admin-web` parity row — identical to § 2.4.5/§ 2.4.6; this § 2.4.7 prose must NOT use the § 3.1 per-row attestation marker phrase, so the FE-006 no-drift guard's count stays exactly 16).
- `projects/platform-console/specs/services/console-web/architecture.md` — add the `features/finance-ops` module + `(console)/finance` route + `api/finance/**` proxy to the Layered-by-Feature map (canonical Identity table + `### Service Type Composition` H3 untouched; ADR-MONO-012 D3 form preserved).
- finance specs **unchanged by this task** (cross-reference only; the reconciliation is the separate `TASK-FIN-BE-005`). No GAP-side change.

### Code (`apps/console-web`, follows the spec)

- `src/features/finance-ops/` (Layered-by-Feature, read-only — mirrors FE-008 scm read discipline + FE-007/FE-008 non-GAP credential):
  - `api/` — server-side finance account-service client. Credential = `getAccessToken()` (GAP OIDC cookie) — never `getOperatorToken()` (asserted, reusing the § 2.4.5 rule shape). finance base URL from runtime env (`FINANCE_BASE_URL` → `http://finance.local`, registry `baseRoute`-aligned; gateway-deferred path `/api/finance/**`, transparent to the console if/when the finance gateway rewrites `/api/v1/finance/**`). Read fns: account-by-id; balances; transactions (paginated, `type`/`status` filters). AbortController hard timeout; finance **flat** error-envelope parser; **no 429 handling fabricated**.
  - `api/types.ts` — zod view-models. **Money is parsed/stored/rendered as a precision-exact string in minor units + currency** (never `number`/float); zod schema enforces the `{ amount: string, currency: string }` shape; a currency→scale map drives scale-correct display. Tolerant of unknown account/txn `status` / `type` enums (generic label, no throw).
  - `hooks/` — TanStack Query read hooks (sane staleTime; no tight refetch).
  - `components/` — read screens: account lookup (by accountId) + account detail (status/kyc/balances) + balances table + paginated transactions table (type/status filters, reversal/counterparty columns). Money rendered scale-correct from minor-units string. Regulated states surfaced honestly. WCAG AA (axe), keyboard-operable.
  - `route` — `src/app/(console)/finance/…` server component; in-console nav; registry-driven (`productKey=finance` `baseRoute`; `available:false` → existing catalog "coming soon" path).
  - `proxy` — `src/app/api/finance/**` same-origin **GET-only** proxies attaching the GAP OIDC token server-side; finance flat error-envelope mapping; no mutation routes (read-only).
- Resilience (§ 2.5): finance down (503/timeout) → only the finance section degrades; 401 → forced GAP re-login; never blank the shell.

### Tests (vitest, jsdom, mocked fetch — FE-001..008 lane)

- Auth: GAP-OIDC-token bearer on every finance call; operator-token path **absent** for finance (reuse the FE-007/FE-008 assertion shape; extend the cross-domain regression so GAP=operator-token / wms=GAP-OIDC / scm=GAP-OIDC / **finance=GAP-OIDC** all hold in one place).
- Read-only: **no** mutation artifacts anywhere (no `Idempotency-Key`, no `X-Operator-Reason`, no confirm dialogs, no finance write calls) — asserted.
- **F5 money**: money parsed/rendered from the minor-units **string** with scale-correct display; a test asserts **no float/`Number()` coercion** and precision is preserved (e.g. a large KRW amount round-trips exactly); the money field is required, not optional/discardable.
- **confidential/F7**: tokens/PII/balances/transactions/account-refs never logged (asserted).
- **honest states**: `FROZEN`/`RESTRICTED`/`CLOSED` account + `FAILED`/`REVERSED` txn rendered (not hidden); unknown enum → generic label, no throw.
- finance flat error-envelope parsing; 401/403 `TENANT_FORBIDDEN`/404 `ACCOUNT_NOT_FOUND`/503/timeout mapping; per-section degrade; **no 429 handling present** (asserted absent — finance has none).
- Regression: FE-001..008 suites green; GAP path still operator-token, wms/scm paths still GAP-OIDC-token (per-domain credential rule holds for **4** domains now); `gap`/wms/scm routes unchanged; finance nav/route resolves.

## Out of Scope

- The finance-side spec reconciliation prerequisite itself (`TASK-FIN-BE-005`, finance project-internal — this task is blocked on it, does not perform it).
- finance **write/mutation** actions (`POST /accounts`, `/kyc/upgrade`, `/holds`, `/holds/{holdId}/capture|release`, `/transfers`) — domain fund-movement / operator-domain mutations, not operator-parity; read-only section.
- finance `gateway-service` (v1-deferred) / v2 `admin-service` (reconciliation queue / KYC review / limits) / `ledger-service` / `wallet-service` / `kyc-service` / `notification-service` — all v2-deferred (ADR-MONO-008 § D3 / finance `PROJECT.md` v2 Service Map).
- A finance account list/search surface — finance has no such v1 endpoint; fabricating one is forbidden (account-id-driven only).
- erp console section (Phase 6). `console-bff` cross-domain aggregation (Phase 7).
- Any change to finance `account-api.md` or a new finance producer endpoint (cross-reference only).
- Any GAP-side change; § 3 GAP-parity matrix (finalized by FE-006) not mutated.

# Acceptance Criteria

- [ ] **Prerequisite satisfied first**: `TASK-FIN-BE-005` (finance-side spec-first reconciliation) is authored, linked here, and **merged** before this task moves `backlog → ready` and before any code (spec-first; CLAUDE.md "Specs win over tasks"). FE-007 + FE-008 also merged (per-domain pattern reuse base).
- [ ] Console renders a finance operations section server-side, tenant-scoped (`tenant_id ∈ {finance,*}`), **read-only**, authenticated with the **GAP OIDC access token** (test asserts bearer = the GAP-session cookie, never the operator token — reuses the § 2.4.5 per-domain-credential rule), consuming the existing finance account-read + balances + transactions surface. finance producer specs unchanged. The no-list/search-GET constraint is honoured (account-id-driven; no fabricated endpoint).
- [ ] **Read-only discipline**: no `Idempotency-Key`, no `X-Operator-Reason`, no confirm dialogs, no finance write calls anywhere (asserted).
- [ ] **F5 money obligation**: every money value is parsed/stored/rendered as a precision-exact minor-units **string** + currency with scale-correct display; **no float/Number coercion** anywhere (asserted by test); the money field is required, never discardable.
- [ ] **confidential/F7 + honest states**: tokens/PII/balances/transactions/account-refs never logged; `FROZEN`/`RESTRICTED`/`CLOSED` accounts and `FAILED`/`REVERSED` txns surfaced honestly; unknown enums degrade gracefully (asserted).
- [ ] **Resilience (§ 2.5)**: finance flat error envelope parsed (distinct producer, not assumed identical to scm/wms); 401 → forced GAP re-login; 403 `TENANT_FORBIDDEN`/`FORBIDDEN` → inline; 404 `ACCOUNT_NOT_FOUND` → inline actionable; 503/timeout → only the finance section degrades (shell intact); **no fabricated 429 handling** (finance has none — asserted absent).
- [ ] Tokens/PII never logged; spec-first § 2.4.7 + `console-web/architecture.md` `features/finance-ops` merged before/with code; finance/GAP specs unchanged by this task; ADR-MONO-012 D3 canonical form intact; § 3 matrix not mutated (count stays 16).
- [ ] `pnpm build` + `pnpm lint` (0) + `pnpm exec vitest run` all green (new + FE-001..008; no regression — per-domain credential rule holds across GAP/wms/scm/finance); axe WCAG AA; no bundle/perf regression beyond the FE-001 budget.
- [ ] Scope = `projects/platform-console/` only (finance cross-reference read-only); no churn-clock effect. ADR-MONO-013 Phase 5 **COMPLETE**; erp (Phase 6) inherits the proven non-GAP contract (third confirmation of § 3.3 zero-retrofit).

# Related Specs

> Target project = `platform-console`. Target service = `console-web`. Governing service-type = `platform/service-types/frontend-app.md`. Follow `platform/entrypoint.md`.

- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D1 (Model B) / § D5 / § D6 (Phase 5 — finance) / § 3.3 (the "zero retrofit" assumption — Phase 5 is its third confirmation across non-GAP domains)
- `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` (finance domain governance — unchanged; Phase 5 builds *to the proven contract*, not a finance re-decision)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.3/§ 2.4/§ 2.4.5 (wms — per-domain credential rule, reused) /§ 2.4.6 (scm — flat-envelope + read-only discipline, reused) /§ 2.5/§ 5 — this task adds § 2.4.7
- `projects/platform-console/specs/services/console-web/architecture.md` (Layered-by-Feature; FE-001..008 patterns; per-domain credential rule)
- `projects/platform-console/tasks/done/TASK-PC-FE-008-console-scm-operations-section.md` (the closest precedent — non-GAP, read-only, flat-envelope; this slice mirrors it for finance)
- `projects/platform-console/tasks/done/TASK-PC-FE-007-console-wms-operations-section.md` (the per-domain-credential rule origin)
- `projects/finance-platform/specs/contracts/http/account-api.md` (authoritative producer — read surface, F5 money shape, flat envelope; consumed unchanged)
- `projects/finance-platform/specs/integration/gap-integration.md` (finance auth + the new § *platform-console Operator Read Consumer* the prerequisite adds)
- `projects/finance-platform/PROJECT.md` (finance domain/traits — fintech F5/F7/regulated constraint context; single-org, no `frontend-app`)
- `projects/finance-platform/tasks/.../TASK-FIN-BE-005-platform-console-operator-read-consumer-reconciliation.md` (the finance-side prerequisite — gate)

# Related Skills

- `.claude/skills/` — frontend-engineer (Next.js App Router server components, TanStack Query, HttpOnly cookie auth, per-domain credential reuse, read-only tables, **precision-exact money rendering from minor-units strings**), a11y, security review (non-GAP read trust boundary; confidential financial data discipline; spec-first cross-project consumption).

---

# Related Contracts

- **Prerequisite (finance project-internal, separate task — must land first)**: finance `gap-integration.md` + `PROJECT.md` reconciliation recording `platform-console` as a sanctioned operator GAP-token read consumer (`TASK-FIN-BE-005`).
- **Changed (this task, console-side spec-first)**: `console-integration-contract.md` **new § 2.4.7** (finance read-only binding; reuses § 2.4.5/§ 2.4.6 per-domain credential rule; F5 money + confidential/F7 + honest-state surfacing obligations; no-429 honest difference) + `console-web/architecture.md` (`features/finance-ops` module).
- **Consumed (unchanged, authoritative — finance-owned)**: finance `account-api.md` (`GET /accounts/{id}` · `/balances` · `/transactions`).
- **Not touched**: GAP `admin-api.md`; § 3 GAP-parity matrix; finance `account-api.md` / `account-service/architecture.md`.

---

# Target Service

- `platform-console` / `apps/console-web` (`frontend-app`) — new read-only `features/finance-ops` module (server-side finance account-service read client, GAP-OIDC-token credential) + `(console)/finance` route + `api/finance/**` GET-only read proxy + in-console nav + registry-driven resolution.

---

# Architecture

- `console-web` follows `platform/service-types/frontend-app.md` + `console-web/architecture.md` Layered-by-Feature (FE-001..008 established). All finance calls server-side; tokens/PII/financial data never to client JS or logs.
- ADR-MONO-013 Model B: the console renders finance operational screens by calling finance's **existing** account-service read API. Third non-GAP federation; reuses the § 2.4.5 per-domain-credential rule (GAP=operator-exchange; wms/scm/finance=GAP OIDC access token).
- Read-only by domain reality (finance has no `admin-service` at v1 — no operator-mutation parity). F5 money-shape rendering + confidential/F7 + honest regulated-state surfacing are contract obligations (the finance analog of scm's S5 obligation).
- Single-domain section (not the Phase-7 `console-bff`).

---

# Implementation Notes

- **Blocked until `TASK-FIN-BE-005` merges** — do not start code (or even the console-side § 2.4.7) until the finance spec reconciliation is merged. Spec-first across projects (CLAUDE.md): consuming a producer whose own spec narrates it as backend-only/user-flow-v2-deferred without the producer acknowledging the consumer is the exact anti-pattern this gating prevents (the concrete ADR-MONO-013 § 3.3 "zero retrofit unverified" instance — Phase 5 resolves it honestly, not assumed). FE-008's Failure Scenario ("console code/contract started before the prerequisite merges → spec-first violation") applies verbatim.
- Reuse FE-007's per-domain-credential rule + FE-008's non-GAP read client / flat-envelope / read-only discipline **verbatim** (no re-derivation). The new work is: the finance read client (account-by-id + balances + paginated transactions), the **precision-exact money rendering from minor-units strings (F5)**, the confidential/F7 + honest-regulated-state surfacing, and the read UI. **No mutation scaffolding at all. No 429 stanza** (finance has none — do not cargo-cult scm's).
- Recommend implementation model: **Opus** (contract extension + cross-project spec dependency; ADR-MONO-013 § D6 Phase 5). Dispatch `Agent(subagent_type="frontend-engineer", model="opus", ...)`. Dispatcher **independently re-verifies** (no operator-token path for finance; no mutation artifacts; **no float/Number money coercion**; flat-envelope parser; no fabricated 429; § 3 marker count == 16; scope = platform-console only) before any close — agent report not trusted (the FE-007/FE-008 dispatcher discipline).
- Branch name must not contain the `master` substring. Use e.g. `task/pc-fe-009-console-finance-ops`.
- Local Docker unavailable → vitest jsdom/mocked-fetch is the local gate; Playwright/Testcontainers E2E is CI/manual.
- platform-console PR Separation Rule: spec PR (`(writing) → ready`) / impl PR (`ready → in-progress → review`, §2.4.7 + module + code + tests) / chore PR (`review → done`, may batch). The FE-007/FE-008 precedent bundled the close into a batch chore.

---

# Edge Cases

- Operator's GAP token not finance-eligible (no `tenant_id=finance` and not SUPER_ADMIN `*`) → section blocks with an actionable "no finance-scoped access" state; finance rejects cross-tenant (`403 TENANT_FORBIDDEN`) producer-side.
- Unknown/future account `status` (`RESTRICTED`/`FROZEN`/`CLOSED`/new) or txn `status`/`type` enum → render honestly with a generic label for unknowns; never a parser throw; a `FROZEN`/`CLOSED` account is shown as such, not hidden.
- A very large minor-units amount (e.g. KRW with scale 0, billions) → rendered exactly from the string; **must not** lose precision via float/Number (the F5 core risk).
- `accountId` not found → `404 ACCOUNT_NOT_FOUND` → inline actionable "no such account" (no crash, no re-login loop).
- finance section 503/timeout → only the finance section degrades; GAP/wms/scm sections + the console shell stay intact.
- 401 (GAP session expired) on a finance call → whole-session forced GAP re-login (no partial authed state), consistent with FE-002..008.
- Registry marks `finance` `available:false` → the data-driven catalog "coming soon" path handles it; finance route/nav must not hard-crash.
- An operator attempts a finance write via the section → impossible by construction (GET-only proxy, no mutation fn, no write UI) — asserted.

# Failure Scenarios

- finance code/contract started before `TASK-FIN-BE-005` merges → spec-first violation; the Dependency Marker + AC gate `backlog → ready` on the linked prerequisite.
- finance client uses `getOperatorToken()` instead of `getAccessToken()` → wrong credential + misapplied GAP-domain auth; test asserts GAP-OIDC-token bearer and absence of the operator-token path for finance (reuses FE-007/FE-008).
- Money coerced to a float/JS `number` (parse, store, arithmetic, or display) → **F5 violation** + precision loss on financial data; test asserts string-exact round-trip and no `Number()`/float coercion.
- Mutation scaffolding (idempotency/reason/confirm) or a finance write call sneaks into the read-only section → defect; test asserts none present.
- A `429`/backoff stanza is cargo-culted from the scm § 2.4.6 binding → wrong; finance has no documented rate-limit response. Spec/AC pin "no fabricated 429"; test asserts no 429 handling path.
- finance flat error envelope mis-parsed as wms's nested `{ error: { code } }` (or assumed scm-identical without its own parser) → mis-rendered errors; test asserts the finance flat-shape parser (per-domain envelope correctness).
- A finance account list/search endpoint is fabricated because the section "needs" one → forbidden; finance has no such v1 producer endpoint; account-id-driven only.
- A finance section failure blanks the whole console shell → violates § 2.5; test asserts finance-only degrade.
- § 3 GAP-parity matrix mutated for finance, or the § 3.1 attestation marker phrase used in § 2.4.7 (drifts FE-006's count off 16) → wrong (additive domain scope, not a parity row); AC forbids it.

---

# Test Requirements

- vitest (jsdom, mocked fetch): auth (GAP-OIDC-token bearer; operator-token path absent for finance), read-only (no mutation artifacts / no finance write), **F5 money** (minor-units string round-trip exact, no float/Number coercion, scale-correct display, required field), confidential/F7 (no token/PII/balance/txn/ref logging), honest states (FROZEN/RESTRICTED/CLOSED/FAILED/REVERSED rendered; unknown enum → generic label, no throw), finance flat error-envelope + 401/403 `TENANT_FORBIDDEN`/404 `ACCOUNT_NOT_FOUND`/503/timeout mapping + per-section degrade + **no-429-path** asserted, components/hooks (account lookup/detail, balances table, paginated transactions w/ type+status filters + reversal/counterparty columns, degraded/permission placeholders), regression (FE-001..008 green; per-domain credential rule holds GAP/wms/scm/finance; gap/wms/scm routes unchanged; finance nav/route resolves).
- `pnpm build` + `pnpm lint` (0) green; axe (WCAG AA); no bundle/perf regression beyond the FE-001 budget.
- Spec internal-link lint clean; `validate-rules` no new inconsistency; ADR-MONO-012 D3 canonical form intact; § 3 attestation-marker count == 16 (FE-006 no-drift guard unaffected).

---

# Definition of Done

- [ ] `TASK-FIN-BE-005` finance-side spec-first prerequisite authored, linked, **merged** (gate for `backlog → ready`); FE-007 + FE-008 merged (per-domain pattern base)
- [ ] Console-side spec-first reconciliation (`console-integration-contract.md` § 2.4.7 + `console-web/architecture.md` `features/finance-ops`) merged before/with code
- [ ] finance operations section rendered server-side, tenant-scoped, **read-only**, GAP-OIDC-token-authed; account-id-driven (no fabricated list); F5 money precision-exact; confidential/F7 + honest regulated-states
- [ ] § 2.5 resilience (finance flat envelope, per-section degrade, 401 whole-session re-login, no fabricated 429) implemented + tested
- [ ] `pnpm build`/`lint`/`vitest` green + axe AA; scope = platform-console only; no regression; § 3 count == 16
- [ ] Acceptance Criteria all satisfied; ADR-MONO-013 Phase 5 **COMPLETE**; erp (Phase 6) inherits the proven non-GAP contract
- [ ] Ready for review
