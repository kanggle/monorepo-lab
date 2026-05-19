# ADR-MONO-008 — finance-platform Bootstrap Criteria, Integration Mode, Template-First Procedure

**Status:** ACCEPTED
**Date:** 2026-05-13
**History:** PROPOSED 2026-05-13 (TASK-MONO-071 — bootstrap criteria pre-authored; not the bootstrap authorisation). · ACCEPTED 2026-05-18 (TASK-MONO-113 — D5.1–D5.6 evaluated, § D6.1 user-explicit intent satisfied; D1 Option C; PR-A doc-only transition, bootstrap artifact = PR-B / TASK-MONO-114).
**Decision driver:** Phase 5 LAUNCHED 2026-05-13 (ADR-MONO-003b ACCEPTED) — `kanggle/project-template` exists and is operational. ADR-MONO-002 § D4 recommends `scm → finance → erp → mes` ordering; scm shipped 2026-05-04~07; finance is next. ADR-MONO-003a § D2.1 mandates that any new project bootstrap requires a fresh ADR because it resets the shared-library churn clock and shifts portfolio narrative scope — both decision points the D4 OVERRIDE was not designed to cover. This ADR is that fresh ADR for finance.
**Supersedes:** none.
**Related:** [ADR-MONO-002](ADR-MONO-002-phase-4-template-extraction-trigger.md) § D4 (ordering parent), [ADR-MONO-003a](ADR-MONO-003a-d4-override-scope-canonicalization.md) § D2.1 (new domain bootstrap requires fresh ADR — this ADR satisfies that), [ADR-MONO-003b](ADR-MONO-003b-phase-5-launch-criteria.md) § 1.4 + § D3 (Template ↔ monorepo sync context), [TEMPLATE.md](../../TEMPLATE.md) § Phase 6+ (downstream procedure), [`rules/taxonomy.md`](../../rules/taxonomy.md) § Financial Services (domain choices), memory [`project_portfolio_7axis_architecture`](../../../memory/project_portfolio_7axis_architecture.md), memory [`project_scm_platform_bootstrap`](../../../memory/project_scm_platform_bootstrap.md) (5번째 프로젝트 부트스트랩 reference).

---

## 1. Context

### 1.1 Phase 5 LAUNCHED state

`kanggle/project-template` is public, `is_template: true`, 435 files / 2.7 MiB at source SHA `68b6877c`. The `Use this template` flow is operational but has no downstream user yet. Phase 5 LAUNCH did not commit to a specific bootstrap timeline (per ADR-MONO-003b § 1.4: "It does not commit to a specific future project being bootstrapped from the Template"), but finance is the natural next domain per ADR-MONO-002 § D4.

### 1.2 Why an ADR for the bootstrap

Per ADR-MONO-003a § D2.1:

> Adding finance / erp / mes / any new project skeleton under `projects/<name>/` is NOT an OVERRIDE-class change... Bootstrap inherently resets the churn clock for `libs/` (skeleton-driven `settings.gradle` change) and shifts portfolio narrative scope — both are decision points the OVERRIDE was not designed to cover.

So the bootstrap cannot land under existing OVERRIDE authority. A fresh ADR is required. This is that ADR.

The pre-author pattern is identical to ADR-MONO-003b: PROPOSED status now (criteria documented, decision not yet made), ACCEPTED transition later (user-explicit intent + readiness checklist re-evaluated + bootstrap executed).

### 1.3 Why PROPOSED, not ACCEPTED

PROPOSED ≠ "we will bootstrap eventually". PROPOSED = "if we choose to bootstrap finance, here are the criteria the moment must satisfy". Same staged pattern that made ADR-MONO-003b work cleanly.

### 1.4 Scope: what "finance bootstrap" means

The bootstrap = three artifacts in sequence:

1. `gh repo create kanggle/finance-platform --template kanggle/project-template --public --clone` produces a fresh standalone repo from the Template.
2. The clone is populated with: `PROJECT.md` (domain + traits + service_types), first service skeleton, first task in `tasks/ready/`.
3. (Optional D1 choice) Monorepo `direct-include` registration: `projects/finance-platform/` added with composite/direct-include integration, `settings.gradle` updated, root `package.json` shortcut script added.

ACCEPTED transition produces 1 + 2 minimum; 3 is the D1 integration-mode choice.

---

