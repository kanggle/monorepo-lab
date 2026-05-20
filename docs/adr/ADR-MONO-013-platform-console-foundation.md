# ADR-MONO-013 — platform-console Foundation: Single-UI Console Model, New-Project Placement, admin-web Parity-Gated Retirement, Cross-Project Integration Contract

**Status:** ACCEPTED
**Date:** 2026-05-16
**History:** PROPOSED 2026-05-16 (TASK-MONO-107 — foundation criteria pre-authored from a converged design dialogue; PR #565). ACCEPTED 2026-05-16 (TASK-MONO-108 — user-explicit intent "ADR-013 ACCEPTED"; D7.1–D7.6 evaluated PASS; Phase 1 bootstrap authorised). AMENDED-BY [ADR-MONO-014](ADR-MONO-014-platform-console-operator-auth-token-exchange.md) 2026-05-16 (PROPOSED TASK-MONO-109 #570 → ACCEPTED TASK-MONO-110 — the § D5 deferred operator-auth bridge is decided there: GAP OIDC ↔ admin-service operator-token exchange, RFC 8693; **ADR-MONO-014 ACCEPTED**, Phase 2 now PAUSED only until the GAP exchange endpoint `TASK-BE-298` is merged). AMENDED-BY [ADR-MONO-015](ADR-MONO-015-platform-console-dashboards-model.md) 2026-05-16 (PROPOSED TASK-MONO-111 #578 → ACCEPTED TASK-MONO-112 — the § 3 / D7.4 parity-checklist **`dashboards`** line has no GAP producer endpoint and `admin-web`'s is a Grafana iframe; ADR-MONO-015 refines it to a **composed operator overview** (Model B, not Grafana), additively — no ADR-013 decision change; **ADR-MONO-015 ACCEPTED**, the Phase 3 admin-web-retirement gate uses the refined checklist; FE-005 unblocked, FE-006 gated on FE-005 merge).
**Decision driver:** A unified AWS/GCP-console-style operations surface over the portfolio's enterprise suite (gap · scm · wms + future erp · finance) was requested. The design dialogue converged on a single model (B — console *is* the UI), a placement (new `projects/platform-console/`), and a retirement path (GAP `admin-web` parity-gated removal). Per ADR-MONO-003a § D2.1, adding any new project under `projects/<name>/` resets the shared-library churn clock and shifts portfolio narrative scope — both decision points that require a fresh ADR. This is that fresh ADR for `platform-console`.
**Supersedes:** none.
**Related:** [ADR-MONO-003a](ADR-MONO-003a-d4-override-scope-canonicalization.md) § D2.1 (new project bootstrap requires fresh ADR — this ADR satisfies that for `platform-console`), [ADR-MONO-003b](ADR-MONO-003b-phase-5-launch-criteria.md) § 1.4 + § D3 (Template ↔ monorepo sync; new-project churn-clock context), [ADR-MONO-008](ADR-MONO-008-finance-platform-bootstrap.md) (finance bootstrap — governs this ADR's Phase 5, NOT re-decided here), [ADR-MONO-002](ADR-MONO-002-phase-4-template-extraction-trigger.md) § D4 (project ordering parent), [global-account-platform PROJECT.md](../../projects/global-account-platform/PROJECT.md) (GAP service map — `admin-web` retirement is a GAP spec change), [docs/project-overview.md](../project-overview.md) § 2.6 (finance/erp 미생성 roadmap), [TEMPLATE.md](../../TEMPLATE.md) § Local Network Convention (`console.local` hostname), [`rules/taxonomy.md`](../../rules/taxonomy.md) (D3 domain/trait choices), memory [`project_portfolio_7axis_architecture`](../../../memory/project_portfolio_7axis_architecture.md), memory [`project_gap_idp_promotion`](../../../memory/project_gap_idp_promotion.md) (GAP = standard OIDC AS, the SSO backbone this console depends on).

---

## 1. Context

### 1.1 What was asked

An AWS/GCP-console-style single screen to operate the portfolio's enterprise suite: **gap, scm, erp, wms, finance**. Of these, `gap`/`wms`/`scm` exist; `erp`/`finance` are 미생성 (per project-overview § 2.6, post-Phase-5).

### 1.2 The design dialogue and what it converged

A multi-turn design dialogue resolved four decisions that this ADR records:

1. **Integration model.** `wms`/`scm` are **backend-only** (no frontend app); `erp`/`finance` do not exist. A pure SSO launcher (portal → redirect to each product's own UI) has nothing to launch into for backend-only domains. The dialogue converged on **Model B — the console *is* the single UI**, rendering each domain's operational screens by calling that domain's gateway/admin REST APIs. This matches the AWS-console anatomy (one console app over many service APIs).
2. **Placement.** Because the console federates multiple `projects/<name>/` domains and GAP returns to backend-only, the console is intrinsically a cross-project artifact. Hosting it inside GAP would exceed GAP's declared scope (it would reach into wms/scm/erp/finance APIs). Converged on a **new project `projects/platform-console/`**.
3. **GAP `admin-web` retirement.** The user stated `admin-web` will be removed once the console is complete. The console therefore absorbs GAP's operator surface. Retirement is **parity-gated**: the console must reach functional parity with `admin-web`'s operator features (lock/unlock, force-logout, audit query) **and that parity must be verified** before `admin-web` is removed — otherwise live operator capability is lost.
4. **finance/erp domain governance is JIT.** `finance`/`erp` taxonomy + `rules/domains/<d>.md` are authored *just-in-time* at each domain's bootstrap (Phase 5/6), NOT pre-authored at Phase 0 — consistent with `rules/README.md` on-demand policy ("do not auto-generate stubs") and to avoid an early, idle churn-clock reset. finance bootstrap remains governed by **ADR-MONO-008**; erp by a future ADR. This ADR does **not** re-decide them.

### 1.3 Why an ADR

Per ADR-MONO-003a § D2.1, adding a new project skeleton under `projects/<name>/` is not an OVERRIDE-class change: it resets the `libs/`/`settings.gradle` churn clock and shifts portfolio narrative scope. `platform-console` is a new project → it requires a fresh ADR. This is that ADR (structurally analogous to ADR-MONO-008 for finance).

### 1.4 Why PROPOSED, not ACCEPTED

PROPOSED ≠ "we will build it eventually". PROPOSED = "the model/placement/retirement-path/contract are decided; the moment the console project is actually bootstrapped is the ACCEPTED transition" — the same staged pattern as ADR-MONO-003b and ADR-MONO-008. The design decisions (D1–D4) are converged from the dialogue; execution gating (D7/D8) defers the skeleton-creation churn to an explicit ACCEPTED moment.

### 1.5 Scope: what "platform-console bootstrap" means

The ACCEPTED transition produces, in sequence:

1. `projects/platform-console/` with `PROJECT.md` (domain + traits + service_types per D3), a `frontend-app` skeleton, first task in `tasks/ready/`.
2. GAP-side: console registered as an OIDC client + a product/tenant registry surface the console catalog reads (GAP project-internal task).
3. Monorepo `settings.gradle` / root `package.json` / Traefik `console.local` registration.

Subsequent phases (D6) deliver the operator-parity MVP, the `admin-web` retirement, per-domain sections, and the BFF aggregation tier.

---

## 2. Decision

### D1 — Integration model

| Option | Mechanics | Fit for this codebase | Verdict |
|---|---|---|---|
| **A. SSO launcher portal** | Portal → SSO → redirect to each product's own frontend | Requires a frontend per product; wms/scm have none, erp/finance don't exist → only gap `admin-web` is a real target (and that is being retired). Degenerate. | Rejected |
| **B. Console *is* the UI** | One console frontend renders every domain's screens by calling that domain's gateway/admin REST API; SSO via GAP OIDC | Matches backend-only reality of wms/scm and the AWS-console anatomy. Single frontend. erp/finance slot in as data-driven catalog entries. | **CHOSEN** |
| **C. Micro-frontend shell** | Shell composes each product UI via Module Federation/iframe | No per-product UI exists to compose; highest maintenance for lowest marginal value here | Rejected |
| **Hybrid** | Launcher for products with their own UI, console-render for backend-only | Collapses to B once `admin-web` (the only product UI) is retired | Rejected (collapses to B) |

**Decision: Model B.** The console is the sole frontend for the enterprise suite. Domains stay backend-only; the console federates them through a backend-for-frontend (BFF) integration contract (D5).

### D2 — Placement

| Option | Verdict |
|---|---|
| New `projects/platform-console/` | **CHOSEN** — cross-project artifact; GAP returns to pure backend IdP; clean boundary |
| Inside GAP (`apps/portal-web`) | Rejected — federating wms/scm/erp/finance APIs exceeds GAP's declared scope; GAP hosts no frontend after `admin-web` retirement |
| Monorepo-level `platform/` | Rejected — `platform/` is project-agnostic rules, not a running app |

**Decision: new project `projects/platform-console/`.** Consequence: GAP's PROJECT.md service map loses `admin-web` (D4); GAP becomes backend-only (IdP + service APIs).

### D3 — Project classification (FINALISED at ACCEPTED 2026-05-16)

Per `rules/taxonomy.md` (verified: `saas` L179, `integration-heavy` L303, `multi-tenant` L313, `audit-heavy` L318; `frontend-app` service-type proven in production by GAP `admin-web`):

- **Domain:** `saas` — horizontal, non-industry internal platform surface (same rationale GAP used for `saas`: an internal product-family shared layer, not an industry vertical).
- **Traits:** `multi-tenant` (tenant/context switcher is core), `integration-heavy` (fans out to N gateway/admin APIs with CB/retry/timeout per `platform/` baselines), `audit-heavy` (absorbed GAP operator actions — lock/unlock/force-logout — must be traceable). `internal-system` considered but **NOT declared**: the console is operator-facing, but its hard constraints are fully captured by the three above; declaring it adds no distinct rule layer (`rules/README.md` on-demand minimalism).
- **service_types:** `frontend-app` (the console UI) at v1; `rest-api` added when the console-bff (Phase 7) lands.

`PROJECT.md` frontmatter at bootstrap (PR-B): `domain: saas` / `traits: [multi-tenant, integration-heavy, audit-heavy]` / `service_types: [frontend-app]`.

### D4 — GAP `admin-web` parity-gated retirement

Retirement is **not a delete**; it is a deprecation gated on verified parity.

1. The console must reach functional parity with `admin-web`'s operator surface: account lock/unlock, force-logout, audit query (and any other `admin-web` capability enumerated at Phase 2).
2. Parity must be **verified** (Phase 2 acceptance) before any removal.
3. Removal is a **GAP spec change first**: GAP `PROJECT.md` service map drops the `admin-web` row and records console absorption; GAP specs referencing `admin-web` updated; then the app is removed. Handled as a GAP project-internal task (spec-first).

Sequencing is enforced by D6 (Phase 2 → gate → Phase 3).

### D5 — Cross-project integration (BFF) contract skeleton

Every domain federated by the console must satisfy a contract. This ADR fixes the **skeleton**; the full contract is its own spec deliverable (Phase 0, root task / spec — see D6).

> **AMENDED-BY [ADR-MONO-014](ADR-MONO-014-platform-console-operator-auth-token-exchange.md):** the operator-authentication bridge (how the console's GAP OIDC `platform-console-web` token reaches `/api/admin/**` operator endpoints, which require `token_type=admin`/`iss=admin-service`) was deferred here and is decided by ADR-MONO-014 = **RFC 8693-style token exchange**. **ADR-MONO-014 is ACCEPTED** (TASK-MONO-110, 2026-05-16); ADR-MONO-013 Phase 2–3 stays PAUSED only until the GAP exchange endpoint (`TASK-BE-298`) is merged.

| Contract element | Requirement |
|---|---|
| **Identity** | Console is a registered GAP OIDC client; user authenticates once via GAP Authorization Code + PKCE; access token carries `tenant_id` |
| **Tenant/product registry** | GAP exposes a registry surface the console catalog reads → catalog is **data-driven** (erp/finance appear as `available:false` until bootstrapped, flipped on with config only — zero console rework) |
| **Routing** | Each domain reachable at its Traefik hostname (`wms.local`, `scm.local`, …); console reaches domains server-side via gateway/admin APIs |
| **Console-facing API surface** | Each domain's gateway/admin service exposes the read/ops endpoints the console renders; calls are tenant-scoped; isolation enforced (cross-tenant denied) |
| **Resilience** | Console/BFF fan-out applies circuit-breaker / retry / timeout per `platform/` baselines; one domain down ≠ console down |

### D6 — Phased roadmap (decided sequence + dependency gates)

| Phase | Deliverable | Location | Gate | Model |
|---|---|---|---|---|
| 0 | This ADR + full BFF integration-contract spec | root `tasks/` + `docs/adr/` + spec | — | Opus |
| 1 | `platform-console` skeleton + GAP OIDC client + tenant/product registry surface | root `tasks/` (new project) + GAP `tasks/` | ADR-013 ACCEPTED | Opus (classification/tenant), Sonnet (skeleton) |
| 2 | Console B-MVP, first surface = **GAP operator parity** | `platform-console` `tasks/` | Phase 1 | Sonnet; contract change → Opus |
| 3 | `admin-web` retirement (GAP PROJECT.md spec change → app removal) | GAP `tasks/` (spec-first) | **Phase 2 parity verified** | Opus |
| 4 | wms / scm console sections | `platform-console` `tasks/` | Phase 2 (proven contract) | Sonnet; contract ext → Opus |
| 5 | finance-platform built **to the proven contract** (governed by ADR-MONO-008) | per ADR-MONO-008 | Phase 4 | Opus/Sonnet |
| 6 | erp-platform built to the proven contract (future erp ADR) | future erp ADR | Phase 5 | Opus/Sonnet |
| 7 | console-bff fan-out + cross-domain dashboards | `platform-console` `tasks/` | 5 domains live | Opus (composition), Sonnet (shell) |
| 8 | Federation hardening — cross-product e2e, observability, multi-tenant isolation regression | root `tasks/` | all domains integrated | Sonnet; isolation → Opus |

**Dependency invariants:** ADR-013 ACCEPTED gates all phases · Phase 2 parity-verified gates Phase 3 retirement · the contract (Phase 0) is validated by the MVP (Phase 2) before finance/erp (5/6) are built to it → eliminates retrofit · BFF aggregation (7) requires all 5 domains. finance/erp taxonomy/rules are JIT at Phase 5/6, governed by ADR-MONO-008 / future erp ADR — **not this ADR**.

### D7 — Readiness criteria (evaluated before ACCEPTED)

| # | Criterion |
|---|---|
| D7.1 | Model B + new-project placement re-confirmed against current monorepo state |
| D7.2 | D3 classification finalised (domain + trait stack + service_types) against `rules/taxonomy.md` |
| D7.3 | Phase 0 BFF integration-contract spec authored (or scoped as the ACCEPTED first artifact) |
| D7.4 | GAP `admin-web` operator-surface capabilities enumerated (parity checklist for Phase 2) |
| D7.5 | User-explicit bootstrap intent recorded (D8.1) |
| D7.6 | Shared-library churn-clock reset acknowledged (ADR-MONO-003a § D2.1 consequence — same as ADR-MONO-008 § 3.3) |

### D8 — ACCEPTED transition mechanics

**D8.1 — User-explicit intent forms.** Any of: "ADR-013 ACCEPTED", "platform-console 부트스트랩 시작", "콘솔 프로젝트 만들어" with affirmative direction. Ambiguous statements ("콘솔은 언제?") do not satisfy.

**D8.2 — Commit pattern.** Typically 2 PRs: **PR-A** doc-only (this ADR PROPOSED → ACCEPTED + § 6 row + memory update); **PR-B** bootstrap artifact (`projects/platform-console/` skeleton + GAP OIDC client/registry + `settings.gradle`/`package.json`/`console.local`).

**D8.3 — Audit-trail row (§ 6 format).**
```
| YYYY-MM-DD | ACCEPTED | <classification> | <Phase reached> | <user-intent-quote> | <PR-A # / PR-B #> |
```

---

## 3. Consequences

### 3.1 PROPOSED merge (this PR)
- ADR-MONO-003a § D2.1's mandate satisfied — `platform-console` now has its required fresh ADR (PROPOSED).
- No project created. No churn-clock reset yet. Monorepo state unchanged.
- A future "should we build the console now?" question has a concrete checklist (§ D7) + sequence (§ D6).

### 3.2 ACCEPTED moment (future)
- D7.1–D7.6 evaluated; D8.1 intent confirmed; D8.2 commit pattern followed.
- `projects/platform-console/` created; GAP gains OIDC client + registry surface.
- Shared-library churn clock reset (settings.gradle), per ADR-MONO-003a § D2.1 — same expected consequence as ADR-MONO-008 § 3.3.

### 3.3 Post-bootstrap
- Monorepo project count 5 → 6 (`platform-console`). GAP becomes backend-only once Phase 3 completes.
- GAP PROJECT.md service map mutated (`admin-web` removed) — a recorded GAP spec change, parity-gated.
- finance/erp, when bootstrapped (ADR-MONO-008 / future erp ADR), inherit the proven console contract → zero console retrofit.

### 3.4 Future-self / future-LLM-session
- A session evaluating "build the console now?" reads § D7 + § D6.
- A session prompted to "build the console" reads § D8.1 intent forms + § D6 sequence before mutating anything.
- finance/erp taxonomy/rules are explicitly out of this ADR — a session must not pre-author them here (§ 1.2 / § D6).

---

## 4. Alternatives Considered

- **4.1 SSO launcher-only (D1 Option A).** Rejected — degenerate for backend-only wms/scm and a being-retired `admin-web`.
- **4.2 Hybrid launcher+console.** Rejected — collapses to B once `admin-web` retires.
- **4.3 Micro-frontend shell (D1 Option C).** Rejected — no per-product UI to compose; worst ROI here.
- **4.4 Console inside GAP.** Rejected — exceeds GAP scope; GAP hosts no frontend post-retirement.
- **4.5 Build finance/erp before the console.** Rejected — two full domain projects (months) gating one frontend; collides with the recorded roadmap order + ADR-MONO-003 Phase-5 freeze; a data-driven catalog makes erp/finance zero-rework later anyway. The contract-first/MVP-validates sequence (§ D6) captures the benefit without the cost.
- **4.6 Pre-author finance/erp taxonomy at Phase 0.** Rejected — speculative rules months before use; violates `rules/README.md` on-demand policy; resets churn clock early and idle. JIT at Phase 5/6.
- **4.7 ACCEPTED now (skip PROPOSED).** Rejected — identical reasoning to ADR-MONO-008 § 4.5: the design is decided but project-creation churn waits for an explicit bootstrap moment.

## 5. Relationship to ADR-MONO-008 / ADR-MONO-003a

| Aspect | ADR-MONO-003a | ADR-MONO-008 | ADR-MONO-013 (this) |
|---|---|---|---|
| Scope | Meta: new project bootstrap needs fresh ADR | finance bootstrap | platform-console bootstrap + integration contract + admin-web retirement |
| Status | ACCEPTED | PROPOSED | PROPOSED |
| Churn-clock | Defines the rule | Resets on finance ACCEPTED | Resets on console ACCEPTED |
| Governs finance/erp? | n/a | **Yes (finance)** | **No** — explicitly defers to ADR-MONO-008 / future erp ADR |

## 6. Status Transition History

Append-only.

| Date | Transition | Classification | Phase reached | User intent quote | PR(s) |
|---|---|---|---|---|---|
| 2026-05-16 | created PROPOSED | n/a (D3 deferred) | Phase 0 (criteria pre-author) | n/a (design dialogue convergence) | #565 |
| 2026-05-16 | PROPOSED → ACCEPTED | `saas` + [`multi-tenant`,`integration-heavy`,`audit-heavy`] + [`frontend-app`] | Phase 0 → Phase 1 (bootstrap authorised) | "ADR-013 ACCEPTED" | PR-A (this) / PR-B (bootstrap artifact) |

D7.1–D7.6 at ACCEPTED: D7.1 Model B + new-project re-confirmed · D7.2 D3 finalised vs `rules/taxonomy.md` (enums verified) · D7.3 BFF integration-contract spec scoped as PR-B first artifact (TASK-MONO-108 § Scope) · D7.4 GAP `admin-web` operator surface enumerated (accounts lock/unlock/bulk-lock/revoke-session/gdpr-delete/export · audit · dashboards · operators create/roles/status/password · security login-history/suspicious) → Phase 2 parity checklist · D7.5 intent "ADR-013 ACCEPTED" · D7.6 churn-clock reset acknowledged (ADR-MONO-003a § D2.1; reset occurs at PR-B `settings.gradle` change, not this doc PR).

> **Additive note — Phase 2 parity verified by TASK-PC-FE-006 (PR (this PR)).** The D7.4 parity checklist (the `dashboards` line refined by ADR-MONO-015 D2 = composed operator overview, *not* Grafana) was finalised into a **verified parity matrix** in [`console-integration-contract.md` § 3](../../projects/platform-console/specs/contracts/console-integration-contract.md) and attested programmatically (16/16 rows, `apps/console-web/tests/unit/parity-verification.test.ts`). ADR-MONO-013 Phase 2 = **COMPLETE (5/5 slices)**; the § D6 Phase 3 `admin-web`-retirement gate (row 3, "Phase 2 parity verified") is **satisfied**. This note is **additive only** — no ADR-MONO-013 decision (D1–D8) is changed (ADR-013 ACCEPTED; HARDSTOP-04 discipline). The retirement itself remains a separate GAP project-internal spec-first task (GAP `PROJECT.md` service map), out of scope of FE-006.

> **Additive note — Phase 4 COMPLETE (FE-007 wms + FE-008 scm) — backfilled by TASK-MONO-123 (PR (this PR)).** The § D6 Phase 4 deliverable (*"wms / scm console sections"*) was landed in two slices, both merged on 2026-05-19: **slice 1** = [`TASK-PC-FE-007`](../../projects/platform-console/tasks/done/TASK-PC-FE-007-console-wms-operations-section.md) (PR #633 squash `81395376`) — first non-GAP federation; established [`console-integration-contract.md` § 2.4.5](../../projects/platform-console/specs/contracts/console-integration-contract.md) as the **normative per-domain credential selection rule** (GAP = RFC 8693 exchanged operator token § 2.6 / #569 *GAP-domain-scoped* ↔ wms = GAP OIDC access token direct, RS256/ADR-001, `tenant_id=wms` claim; no operator-exchange); wms nested error envelope; §3 GAP-parity matrix byte-unchanged (16/16). **slice 2** = [`TASK-PC-FE-008`](../../projects/platform-console/tasks/done/TASK-PC-FE-008-console-scm-operations-section.md) (PR #637 squash `c34fc0ac`) — second non-GAP federation, completes Phase 4; § 2.4.6 **reuses** § 2.4.5's per-domain credential rule verbatim, scm flat error envelope (distinct from wms nested), 429 bounded backoff, strictly read-only (scm v1 has no `admin-service`, no operator-mutation parity), S5 `meta.warning` REQUIRED-surfaced; §3 = 16 unchanged. Cross-project prereq for slice 2 = [`TASK-SCM-BE-015`](../../projects/scm-platform/tasks/done/TASK-SCM-BE-015-platform-console-operator-read-consumer-reconciliation.md) (#635/#636, scm `gateway-public-routes.md` `## platform-console operator read consumer (ADR-MONO-013 Model B)` section), merged before FE-008 impl. ADR-MONO-013 Phase 4 = **COMPLETE (2/2 slices)**; the § D6 § 3.3 "zero retrofit" assumption is **verified for the first time** across two non-GAP domains (the contract proven in FE-007 generalised verbatim to scm — zero re-derivation). Phase 5 (finance) is unblocked. This note is **additive only** — no ADR-MONO-013 decision (D1–D8) is changed (ADR-013 ACCEPTED; HARDSTOP-04 discipline). The retrospective backfill itself is governed by [`TASK-MONO-123`](../../tasks/done/) (not by FE-007/FE-008, which each scoped their § 6 update out of their original deliverable).

> **Additive note — Phase 5 COMPLETE (FE-009 finance + FIN-BE-005 reconciliation) — backfilled by TASK-MONO-123 (PR (this PR)).** The § D6 Phase 5 deliverable (*"finance-platform built to the proven contract (governed by ADR-MONO-008)"*) was landed across **two project-internal task chains** on 2026-05-20. **finance-side spec-first reconciliation** = [`TASK-FIN-BE-005`](../../projects/finance-platform/tasks/done/TASK-FIN-BE-005-platform-console-operator-read-consumer-reconciliation.md) (spec PR #639 squash `95c543a1` + impl PR #640 `8b5d60aa` + close chore #641 `297948bd`) — additive `(B) document/accept` of finance's existing GAP-RS256 JWT chain (`AllowedIssuersValidator` + `TenantClaimValidator` `tenant_id ∈ {finance,*}` + `X-Token-Type=user`) recording `platform-console` as a sanctioned external operator read consumer of finance's v1-live read surface; finance `gap-integration.md` `## platform-console Operator Read Consumer (ADR-MONO-013)` section + `PROJECT.md` clarifying bullet; **no finance ADR** (finance domain governance stays ADR-MONO-008, untouched); finance has no `gateway-public-routes.md` (gateway v1-deferred) so the honest finance shape is the SCM-BE-015 *subset* (2 files, not 3); `account-api.md` / `account-service/architecture.md` authoritative + byte-unchanged. **console-side section** = [`TASK-PC-FE-009`](../../projects/platform-console/tasks/done/TASK-PC-FE-009-console-finance-operations-section.md) (4-PR sequence per platform-console lifecycle: spec #642 `c49edce1` → promote #643 `456a6bde` → impl #644 `29b01826` → close #645 `59ab228e`) — third non-GAP federation; [`console-integration-contract.md` § 2.4.7](../../projects/platform-console/specs/contracts/console-integration-contract.md) **reuses** the § 2.4.5 (wms) per-domain credential rule + § 2.4.6 (scm) flat-envelope/read-only discipline **verbatim** (finance = GAP OIDC access token direct via `getAccessToken()`, never `getOperatorToken()`; `tenant_id ∈ {finance,*}` from JWT claim, no `X-Tenant-Id`); strictly read-only (finance v1 has no `admin-service`, v2-deferred per ADR-MONO-008 § D3 — no operator-mutation parity, closest to FE-008 scm); finance read surface `GET /accounts/{id}` · `/balances` · `/transactions` (no list/search GET — account-id-driven, honest constraint); finance flat error envelope (distinct producer, own parser, NOT wms nested); **no 429** (finance has none — honest difference from scm § 2.4.6, not cargo-culted); finance write surface + v2 `admin-service` excluded. **fintech producer obligations (finance analog of scm S5)**: F5 money = precision-exact minor-units **string** with `formatMoney` scale-correct rendering (no float/`Number`/`parseFloat`/`parseInt` on `amount` anywhere — on-disk source grep-asserted by `finance-api.test.ts`) + confidential/F7 (no token/PII/balance/txn/account-ref logging — console spy asserted) + honest regulated-state surfacing (FROZEN/RESTRICTED/CLOSED accounts + FAILED/REVERSED txns rendered; unknown enums → generic label, no throw). §3 parity matrix NOT mutated (attestation-marker count = exactly 16; FE-006 no-drift guard unaffected — `finance-no-drift.test.ts` pins this). ADR-MONO-013 Phase 5 = **COMPLETE**; the § D6 § 3.3 "zero retrofit" assumption is **verified for the third time** (the contract written in § 2.4.5 generalised verbatim to § 2.4.6 and again to § 2.4.7 — three non-GAP domains, zero re-derivation, zero retrofit). Phase 6 (erp console section, governed by future erp ADR per [ADR-MONO-016](ADR-MONO-016-erp-platform-bootstrap.md) ACCEPTED 2026-05-19) inherits the proven non-GAP contract. Phase 7 (`console-bff` + cross-domain dashboards) is at **4/5** domains live (GAP + wms + scm + finance; erp pending the future Phase 6). This note is **additive only** — no ADR-MONO-013 decision (D1–D8) is changed (ADR-013 ACCEPTED; HARDSTOP-04 discipline). finance domain governance stays ADR-MONO-008 (not re-decided here, exactly as § D6 Phase 5 states). The retrospective backfill itself is governed by [`TASK-MONO-123`](../../tasks/done/) (not by FE-009/FIN-BE-005, which each scoped their § 6 update out of their original deliverable).

> **Additive note — Phase 6 COMPLETE (FE-010 erp + ERP-BE-002 reconciliation) — backfilled by TASK-PC-FE-010 (PR (this PR)).** The § D6 Phase 6 deliverable (*"erp console section (governed by future erp ADR)"* — that ADR is [`ADR-MONO-016`](ADR-MONO-016-erp-platform-bootstrap.md) ACCEPTED 2026-05-19, **not** re-decided here) was landed across **two project-internal task chains** on 2026-05-20. **erp-side spec-first reconciliation** = [`TASK-ERP-BE-002`](../../projects/erp-platform/tasks/done/TASK-ERP-BE-002-platform-console-operator-read-consumer-reconciliation.md) (spec PR #655 squash `09d4cb2a` + impl PR #656 `083c744b` + close chore #657 `4e626fdc`) — additive `(B) document/accept` of erp's existing GAP-RS256 JWT chain (GAP JWKS + issuer + `tenant_id ∈ {erp,*}` + `X-Token-Type=user`) recording `platform-console` as a sanctioned **external operator read consumer** of erp's v1-live read surface; erp `gap-integration.md` `## platform-console Operator Read Consumer (ADR-MONO-013)` section clarifies the erp "internal-only 경계" (#6 / E7) narrative as in-SSO-boundary inclusion (GAP-authenticated console traffic routed through internal Traefik is within the SSO boundary — boundary scopes *non*-GAP-SSO traffic; clarified, **not weakened**); **no erp ADR** for the console consumer (erp domain governance stays ADR-MONO-016, untouched); erp `masterdata-api.md` / `masterdata-service/architecture.md` authoritative + byte-unchanged. **console-side section** = [`TASK-PC-FE-010`](../../projects/platform-console/tasks/done/TASK-PC-FE-010-console-erp-operations-section.md) — fourth non-GAP federation and the **first internal-system-primary** non-GAP confirmation (FE-007 transactional, FE-008 integration-heavy, FE-009 regulated/transactional, FE-010 **internal-system + transactional + audit-heavy**); [`console-integration-contract.md` § 2.4.8](../../projects/platform-console/specs/contracts/console-integration-contract.md) **reuses** the § 2.4.5 (wms) per-domain credential rule + § 2.4.6 (scm) flat-envelope/read-only discipline + § 2.4.7 (finance) no-fabricated-429 honesty **verbatim** (erp = GAP OIDC access token direct via `getAccessToken()`, never `getOperatorToken()`; `tenant_id ∈ {erp,*}` from JWT claim, no `X-Tenant-Id`); strictly read-only (erp v1 has no `admin-service` — v2-deferred per ADR-MONO-016 § D3; erp's 16-endpoint mutation surface — 5×create / 5×patch / 5×retire / 1×move-parent — is operator-domain mutation requiring `Idempotency-Key` + role-scoped E6 + append-only E8 audit, NOT operator-parity, excluded); erp read surface = **10 GET endpoints** (5 masters × {list, detail}: departments / employees / job-grades / cost-centers / business-partners) — **list-driven with `?asOf=<ISO-8601>` first-class** (E3 point-in-time read), the INVERSE of the FE-009 finance account-id-driven shape; erp flat error envelope (same wire shape as scm/finance, distinct producer, own parser, NOT wms nested); **no 429** (erp has none — identical to finance § 2.4.7, asserted absent — not cargo-culted from scm § 2.4.6). **erp internal-system producer obligations (erp analog of scm S5 / finance F5/F7)**: E2/E3 effective-dating with `<AsOfPicker>` URL-bound first-class component + `effectivePeriod` rendered on every master (active `effectiveTo: null` vs retired `effectiveTo: <past>` both shown, retired visually distinct but **not** hidden — asserted) + E1 reference integrity surfacing (broken/retired cross-references rendered with a `<RetiredReferenceBadge>`, NOT silently sanitized — asserted) + confidential discipline (no token/PII/business-partner-financial/cost-center-sensitive logging — console spy asserted) + honest enum surfacing (`RETIRED` master + `SEPARATED` employee rendered; unknown enums → generic label, no throw). §3 parity matrix NOT mutated (attestation-marker count = exactly 16; FE-006 no-drift guard unaffected — `erp-no-drift.test.ts` pins this). Cross-domain regression extended to **5 domains** (GAP=operator-token / wms=GAP-OIDC / scm=GAP-OIDC / finance=GAP-OIDC / **erp=GAP-OIDC**) — the per-domain credential rule holds across all five. ADR-MONO-013 Phase 6 = **COMPLETE**; the § D6 § 3.3 "zero retrofit" assumption is **verified for the fourth time** and the **first time across an internal-system-primary domain trait shape** (the contract written in § 2.4.5 generalised verbatim to § 2.4.6, § 2.4.7, and now § 2.4.8 — four non-GAP domains, zero re-derivation, zero retrofit, across transactional / integration-heavy / regulated / internal-system-primary trait shapes). Phase 7 (`console-bff` + cross-domain dashboards) gate is **ungated to 5/5 domains live** (GAP + wms + scm + finance + erp). This note is **additive only** — no ADR-MONO-013 decision (D1–D8) is changed (ADR-013 ACCEPTED; HARDSTOP-04 discipline). erp domain governance stays ADR-MONO-016 (not re-decided here, exactly as § D6 Phase 6 states — that ADR is ACCEPTED 2026-05-19, so the "future erp ADR" forward reference resolves to it now).

## 7. Provenance

- ADR-MONO-003a § D2.1 — explicit mandate for a fresh ADR on new project bootstrap; this ADR satisfies it for `platform-console`.
- ADR-MONO-008 — structural template (PROPOSED criteria-doc pattern) and the governing ADR for this ADR's Phase 5 (finance); not re-decided here.
- GAP PROJECT.md § Service Map — `admin-web` is the only existing product UI; its parity-gated retirement is a GAP spec change.
- Memory `project_gap_idp_promotion` — GAP as standard OIDC AS is the SSO backbone Model B depends on.
- Design dialogue 2026-05-15~16 — converged D1 (Model B) / D2 (new project) / D4 (parity-gated retirement) / § 1.2.4 (finance/erp JIT).

분석=Opus 4.7 / 구현 권장: ADR·통합계약 spec = Opus (model/placement/retirement-path/contract phrasing require interpretive judgement; structurally identical to TASK-MONO-071 → ADR-MONO-008 PROPOSED authoring path) / 콘솔 Next.js 구현 = Sonnet 4.6.
