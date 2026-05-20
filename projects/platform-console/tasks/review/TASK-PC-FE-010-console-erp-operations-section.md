# Task ID

TASK-PC-FE-010

# Title

console-web Phase 6 — erp operations console section (read-only: 5 masters × {list, detail} + asOf point-in-time)

# Status

review

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

- **depends on**: **ADR-MONO-013 ACCEPTED** (TASK-MONO-108) § D6 **Phase 6** (erp console section, "governed by future erp ADR" — that ADR is **ADR-MONO-016** ACCEPTED 2026-05-19, **not re-decided here**); `TASK-PC-FE-007` (wms) + `TASK-PC-FE-008` (scm) + `TASK-PC-FE-009` (finance) — establish the **non-GAP per-domain client + per-domain-credential** pattern this slice **reuses** (do not re-derive it). All **MERGED** → origin/main (FE-007 #633 `81395376`; FE-008 #637 `c34fc0ac`; FE-009 #644 `29b01826`). The `console-integration-contract.md` **§ 2.4.5** (FE-007 wms — defines the per-domain credential rule) + **§ 2.4.6** (FE-008 scm — proves it generalises, flat-envelope + read-only discipline) + **§ 2.4.7** (FE-009 finance — third confirmation, flat-envelope + read-only + producer-domain obligations cross-ref + no-fabricated-429) are on main; **§ 2.4.8** (this task) **reuses** them for erp (do not redefine).
- **BLOCKED ON cross-project spec-first prerequisite (this is why the task starts in `backlog/`)**: the erp-side reconciliation = **`TASK-ERP-BE-002`** (`erp-platform`, *platform-console operator read-consumer spec reconciliation*). It records `platform-console` (ADR-MONO-013 Model B) as a **sanctioned external operator read consumer** of erp's existing read surface — a **(B) document/accept** of the *already-existing* erp JWT chain (GAP RS256 + JWKS + issuer + `tenant_id ∈ {erp,*}` + `X-Token-Type=user`) under the governing ADR-MONO-013 (no erp ADR; erp domain governance stays ADR-MONO-016; the "internal-only 경계" #6 / E7 narrative is **clarified, not weakened** — boundary scopes non-GAP-SSO traffic, GAP-authenticated console routed through internal Traefik is within SSO boundary). **Authored + merged**: spec #655 `09d4cb2a` + impl #656 `083c744b` + close #657 `4e626fdc` (all 2026-05-20). This task may move `backlog → ready` because ERP-BE-002 **and** FE-007/FE-008/FE-009 are all merged (spec-first; CLAUDE.md "Specs win over tasks" — mirrors the GAP `TASK-BE-296` ⊃ `FE-001`, scm `TASK-SCM-BE-015` ⊃ `FE-008`, finance `TASK-FIN-BE-005` ⊃ `FE-009` gating).
- **part of**: ADR-MONO-013 § D6 **Phase 6** — the erp console section, the **first internal-system-primary** non-GAP federation (FE-007/008/009 were transactional/integration-heavy). Phase 6 completes the non-GAP federation across **four** domains (wms/scm/finance/erp) — the § 3.3 "zero retrofit" assumption's fourth confirmation. Phase 7 (`console-bff` + cross-domain dashboards) is now at 5/5 domains live (GAP + wms + scm + finance + erp) once this slice lands — Phase 7 gate ungated.
- **spec-first**: the console-side contract extension (**new § 2.4.8 erp binding** in `console-integration-contract.md` + `console-web/architecture.md` `features/erp-ops` module) lands before/with the code, **after** the erp-side prerequisite spec change merges (already merged 2026-05-20). erp `masterdata-api.md` + `masterdata-service/architecture.md` are **unchanged** (cross-reference only — erp owns them).
- **contract-extension → Opus**: ADR-MONO-013 § D6 Phase 6 = "Opus/Sonnet"; this slice **extends the contract** (§ 2.4.8) and depends on a cross-project spec reconciliation → **Opus**. Dispatch `Agent(subagent_type="frontend-engineer", model="opus", ...)` per the FE-007/FE-008/FE-009 pattern; dispatcher independently re-verifies before any close.

# Goal

Build the console's **erp operations section** — ADR-MONO-013 Phase 6. It is a server-side, tenant-scoped, **read-only** section over erp's existing `masterdata-service` v1-live read surface — 5 masters × {list, detail} = **10 GET endpoints**, all supporting `?asOf=<ISO-8601>` point-in-time read (architecture.md E3, effective-period `[from, to)` half-open semantics):

- **departments** — `GET /api/erp/masterdata/departments` (list) + `/{id}` (detail; includes `parentId`, tree position)
- **employees** — `GET /api/erp/masterdata/employees` (list) + `/{id}` (detail; employment status, organisational attributes, departmentId/jobGradeId/costCenterId references)
- **job-grades** — `GET /api/erp/masterdata/job-grades` (list, ordered by `displayOrder` asc) + `/{id}` (detail)
- **cost-centers** — `GET /api/erp/masterdata/cost-centers` (list) + `/{id}` (detail)
- **business-partners** — `GET /api/erp/masterdata/business-partners` (list) + `/{id}` (detail; partner type, contact info)

erp v1 has **no `admin-service`** (deferred to erp v2 — `PROJECT.md` § v1 OUT / ADR-MONO-016 § D3). There is therefore **no operator-mutation parity** for erp at v1; the section is **strictly read-only**, closest to the FE-008 scm and FE-009 finance precedents. The erp **write/mutation** surface (16 endpoints — 5×`POST` create / 5×`PATCH` / 5×`POST /retire` / 1×`POST .../move-parent`) is operator-domain mutation requiring `Idempotency-Key` (F1) + role-scoped authorization (E6 fail-CLOSED) + append-only audit (E8) — **not** an operator-parity console surface at v1 (alongside v2 `approval-service`/`read-model-service`/future `admin-service`, ADR-MONO-016 § D3); explicitly out of scope.

**Honest erp read-surface constraint (recorded, not papered over) — DIFFERENT from finance**: erp v1 exposes **both list and detail** GETs for every master (10 endpoints), **AND** supports `?asOf=<past>` point-in-time read on all of them. This is the **inverse** of the FE-009 finance situation (finance had `GET /accounts/{id}` only, account-id-driven; erp has full list+detail with effective-dating). The honest erp section is therefore **list-driven** (browsable index for each master, drillable into detail), **with explicit effective-dating** (an operator can supply `?asOf=<ISO-8601>` to view historical state — first-class UI surface for the E3 invariant). Do **not** force-fit the finance account-id-driven shape; the erp UX is browsable-with-effective-dating.

Auth follows the FE-007/FE-008/FE-009 non-GAP pattern: erp masterdata-service = GAP RS256 JWT (ADR-001), `tenant_id ∈ {erp,*}` from the JWT claim — the **GAP OIDC access token** (`getAccessToken()`), **not** the GAP operator-token-exchange (§ 2.6). This **reuses** the § 2.4.5 per-domain-credential rule (no re-derivation), with the **same outcome as wms, scm, and finance** — the fourth domain confirms the rule generalises across transactional, integration-heavy, regulated, and now internal-system-primary domain shapes.

# Scope

## In Scope

### Cross-project prerequisite (must land first — erp project-internal, separate task)

- An erp-side spec-first change recording `platform-console` as a sanctioned operator GAP-token **read** consumer of the erp read surface, clarifying the erp "internal-only 경계" (#6 / E7) narrative as in-SSO-boundary inclusion (not external bypass). **Not implemented by this task** — already merged via `TASK-ERP-BE-002` (see Dependency Markers): spec #655 `09d4cb2a` + impl #656 `083c744b` + close #657 `4e626fdc` (all 2026-05-20). This task only *consumes* the reconciled erp contract.

### Spec-first (console-side, lands before/with code, same PR)

- `projects/platform-console/specs/contracts/console-integration-contract.md` — add **§ 2.4.8 "erp operations surface (TASK-PC-FE-010 — cross-reference, not a redefinition)"**:
  - Authoritative producer = erp [`masterdata-api.md`](../../../erp-platform/specs/contracts/http/masterdata-api.md) (the 10 read endpoints — 5 masters × {list, detail}, all with `?asOf=` query) — **unchanged, consumed read-only**. Record the **list-driven + effective-dating-first-class** UX honestly (NOT the finance account-id-driven shape).
  - **Read-only binding**: no mutation, no `Idempotency-Key`, no `X-Operator-Reason`, no confirm dialogs (carrying the FE-007 alert-ack or the GAP § 2.4.1 mutation scaffolding here is a defect). erp write actions (16 mutation endpoints) + v2 `approval-service`/`read-model-service`/future `admin-service` are explicitly excluded.
  - **Auth**: reuse the § 2.4.5 per-domain-credential rule — erp = **GAP OIDC access token** (`getAccessToken()`), `tenant_id ∈ {erp,*}` enforced producer-side from the JWT claim; never the § 2.6 operator-token-exchange. Reference (do not restate) § 2.4.5 (wms) + § 2.4.6 (scm) + § 2.4.7 (finance) + the erp-side prerequisite (`TASK-ERP-BE-002`, erp `gap-integration.md` § *platform-console Operator Read Consumer*) that sanctions the console consumer. **Tenant model**: erp resolves the tenant from the JWT `tenant_id` claim producer-side — the console does **not** send `X-Tenant-Id` (same divergence as wms/scm/finance).
  - **erp internal-system producer obligations surfacing (erp domain constraint, normative — the erp analog of scm § 2.4.6 S5 / finance § 2.4.7 F5/F7)**:
    - **E2 effective-dating + E3 point-in-time (UX-first-class, not buried)**: every master detail surfaces `effectivePeriod: { effectiveFrom, effectiveTo }` honestly — `effectiveTo: null` (open-ended / active) and `effectiveTo: <past>` (retired) rendered **visually distinct** (retired rows clearly de-emphasised but **not hidden**). The console **MUST** expose the `?asOf=<ISO-8601>` query as a first-class user-controllable input (date picker or URL param), and the rendered list/detail **MUST** correctly reflect the state-at-that-instant (the E3 invariant). Substituting "current state" for `?asOf=<past>` is the core erp UX defect to avoid.
    - **E1 reference integrity surfacing**: when the console renders a master detail referencing other masters (e.g. employee → department/jobGrade/costCenter; cost-center → parent; business-partner → reference targets), broken/retired references are surfaced **honestly** (a "retired reference" badge or similar, not silently sanitized). When the producer rejects a mutation due to E1 (which the console doesn't issue, but might surface as a historic audit reason), the producer message is rendered faithfully.
    - **confidential + audit-heavy discipline**: erp is `data_sensitivity: confidential`; producer enforces it. The console **MUST NOT** log employee PII (names / contact info), business-partner financial identifiers, cost-center sensitive attributes, or the token (reinforced no-PII/no-token logging for confidential internal master data). The architecture E8 (append-only `audit_log`) is the producer's authority on change history; the console renders that history (when surfaced) faithfully, never doctored.
    - **honest enum / status surfacing**: master `status` enums (`ACTIVE`/`RETIRED` and any future addition) + employee employment status (`EMPLOYED`/`ON_LEAVE`/`SEPARATED` per architecture.md) — surfaced **honestly** (a `RETIRED` master is shown as such, never hidden). Unknown/future enum values degrade to a generic label, never a parser throw (same tolerant-parser discipline as wms/scm/finance).
  - Resilience (§ 2.5): erp error envelope = **flat** `{ code, message, details?, timestamp }`, success `{ data, meta: { timestamp, page?, size?, totalElements? } }` (per `masterdata-api.md` / `platform/error-handling.md` erp section) — the **same flat shape as scm and finance, but a DISTINCT producer** (the client MUST parse the erp flat shape; do not assume scm/finance parser identity). `401 UNAUTHORIZED` → forced **whole-session GAP re-login** (no partial authed state, consistent with FE-002..009); `403 TENANT_FORBIDDEN`/`FORBIDDEN`/`DATA_SCOPE_FORBIDDEN`/`EXTERNAL_TRAFFIC_REJECTED` → inline "not available / not scoped"; `404 MASTERDATA_NOT_FOUND` → inline actionable (no crash); `409 MASTERDATA_DUPLICATE_KEY` / `MASTERDATA_REFERENCE_VIOLATION` / `MASTERDATA_PARENT_CYCLE` + `422 MASTERDATA_EFFECTIVE_PERIOD_INVALID` are **mutation-only** (reads never hit them — surface them only if/when surfacing producer audit history); `503`/timeout → **only the erp section degrades** (shell + GAP/wms/scm/finance sections intact). **erp has no documented `429`/rate-limit response** (`masterdata-api.md` documents no 429) — do **not** fabricate a backoff clause; this is an honest difference from the scm § 2.4.6 binding, identical to finance § 2.4.7 (record it, do not cargo-cult).
  - § 3 GAP-parity matrix **not** mutated (erp is additive domain scope, not a GAP `admin-web` parity row — identical to § 2.4.5/§ 2.4.6/§ 2.4.7; this § 2.4.8 prose must NOT use the § 3.1 per-row attestation marker phrase, so the FE-006 no-drift guard's count stays exactly 16).
- `projects/platform-console/specs/services/console-web/architecture.md` — add the `features/erp-ops` module + `(console)/erp` route + `api/erp/**` proxy to the Layered-by-Feature map (canonical Identity table + `### Service Type Composition` H3 untouched; ADR-MONO-012 D3 form preserved).
- erp specs **unchanged by this task** (cross-reference only; the reconciliation is the already-merged `TASK-ERP-BE-002`). No GAP-side change.

### Code (`apps/console-web`, follows the spec)

- `src/features/erp-ops/` (Layered-by-Feature, read-only — mirrors FE-008 scm / FE-009 finance read discipline + per-domain-credential rule):
  - `api/` — server-side erp masterdata-service client. Credential = `getAccessToken()` (GAP OIDC cookie) — never `getOperatorToken()` (asserted, reusing the § 2.4.5 rule shape). erp base URL from runtime env (`ERP_BASE_URL` → `http://erp.local`, registry `baseRoute`-aligned; gateway-pending path `/api/erp/**`). Read fns: 10 reads — for each of 5 masters, `list*(page?, size?, asOf?)` + `get*ById(id, asOf?)`. AbortController hard timeout; erp **flat** error-envelope parser; **no 429 handling fabricated** (asserted absent).
  - `api/types.ts` — zod view-models for all 5 masters: `Department` (with `parentId` for hierarchy), `Employee` (with `departmentId`/`jobGradeId`/`costCenterId` refs + employment status enum), `JobGrade` (with `displayOrder`), `CostCenter`, `BusinessPartner`. **`EffectivePeriod` is a required, first-class field on every master** (`{ effectiveFrom: string ISO date, effectiveTo: string | null }`). `Audit` (createdAt/By/updatedAt/By). Tolerant of unknown `status` enums (generic label, no throw).
  - `hooks/` — TanStack Query read hooks (sane staleTime; no tight refetch). `useAsOf()` — shared hook that reads the `asOf` URL parameter / date picker state and threads it into every list/detail query (the E3 first-class UX surface).
  - `components/` — read screens, one per master (5 list views, 5 detail views) + a shared `<AsOfPicker>` control above each list/detail that controls the `?asOf=` URL parameter. List views are paginated tables with `effectivePeriod` columns; rows where `effectiveTo` is `<past>` (retired in current view) or where `effectiveFrom > now` (future) are visually de-emphasised but rendered (not hidden). Detail views show full master + cross-references (employee → department/jobGrade/costCenter resolved by parallel ID lookups; broken/retired references surfaced honestly with a badge). WCAG AA (axe), keyboard-operable, `<AsOfPicker>` keyboard accessible.
  - `route` — `src/app/(console)/erp/…` server component; in-console nav; registry-driven (`productKey=erp` `baseRoute`; `available:false` → existing catalog "coming soon" path). Sub-routes per master.
  - `proxy` — `src/app/api/erp/**` same-origin **GET-only** proxies attaching the GAP OIDC token server-side; erp flat error-envelope mapping; pass-through of the `asOf` query parameter; no mutation routes (read-only).
- Resilience (§ 2.5): erp down (503/timeout) → only the erp section degrades; 401 → forced GAP re-login; never blank the shell.

### Tests (vitest, jsdom, mocked fetch — FE-001..009 lane)

- Auth: GAP-OIDC-token bearer on every erp call; operator-token path **absent** for erp (reuse the FE-007/FE-008/FE-009 assertion shape; extend the cross-domain regression so GAP=operator-token / wms=GAP-OIDC / scm=GAP-OIDC / finance=GAP-OIDC / **erp=GAP-OIDC** all hold in one place — 5 domains).
- Read-only: **no** mutation artifacts anywhere (no `Idempotency-Key`, no `X-Operator-Reason`, no confirm dialogs, no erp write calls) — asserted.
- **E3 asOf first-class**: a test feeds `?asOf=<past-instant>` and asserts the erp client passes it through to the producer; a separate test asserts the rendered state matches the `asOf`-instant response (not the current-state response). The `<AsOfPicker>` test asserts URL parameter binding (date picker change → URL update → query refetch).
- **E2 effective-period rendering**: tests for both `effectiveTo: null` (active) and `effectiveTo: <past>` (retired) rendering — both shown, retired visually distinct. A test asserts a retired row is NOT hidden / NOT filtered out at the consumer; honest surfacing.
- **E1 reference integrity surfacing**: a test renders an employee whose `departmentId` resolves to a retired department; the retired-reference badge is asserted present (not silently sanitized).
- **confidential**: tokens/PII (employee names / contact) / business-partner financial details / cost-center sensitive attrs never logged (asserted; console spy).
- **honest enum surfacing**: `RETIRED` master + `SEPARATED` employee status rendered (not hidden); unknown enum → generic label, no throw.
- erp flat error-envelope parsing (distinct producer parser — own test, NOT assumed scm/finance-identical); 401/403 (`TENANT_FORBIDDEN`/`FORBIDDEN`/`DATA_SCOPE_FORBIDDEN`/`EXTERNAL_TRAFFIC_REJECTED`) / 404 `MASTERDATA_NOT_FOUND` / 503 / timeout mapping; per-section degrade; **no 429 handling present** (asserted absent — erp has none, identical to finance).
- Regression: FE-001..009 suites green; GAP path still operator-token, wms/scm/finance paths still GAP-OIDC-token (per-domain credential rule holds for **5** domains now); `gap`/wms/scm/finance routes unchanged; erp nav/routes (5 master sub-routes) resolve.

## Out of Scope

- The erp-side spec reconciliation prerequisite itself (`TASK-ERP-BE-002`, erp project-internal — this task is blocked on it, does not perform it; already merged 2026-05-20).
- erp **write/mutation** actions (16 endpoints — 5×create / 5×patch / 5×retire / 1×move-parent) — operator-domain mutations, not operator-parity; read-only section.
- erp `gateway-service` architecture spec (declared v1-IN in `PROJECT.md` but not yet authored under `specs/services/gateway-service/`) — the console consumes the v1-live `masterdata-service` reads directly; gateway-rewrite is transparent if/when it lands.
- v2 `approval-service` / `read-model-service` / future `admin-service` / `notification-service` / `permission-service` — all v2-deferred (ADR-MONO-016 § D3 / erp `PROJECT.md` § v1 OUT).
- `console-bff` cross-domain aggregation (Phase 7 — separate ADR/PROPOSED + task).
- Any change to erp `masterdata-api.md` or a new erp producer endpoint (cross-reference only).
- Any GAP-side change; § 3 GAP-parity matrix (finalized by FE-006) not mutated.

# Acceptance Criteria

- [ ] **Prerequisite satisfied**: `TASK-ERP-BE-002` (erp-side spec-first reconciliation) is authored, linked here, and **merged** (already done 2026-05-20: spec #655 / impl #656 / close #657); FE-007/FE-008/FE-009 also merged (per-domain pattern reuse base).
- [ ] Console renders an erp operations section server-side, tenant-scoped (`tenant_id ∈ {erp,*}`), **read-only**, authenticated with the **GAP OIDC access token** (test asserts bearer = the GAP-session cookie, never the operator token — reuses the § 2.4.5 per-domain-credential rule), consuming the existing erp 10 GET reads (5 masters × {list, detail}). erp producer specs unchanged. List-driven UX with `<AsOfPicker>` as first-class E3 surface (not finance account-id-driven shape).
- [ ] **Read-only discipline**: no `Idempotency-Key`, no `X-Operator-Reason`, no confirm dialogs, no erp write calls anywhere (asserted).
- [ ] **E2/E3 effective-dating obligation**: every master detail surfaces `effectivePeriod`; `effectiveTo: null` (active) vs `effectiveTo: <past>` (retired) rendered visually distinct (asserted; retired NOT hidden); `?asOf=<past>` correctly threads through every list/detail query and the rendered state matches the asOf-instant response (asserted by test).
- [ ] **E1 reference integrity surfacing**: broken/retired cross-references surfaced honestly (badge, not silent sanitize; asserted).
- [ ] **confidential discipline + honest enum surfacing**: tokens/PII/business-partner financials/cost-center sensitive attrs never logged; `RETIRED` masters + `SEPARATED` employees surfaced honestly; unknown enums degrade gracefully (asserted).
- [ ] **Resilience (§ 2.5)**: erp flat error envelope parsed (distinct producer parser, not assumed scm/finance-identical); 401 → forced GAP re-login; 403 erp codes (`TENANT_FORBIDDEN`/`FORBIDDEN`/`DATA_SCOPE_FORBIDDEN`/`EXTERNAL_TRAFFIC_REJECTED`) → inline; 404 `MASTERDATA_NOT_FOUND` → inline actionable; 503/timeout → only the erp section degrades (shell intact); **no fabricated 429 handling** (erp has none — asserted absent).
- [ ] Tokens/PII never logged; spec-first § 2.4.8 + `console-web/architecture.md` `features/erp-ops` merged before/with code; erp/GAP specs unchanged by this task; ADR-MONO-012 D3 canonical form intact; § 3 matrix not mutated (count stays 16).
- [ ] `pnpm build` + `pnpm lint` (0) + `pnpm exec vitest run` all green (new + FE-001..009; no regression — per-domain credential rule holds across GAP/wms/scm/finance/erp = 5 domains); axe WCAG AA; no bundle/perf regression beyond the FE-001 budget.
- [ ] Scope = `projects/platform-console/` only (erp cross-reference read-only); no churn-clock effect. ADR-MONO-013 Phase 6 **COMPLETE**; Phase 7 (`console-bff` + cross-domain dashboards) gate ungated (5/5 domains live).

# Related Specs

> Target project = `platform-console`. Target service = `console-web`. Governing service-type = `platform/service-types/frontend-app.md`. Follow `platform/entrypoint.md`.

- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D1 (Model B) / § D5 / § D6 (Phase 6 — erp) / § 3.3 (the "zero retrofit" assumption — Phase 6 is its fourth confirmation across non-GAP domains and the FIRST internal-system-primary confirmation)
- `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` (erp domain governance — unchanged; Phase 6 builds *to the proven contract*, not an erp re-decision; § D3.1 platform-console parity-slice as binding UI decision is the relevant authority)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.3/§ 2.4/§ 2.4.5 (wms — per-domain credential rule, reused) /§ 2.4.6 (scm — flat-envelope + read-only discipline, reused) /§ 2.4.7 (finance — flat-envelope + read-only + no-fabricated-429, reused) /§ 2.5/§ 5 — this task adds § 2.4.8
- `projects/platform-console/specs/services/console-web/architecture.md` (Layered-by-Feature; FE-001..009 patterns; per-domain credential rule)
- `projects/platform-console/tasks/done/TASK-PC-FE-009-console-finance-operations-section.md` (the closest precedent — non-GAP, read-only, flat-envelope, no-fabricated-429; this slice mirrors it for erp but with list-driven + E2/E3 effective-dating-first-class UX)
- `projects/platform-console/tasks/done/TASK-PC-FE-008-console-scm-operations-section.md` (read-only + flat-envelope precedent)
- `projects/platform-console/tasks/done/TASK-PC-FE-007-console-wms-operations-section.md` (the per-domain-credential rule origin)
- `projects/erp-platform/specs/contracts/http/masterdata-api.md` (authoritative producer — 10 read endpoints, `?asOf=` query, flat envelope, erp Standard Error Codes; consumed unchanged)
- `projects/erp-platform/specs/integration/gap-integration.md` (erp auth + the § *platform-console Operator Read Consumer* section added by ERP-BE-002)
- `projects/erp-platform/PROJECT.md` (erp domain/traits — internal-system / transactional / audit-heavy + confidential + single-org; no `frontend-app`)
- `projects/erp-platform/specs/services/masterdata-service/architecture.md` (read-only context — E1/E2/E3/E8 invariants, `tenant_id ∈ {erp,*}` enforcement, JWT chain)
- `projects/erp-platform/tasks/done/TASK-ERP-BE-002-platform-console-operator-read-consumer-reconciliation.md` (the erp-side prerequisite — merged 2026-05-20)

# Related Skills

- `.claude/skills/` — frontend-engineer (Next.js App Router server components, TanStack Query, HttpOnly cookie auth, per-domain credential reuse, read-only tables, **effective-dating-first-class UI with `?asOf=` thread-through**), a11y, security review (non-GAP read trust boundary; confidential internal master data discipline; spec-first cross-project consumption).

---

# Related Contracts

- **Prerequisite (erp project-internal, separate task — already merged)**: erp `gap-integration.md` + `PROJECT.md` reconciliation (`TASK-ERP-BE-002` #655/#656/#657).
- **Changed (this task, console-side spec-first)**: `console-integration-contract.md` **new § 2.4.8** (erp read-only binding; reuses § 2.4.5/§ 2.4.6/§ 2.4.7 per-domain credential rule + flat-envelope + read-only + no-fabricated-429 discipline; E1/E2/E3/E8 + confidential cross-ref; list-driven + asOf-first-class UX honestly recorded) + `console-web/architecture.md` (`features/erp-ops` module).
- **Consumed (unchanged, authoritative — erp-owned)**: erp `masterdata-api.md` (10 GET reads at `/api/erp/masterdata/*` with `?asOf=` query).
- **Not touched**: GAP `admin-api.md`; § 3 GAP-parity matrix; erp `masterdata-api.md` / `masterdata-service/architecture.md`; ADR-MONO-016.

---

# Target Service

- `platform-console` / `apps/console-web` (`frontend-app`) — new read-only `features/erp-ops` module (server-side erp masterdata-service read client, GAP-OIDC-token credential) + `(console)/erp` route (5 master sub-routes) + `api/erp/**` GET-only read proxy + in-console nav + registry-driven resolution + `<AsOfPicker>` shared component (E3 first-class).

---

# Architecture

- `console-web` follows `platform/service-types/frontend-app.md` + `console-web/architecture.md` Layered-by-Feature (FE-001..009 established). All erp calls server-side; tokens/PII/financial data never to client JS or logs.
- ADR-MONO-013 Model B: the console renders erp operational screens by calling erp's **existing** masterdata-service read API. Fourth non-GAP federation; reuses the § 2.4.5 per-domain-credential rule (GAP=operator-exchange; wms/scm/finance/erp=GAP OIDC access token).
- Read-only by domain reality (erp has no `admin-service` at v1 — no operator-mutation parity). E2/E3 effective-dating + E1 reference integrity + confidential + audit-heavy surfacing are contract obligations (the erp analog of scm's S5 / finance's F5/F7).
- First **internal-system-primary** non-GAP federation — proves the § 3.3 zero-retrofit holds across a fourth trait shape; Phase 7 (`console-bff` + cross-domain dashboards) gate is ungated once this lands (5/5 domains live).
- Single-domain section (not the Phase-7 `console-bff`).

---

# Implementation Notes

- **Prerequisite already merged** — `TASK-ERP-BE-002` 3-PR sequence is on origin/main (verified squash hashes: #655 `09d4cb2a` / #656 `083c744b` / #657 `4e626fdc`, all 2026-05-20). This task may begin its `backlog → ready` promote immediately on its own gate. FE-008's Failure Scenario ("console code/contract started before the prerequisite merges → spec-first violation") is non-applicable here because the prerequisite is already on main.
- Reuse FE-007's per-domain-credential rule + FE-008's flat-envelope read discipline + FE-009's no-fabricated-429 honesty **verbatim** (no re-derivation). The new work is: the erp read client (10 reads, list+detail × 5), the **`<AsOfPicker>` shared E3 component + `useAsOf()` hook thread-through**, the E2 effective-period rendering distinction (active vs retired, both shown), the E1 reference integrity surfacing, the confidential + audit-heavy discipline, and the list-driven read UI. **No mutation scaffolding at all. No 429 stanza** (erp has none — do not cargo-cult scm's).
- Recommend implementation model: **Opus** (contract extension + cross-project spec dependency; ADR-MONO-013 § D6 Phase 6). Dispatch `Agent(subagent_type="frontend-engineer", model="opus", ...)`. Dispatcher **independently re-verifies** (no operator-token path for erp; no mutation artifacts; **no fabricated 429**; `?asOf=` thread-through to every query; flat-envelope parser; § 3 marker count == 16; scope = platform-console only) before any close — agent report not trusted (the FE-007/FE-008/FE-009 dispatcher discipline).
- Branch name must not contain the `master` substring. Use e.g. `task/pc-fe-010-console-erp-ops`.
- Local Docker unavailable → vitest jsdom/mocked-fetch is the local gate; Playwright/Testcontainers E2E is CI/manual.
- platform-console PR Separation Rule: spec PR (`(writing) → backlog`) / promote chore (`backlog → ready` after prereq met) / impl PR (`ready → in-progress → review`, §2.4.8 + module + code + tests) / chore PR (`review → done`, may batch). The FE-007/FE-008/FE-009 precedents used 4-PR shape (FE-007/FE-008 bundled close into batch chore; FE-009 had a dedicated close).

---

# Edge Cases

- Operator's GAP token not erp-eligible (no `tenant_id=erp` and not SUPER_ADMIN `*`) → section blocks with an actionable "no erp-scoped access" state; erp rejects cross-tenant (`403 TENANT_FORBIDDEN`) producer-side.
- Unknown/future master `status` (e.g. a future `SUSPENDED` enum) or employee employment status → render honestly with a generic label for unknowns; never a parser throw; a `RETIRED` master / `SEPARATED` employee is shown as such, not hidden.
- `?asOf=<future-instant>` queried by the operator → producer behaviour governs (architecture.md E3); the console renders whatever the producer returns honestly (does not substitute current state).
- `?asOf=<past>` returning a state where a referenced master was active at that time but is retired now → the console renders the as-of-that-time state honestly (not "patch with current" — that would violate E3 and is the core defect to avoid).
- `masterdataId` not found → `404 MASTERDATA_NOT_FOUND` → inline actionable "no such record" (no crash, no re-login loop).
- erp section 503/timeout → only the erp section degrades; GAP/wms/scm/finance sections + the console shell stay intact.
- 401 (GAP session expired) on an erp call → whole-session forced GAP re-login (no partial authed state), consistent with FE-002..009.
- Registry marks `erp` `available:false` → the data-driven catalog "coming soon" path handles it; erp route/nav must not hard-crash.
- An operator attempts an erp write via the section → impossible by construction (GET-only proxy, no mutation fn, no write UI) — asserted.
- A retired master referenced by an active employee → the employee detail surfaces a "retired reference" badge on the department/jobGrade/costCenter field, not silently sanitized.

# Failure Scenarios

- erp code/contract started before `TASK-ERP-BE-002` merges → spec-first violation; here the prerequisite is already merged, so this risk is closed (recorded for completeness).
- erp client uses `getOperatorToken()` instead of `getAccessToken()` → wrong credential + misapplied GAP-domain auth; test asserts GAP-OIDC-token bearer and absence of the operator-token path for erp (reuses FE-007/FE-008/FE-009).
- Mutation scaffolding (idempotency/reason/confirm) or an erp write call sneaks into the read-only section → defect; test asserts none present.
- A `429`/backoff stanza is cargo-culted from the scm § 2.4.6 binding → wrong; erp has no documented rate-limit response (identical to finance § 2.4.7). Spec/AC pin "no fabricated 429"; test asserts no 429 handling path.
- erp flat error envelope mis-parsed as wms's nested `{ error: { code } }` (or assumed scm/finance-identical without its own parser) → mis-rendered errors; test asserts the erp flat-shape parser (per-domain envelope correctness — each domain owns its own parser even if the shape is identical).
- The `?asOf=` query parameter is silently dropped (not threaded through to the producer) → **core E3 UX defect**; test asserts the producer client passes `asOf` through verbatim and the rendered state matches the asOf-instant response (not current state).
- A retired master is silently filtered/hidden in the consumer → E2 honesty violation; test asserts retired rows are rendered (visually distinct, but not hidden).
- A broken cross-reference (e.g. employee → retired department) is silently sanitized → E1 honesty violation; test asserts the retired-reference badge surfaces.
- An erp section failure blanks the whole console shell → violates § 2.5; test asserts erp-only degrade.
- § 3 GAP-parity matrix mutated for erp, or the § 3.1 attestation marker phrase used in § 2.4.8 (drifts FE-006's count off 16) → wrong (additive domain scope, not a parity row); AC forbids it.

---

# Test Requirements

- vitest (jsdom, mocked fetch): auth (GAP-OIDC-token bearer; operator-token path absent for erp), read-only (no mutation artifacts / no erp write), **E2 effective-period rendering** (active vs retired both shown, retired visually distinct, retired NOT hidden), **E3 `?asOf=` thread-through** (URL param → query refetch; rendered state matches asOf-instant response, not current state), **E1 reference integrity surfacing** (retired-reference badge present), **confidential** (tokens/PII/business-partner financials/cost-center sensitive attrs never logged), honest enum surfacing (RETIRED/SEPARATED rendered; unknown enum → generic label, no throw), erp flat error-envelope + 401/403 (`TENANT_FORBIDDEN`/`FORBIDDEN`/`DATA_SCOPE_FORBIDDEN`/`EXTERNAL_TRAFFIC_REJECTED`) / 404 `MASTERDATA_NOT_FOUND` / 503 / timeout mapping + per-section degrade + **no-429-path** asserted, components/hooks (5 list views, 5 detail views, `<AsOfPicker>` shared, paginated tables, cross-reference resolution), regression (FE-001..009 green; per-domain credential rule holds GAP/wms/scm/finance/erp = 5 domains; gap/wms/scm/finance routes unchanged; erp nav/5 sub-routes resolve).
- `pnpm build` + `pnpm lint` (0) green; axe (WCAG AA); no bundle/perf regression beyond the FE-001 budget.
- Spec internal-link lint clean; ADR-MONO-012 D3 canonical form intact; § 3 attestation-marker count == 16 (FE-006 no-drift guard unaffected).

---

# Definition of Done

- [ ] `TASK-ERP-BE-002` erp-side spec-first prerequisite merged (gate for `backlog → ready` — satisfied 2026-05-20: #655/#656/#657); FE-007 + FE-008 + FE-009 merged (per-domain pattern base — also satisfied)
- [ ] Console-side spec-first reconciliation (`console-integration-contract.md` § 2.4.8 + `console-web/architecture.md` `features/erp-ops`) merged before/with code
- [ ] erp operations section rendered server-side, tenant-scoped, **read-only**, GAP-OIDC-token-authed; list-driven (5 master list views + 5 detail views) with `<AsOfPicker>` as first-class E3 surface; E2 effective-period rendering distinct (active vs retired); E1 reference integrity surfacing; confidential + audit-heavy discipline
- [ ] § 2.5 resilience (erp flat envelope, per-section degrade, 401 whole-session re-login, no fabricated 429) implemented + tested
- [ ] `pnpm build`/`lint`/`vitest` green + axe AA; scope = platform-console only; no regression; § 3 count == 16
- [ ] Acceptance Criteria all satisfied; ADR-MONO-013 Phase 6 **COMPLETE**; Phase 7 (`console-bff`) gate ungated
- [ ] Ready for review