## 2. Decision

### D1 — Integration mode

Three options. Choice deferred to ACCEPTED transition.

| Option | Pros | Cons | Portfolio narrative |
|---|---|---|---|
| **A. Standalone-only** (Template fork, no monorepo entry) | Cleanest "Use this template" demonstration. Zero monorepo churn (settings.gradle untouched, libs/ untouched). No D2.1 churn-clock reset. | No monorepo CI / cross-project refactor benefits. Library updates must be back-ported manually per ADR-003b § D3.5. | Demonstrates the Template flow end-to-end. Signal: "the strategy works as designed" — Discovery → Distribution → independent evolution. |
| **B. Monorepo-only** (`projects/finance-platform/` direct-include, no standalone) | Full monorepo benefits (CI, refactor atomicity, libs/ sync). | Skips the Template flow demonstration. D2.1 churn-clock reset. ADR-MONO-003a § D2 explicit OUT-of-scope reasoning applies. | Continuation of scm-platform model. Signal: "monorepo continues to be the dev surface" — but the Template wasn't actually used. |
| **C. Both** (Template fork + monorepo direct-include via standalone-repo `direct-include` style à la ecommerce/GAP import) | Template demo + monorepo benefits. Per memory `project_ecommerce_import_readiness.md` trigger logic, this is the "external prototype joining monorepo" pattern. | Double-bookkeeping (sync needed). Most complex artifact. Two truth surfaces. | Strongest signal: "Discovery → Distribution → Re-integration is a real workflow". Closest to memory `project_monorepo_template_strategy` end-state vision. |

**Default recommendation at ACCEPTED**: Option C (both), but ACCEPTED moment may pick A or B based on user judgement on portfolio narrative value vs maintenance cost.

### D2 — Project classification

Per `rules/taxonomy.md` § Financial Services, four candidate domains: `fintech`, `pg`, `banking`, `securities`.

**Recommended primary domain**: `fintech` — broadest scope, captures Account / Wallet / Transaction / KYC / Risk / Compliance which together represent a portfolio-grade "financial services" generalist project.

**Trait stack (proposed at ACCEPTED transition; not finalised in PROPOSED)**:

- `transactional` (necessary — financial ops are inherently transactional)
- `regulated` (per taxonomy: KYC / AML / PCI-DSS-adjacent compliance signals)
- `audit-heavy` (per taxonomy: every financial operation traceable)
- Optional: `event-driven` (if outbox / saga / event-replay are part of v1 scope)

This trait stack stresses the rule library on a NEW combination (vs scm's `transactional + integration-heavy + batch-heavy`), specifically `regulated + audit-heavy` simultaneously. Portfolio gain: validates the rule library across "compliance-heavy" domain surfaces.

**service_types**: `rest-api`, `event-consumer` minimum. Optionally `batch-job` if scheduled reconciliation jobs are in v1 scope.

### D3 — Initial service skeleton scope

One service for v1. Two candidate first services:

| Candidate | Responsibility | Architecture style | Stress axis |
|---|---|---|---|
| **`account-service`** | Account lifecycle (KYC, balance, hold/release) | Hexagonal | `transactional` + `audit-heavy` |
| **`ledger-service`** | Double-entry ledger (debit/credit, balance reconciliation, period close) | Hexagonal + DDD | `transactional` + `audit-heavy` + harder constraints (immutable journal) |

**Default recommendation**: `account-service` — simpler v1 scope; `ledger-service` deferred to v2 (after the rule library digests the `regulated + audit-heavy` combination at smaller scope first).

### D4 — Procedure

Pre-bootstrap:

1. `git status` clean in monorepo.
2. `bash scripts/verify-template-readiness.sh` — record exit code (informational only; this ADR doesn't gate on it).
3. Decide D1 / D2 / D3 finalisation.

Bootstrap (Template flow):

4. `gh repo create kanggle/finance-platform --template kanggle/project-template --public --clone --description "Finance platform — fintech domain (account-service v1) bootstrapped from kanggle/project-template ADR-MONO-008 ACCEPTED <date>"`
5. `cd finance-platform/`
6. Rename `projects/<placeholder>/` to `projects/finance-platform/` if Option B / C, or hoist its content to repo root + delete `projects/` level if Option A.
7. Author `PROJECT.md` per D2 classification.
8. Author first service skeleton at `projects/finance-platform/apps/account-service/` (Option B/C) or `apps/account-service/` (Option A).
9. Author `tasks/ready/TASK-FIN-BE-001-account-service-bootstrap.md` (skeleton task; implementation deferred).
10. `docker-compose.yml` with Traefik hostname `finance.local` (per TEMPLATE.md § Local Network Convention).
11. `.env.example`.
12. Commit + push.

Monorepo integration (Options B / C):

13. `cd <monorepo>` + new branch `task/mono-072-finance-direct-include`.
14. Update root `settings.gradle` to include `projects:finance-platform:apps:account-service`.
15. Update root `package.json` if shortcut scripts pattern is used.
16. Sanity check: `./gradlew :projects:finance-platform:apps:account-service:tasks` lists tasks.
17. Register in `scripts/sync-portfolio.sh` PROJECT_REMOTES (Option C only; Option B unregistered, Option A unregistered since standalone-only).

Recording (same PR as monorepo integration, or separate PR for Option A):

18. Append ACCEPTED row to this ADR § 6.
19. Append ADR-MONO-003a § 3 audit-trail row (category: new domain bootstrap, Phase 6 first artifact).
20. Update memory `project_portfolio_7axis_architecture.md` + `project_monorepo_template_strategy.md` to reflect finance bootstrapped + Template first-use confirmed.

### D5 — Readiness criteria

Before ACCEPTED transition, the implementer evaluates:

| # | Criterion | Default verification |
|---|---|---|
| **D5.1** | `fintech` domain in `rules/taxonomy.md` | grep `^#### fintech` rules/taxonomy.md — confirmed present at L201. |
| **D5.2** | Trait stack finalised | D2 trait list reviewed against `rules/taxonomy.md` § Traits — all 11 traits available; final stack picked. |
| **D5.3** | Initial service decision finalised | D3 choice between `account-service` / `ledger-service` / other made. |
| **D5.4** | Integration mode chosen (A / B / C) | D1 weighed against current portfolio state; one option picked. |
| **D5.5** | User-explicit intent recorded | "ADR-008 ACCEPTED" / "finance bootstrap 시작" / equivalent. |
| **D5.6** | Template repo unchanged since launch (informational) | `gh api repos/kanggle/project-template --jq .pushed_at` matches launch SHA's timestamp or is a planned sync update. |

### D6 — ACCEPTED transition mechanics

#### D6.1 — User-explicit intent forms

Any of:

- "ADR-008 ACCEPTED" / "ADR-008 ACCEPTED 진행"
- "finance 부트스트랩 시작" / "finance bootstrap launch"
- "finance-platform 만들어" with affirmative direction context

Ambiguous statements ("finance는 언제?", "finance도 해야지") do NOT satisfy.

#### D6.2 — Commit pattern

ACCEPTED transition + actual bootstrap typically produces 2 PRs:

- **PR-A** (this ADR's status flip + recording): ADR-008 PROPOSED → ACCEPTED, ADR-MONO-002 § D4 footer update, ADR-MONO-003a § 3 row, memory updates. Doc-only.
- **PR-B** (bootstrap artifact): new `kanggle/finance-platform` repo (external) + monorepo `projects/finance-platform/` direct-include + first service skeleton + first task.

Option A (standalone-only) collapses PR-B to "monorepo PR with only the ADR row + memory; standalone repo creation logged in launch record".

#### D6.3 — Audit-trail row format (this ADR's § 6)

```
| YYYY-MM-DD | ACCEPTED | <Option> | <domain+traits+service_types> | <Standalone URL | Monorepo path | Both> | <user-intent-quote> | <PR-A # / PR-B # > |
```

---

## 3. Consequences

### 3.1 PROPOSED merge (this PR)

- ADR-MONO-003a § D2.1's mandate satisfied — finance bootstrap now has its required fresh ADR (PROPOSED status).
- No actual bootstrap. Phase 5 LAUNCHED state unchanged.
- Future "should we bootstrap finance now?" question has a concrete checklist (§ D5) to evaluate against.

### 3.2 ACCEPTED moment (future)

- D5.1–D5.6 evaluated.
- D6.1 intent confirmed.
- D6.2 commit pattern followed.
- New `kanggle/finance-platform` GitHub repo created via Template flow (D1 Option A / B / C).
- ADR-MONO-002 § D4 ordering progresses scm → **finance**.
- 6th project visible in monorepo (if Option B / C) or in portfolio hub (if Option A / C).

### 3.3 Post-bootstrap

- Monorepo project count: 5 → 6 (Option B / C) or unchanged (Option A).
- Standalone portfolio repo count: 5 → 6 (Option A / C) or unchanged (Option B).
- Shared library churn clock reset (settings.gradle + potential libs/ touch for new patterns). Per ADR-MONO-003a § D2.1, this is the expected D2.1 consequence.
- Memory `project_monorepo_template_strategy.md` carries the first downstream Template usage record.

### 3.4 Future-self / future-LLM-session

- A future session evaluating "should we bootstrap finance now?" reads this ADR § D5.
- A future session prompted to "bootstrap finance" reads § D4 procedure + § D6.1 intent forms before mutating anything.

---

## 4. Alternatives Considered

### 4.1 Skip finance, jump to erp / mes

ADR-MONO-002 § D4 recommends `scm → finance → erp → mes` ordering based on stress-axis progression:

| Step | New stress | Justification |
|---|---|---|
| wms → ecommerce → GAP → fan-platform → **scm** | `transactional + integration-heavy + batch-heavy` simultaneously | Cross-project event consumption first time. (PASSED 2026-05-04~07.) |
| scm → **finance** | adds `regulated + audit-heavy` | Compliance domain surface first time. |
| finance → erp | adds workflow-heavy + multi-module integration | Internal-system workflow patterns. |
| erp → mes | adds plant-floor real-time + safety | Hardest domain (deferred). |

Reversing skips intermediate validation. Rejected.

### 4.2 Bootstrap without an ADR

Rejected by ADR-MONO-003a § D2.1 explicit text. Bypassing would violate the meta-rule that gives D4 OVERRIDE its consistency.

### 4.3 Bootstrap into monorepo only (Option B)

Considered: skip the Template flow demo entirely. Continue the scm-platform pattern.

Rejected as default (but kept as D1 Option B): the Template repo existed for 0 seconds of useful life before being unused. The whole Phase 5 narrative collapses if no downstream user materialises. At least one downstream Template usage is the minimum "the strategy worked as designed" signal.

### 4.4 Standalone-only (Option A) without monorepo entry

Considered: cleanest Template demo, zero monorepo churn.

Rejected as default (but kept as D1 Option A): monorepo CI / refactor benefits disappear. Library updates require manual back-port. For a portfolio project that the owner actively iterates on, this is awkward.

### 4.5 ACCEPTED status now (skip PROPOSED stage)

Considered: ADR-008 could go straight to ACCEPTED if we treat the bootstrap as imminent.

Rejected: identical reasoning to ADR-MONO-003b § 4.6. The user has not stated bootstrap intent in this session. PROPOSED is the correct status for criteria documents authored before the decision.

---

## 5. Relationship to ADR-MONO-002 and ADR-MONO-003b

| Aspect | ADR-MONO-002 | ADR-MONO-003b | ADR-MONO-008 (this ADR) |
|---|---|---|---|
| Scope | Phase 4 catalyst trigger + ordering | Phase 5 launch gate | Phase 6 first downstream (finance) |
| Status | ACCEPTED | ACCEPTED | PROPOSED |
| Key decision | scm catalyst + D4 ordering | Launch criteria + procedure + sync + rollback | Integration mode + classification + procedure + readiness checklist |
| Audit trail | n/a | § 6 transition history | § 6 transition history (analogous structure) |
| Forward pointer pattern | § D4 footer to ADR-MONO-008 (after this PR) | § Status + § 6 (canonical) | § 5 → ADR-MONO-016 (erp). NB: identifier corrected — `ADR-MONO-009` = Chrome DevTools MCP (gap #4, pre-existing); erp bootstrap ADR = **ADR-MONO-016**, filed PROPOSED 2026-05-19 (TASK-MONO-117). |

**Practical reading order for a new session evaluating "should we bootstrap finance now?"**:

1. Read this ADR § 2 (Decision) — entire D1–D6 block.
2. Read ADR-MONO-002 § D4 to confirm ordering intent.
3. Re-evaluate D5.1–D5.6 against current monorepo state.
4. If criteria pass and user intent satisfies § D6.1, follow § D4 procedure + § D6.2 commit pattern.

---

## 6. Status Transition History

Append-only.

| Date | Transition | Option | Classification | Standalone / Monorepo / Both | User intent quote | PR(s) |
|---|---|---|---|---|---|---|
| 2026-05-13 | created PROPOSED | TBD | TBD | TBD | n/a (criteria pre-author, not bootstrap intent) | TBD (this PR) |
| 2026-05-18 | ACCEPTED | C (Both) | fintech / [transactional, regulated, audit-heavy] / [rest-api, event-consumer] | Both (kanggle/finance-platform standalone via Template fork + projects/finance-platform/ monorepo direct-include) | "finance-platform bootstrap" → domain "fintech" → integration mode "C. Both" (deliberate AskUserQuestion authorization, 2026-05-18; satisfies § D6.1 affirmative-direction form, not the excluded ambiguous form) | PR-A #593 (squash `1c98fab6`) / PR-B #595 (squash `d2b579f2`) — monorepo `projects/finance-platform/` direct-include + GAP V0017 seed LANDED (GAP Testcontainers IT pass 2m56s = authoritative); external `kanggle/finance-platform` Template-fork **PENDING** user `gh repo create` (classifier-blocked outward-facing op, handed off 2026-05-18; Option C standalone side not yet confirmed) |
| 2026-05-19 | Option C standalone side CONFIRMED | C (Both) | (unchanged — fintech / [transactional, regulated, audit-heavy] / [rest-api, event-consumer]) | Both — **standalone side CONFIRMED** | 사용자 "a 했어" (옵션 A 실행: `gh repo create kanggle/finance-platform --template kanggle/project-template --public --clone` + stale remote ref prune; 2026-05-19 사용자 셸 — 직전 AskUserQuestion "외부 fork 선행 (정석)" 선택의 실행) | spec PR #612 (squash `c1c27011`) / impl PR #613 (squash `f511105d`) / close chore PR (this) — TASK-MONO-116. `gh repo view kanggle/finance-platform` 객관 검증: `templateRepository={name:project-template,owner:kanggle}` + `visibility:PUBLIC` + `isTemplate:false` + `createdAt:2026-05-19T03:42:33Z` + url `https://github.com/kanggle/finance-platform` = 진짜 Template-derived fork → ADR-MONO-008 D1 **Option C** standalone side 충족. 2026-05-18 ACCEPTED row 의 **PENDING** 이 본 row 로 해소 (그 row 본문 불변 — append-only). finance v1 양쪽(monorepo 행위-증명 chain MONO-115→FIN-BE-002→003→004 CI 12/12 + standalone Template fork) 완전 종결 = ADR-MONO-008 전 항목 해소. |

(ACCEPTED row appended 2026-05-18 per § D6.3 format. PR-A/PR-B numbers backfilled at PR open / close chore — append-only, no rewrite. D5.2 trait stack excludes ADR § D2's "optional event-driven" — not one of the 11 taxonomy traits, would HARDSTOP-02.)

(2026-05-19 resolution row appended per § D6.3 append-only — the 2026-05-18 ACCEPTED row's "PENDING" text is preserved verbatim as honest history; resolution is recorded as a NEW row, never a rewrite. TASK-MONO-116; spec PR #612 `c1c27011` / impl PR #613 `f511105d` (PR# backfill = §-sanctioned mechanical bookkeeping per this note's prior sentence, not a decision rewrite).)

---

## 7. Provenance

- ADR-MONO-002 § D4 — ordering origin.
- ADR-MONO-003a § D2.1 — explicit mandate for fresh ADR on new domain bootstrap. This ADR is that fresh ADR.
- ADR-MONO-003b § 1.4 — "It does not commit to a specific future project being bootstrapped from the Template" — confirms no obligation; this ADR is the explicit invitation to bootstrap one when the user is ready.
- Memory `project_portfolio_7axis_architecture.md` — 7-axis architecture confirms finance as next.
- Memory `project_scm_platform_bootstrap.md` — bootstrap pattern reference (5번째 프로젝트 부트스트랩 5 PR).

분석=Opus 4.7 / 구현=Opus 4.7 (meta-policy authoring — D1 integration mode + D2 classification + D5 criteria phrasing require interpretive judgement; structurally identical to TASK-MONO-069 ADR-003b PROPOSED authoring path).
