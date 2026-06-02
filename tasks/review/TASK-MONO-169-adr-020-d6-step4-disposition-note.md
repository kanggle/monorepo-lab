# Task ID

TASK-MONO-169

# Title

ADR-MONO-020 D6 Step 4 (legacy single-value read cleanup) disposition — record (additive note to § 3.3 + § 6) that the cleanup is **intentionally NOT executed** at portfolio scale: the dual-read is the steady state (the `'*'` platform sentinel is assignment-inexpressible by design, and production has zero `operator_tenant_assignment` rows so every operator resolves via the legacy read). Doc-only; no decision reversal (HARDSTOP-04 additive — D6 Option A "staged migration" stands; step 4 was always conditional on "once all operators are migrated").

# Status

review

# Owner

docs / ADR disposition (no code — investigation conclusion recorded in the ADR)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- adr

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

- **records investigation of**: [ADR-MONO-020](../../docs/adr/ADR-MONO-020-operator-multitenant-assignment.md) D6 (migration phasing). Steps 0-3 are DONE (ACCEPTED → BE-326 table+dual-read → BE-327 assume-tenant exchange → MONO-158~162 switcher+e2e). Step 4 (cleanup) is the only outstanding D6 item; this task records its disposition.
- **evidence (read, not changed)**: `TASK-BE-326` `TenantScopeResolver` (the dual-read core) + `V0030__create_operator_tenant_assignment.sql` (net-zero, no backfill).
- **additive only (HARDSTOP-04 clean)**: D6 Option A (backward-compatible staged migration) is CHOSEN and **unchanged**; step 4's own text already conditions cleanup on "once all operators are migrated to assignments". This task records that the precondition is unmet AND structurally cannot be fully met (the `'*'` sentinel), so step 4 is dispositioned not-applicable at portfolio scale — a status record, not a decision reversal.
- **supersedes the "remaining" framing**: prior notes (e.g. MONO-168 § 9 roadmap "remaining = ADR-020 D6 step4 user-gated cleanup") treated step 4 as pending work; this closes it as intentional non-execution. (The `docs/project-overview.md` § 9 line reconciliation is deferred to the next reality-alignment — out of scope here; the ADR is the authoritative record.)

---

# Goal

Close the only dangling ADR-MONO-020 item (D6 Step 4 — "retire legacy single-value read") as an **evidence-backed disposition** so a future session/reviewer does not re-run the same investigation or attempt a regression-causing cleanup.

The investigation (2026-06-02) established that the dual-read introduced by D1/D6 step 1 (BE-326) is the **steady state**, not transitional scaffolding to be removed, for three reasons:

1. **Production has zero `operator_tenant_assignment` rows.** `V0030` creates an empty table and seeds nothing ("NET-ZERO"); there is no production backfill migration. Every operator's effective scope therefore resolves solely via the legacy `admin_operators.tenant_id` read. Retiring that read now would break **every** operator — the exact opposite of step 4's precondition ("once all operators are migrated to assignments").
2. **The `'*'` platform sentinel is assignment-inexpressible by design.** `TenantScopeResolver` short-circuits `tenant_id == '*'` to `{"*"}` and never expands it via assignments (javadoc: "field-level, assignment-independent; `isPlatformScope()` stays byte-unchanged"). `'*'` means "all tenants" — it cannot be enumerated as assignment rows. The legacy read is therefore **structurally permanent** for the platform-scope branch.
3. **The legacy home tenant is the net-zero base.** `resolveEffectiveTenantScope` always adds `legacyHomeTenantId`; full cleanup would require every operator to first hold an explicit assignment row covering its home tenant (a backfill that does not exist) plus a `'*'`-sentinel redesign.

Disposition: D6 Step 4 is **intentionally NOT executed** at the portfolio/demo scale. Its execution trigger would be a real-SaaS per-operator assignment backfill **and** a `'*'`-sentinel redesign — neither warranted now. ADR-MONO-020 is, with this note, effectively complete (steps 0-3 done; step 4 dispositioned).

# Scope

## In Scope

Doc-only — a single bundled PR (no code, no decision reversal):

1. **`docs/adr/ADR-MONO-020-operator-multitenant-assignment.md` § 3.3 item 4** — append a DISPOSITION note to the step-4 roadmap line recording the intentional non-execution + the three evidences + the execution trigger. The D6 decision body (§ 2 D6 table) is **byte-unchanged**.
2. **`docs/adr/ADR-MONO-020...` § 6 Status Transition History** — add one row: `2026-06-02 | D6 Step 4 disposition | intentionally NOT executed at portfolio scale (dual-read = steady state; '*' sentinel assignment-inexpressible; zero production assignment rows) | TASK-MONO-169 | #<this>`.
3. **Task md + root `tasks/INDEX.md`** entry.

## Out of Scope

- **Any code / migration / seed change.** No `TenantScopeResolver`, no `V0030`, no backfill. The dual-read stays exactly as shipped.
- **Reversing or re-deciding D6** — the § 2 D6 decision table (Option A CHOSEN) is byte-unchanged; this is an additive execution-status note (HARDSTOP-04 discipline).
- **`docs/project-overview.md` § 9 reconciliation** — deferred to the next reality-alignment (MONO-141/148/168 cadence); the ADR is the authoritative record this task corrects.
- **Actually implementing the cleanup** — explicitly the thing this task concludes should NOT be done now.

# Acceptance Criteria

- [ ] **AC-1** ADR-MONO-020 § 3.3 item 4 carries a DISPOSITION note: intentionally NOT executed at portfolio scale, with the three evidences (zero production assignment rows / `'*'` assignment-inexpressible / legacy = net-zero base) and the execution trigger (real-SaaS backfill + `'*'` redesign).
- [ ] **AC-2** ADR-MONO-020 § 6 has a new history row recording the disposition (date 2026-06-02, TASK-MONO-169, the PR).
- [ ] **AC-3** The § 2 D6 decision table (Option A vs B verdicts) is **byte-unchanged** — additive note only, no decision reversal (HARDSTOP-04 clean; `git diff` shows only § 3.3 item 4 + § 6 additions).
- [ ] **AC-4** The note's factual claims are verifiable in the repo: `V0030` is net-zero (no INSERT), `TenantScopeResolver` short-circuits `'*'`, no production migration seeds `operator_tenant_assignment`.
- [ ] **AC-5** Diff = `docs/adr/ADR-MONO-020-...md` + task md + `tasks/INDEX.md` only. No code/spec/contract/other-doc change.

# Related Specs

- [ADR-MONO-020](../../docs/adr/ADR-MONO-020-operator-multitenant-assignment.md) (the target — D6 § 2 table unchanged; § 3.3 item 4 + § 6 amended).
- [ADR-MONO-019](../../docs/adr/ADR-MONO-019-customer-tenant-model.md) (parent — D3-A single-value MVP that the dual-read keeps working).
- `projects/global-account-platform/apps/admin-service/.../application/TenantScopeResolver.java` (the dual-read core — evidence ② / ③).
- `projects/global-account-platform/apps/admin-service/src/main/resources/db/migration/V0030__create_operator_tenant_assignment.sql` (net-zero table — evidence ①).

# Related Contracts

- **None.** Doc-only ADR disposition note; no API/event/composition contract is touched.

# Edge Cases

- **Future real-SaaS adoption** — if the portfolio ever onboards real multi-customer operators at scale, the disposition's execution trigger (per-operator assignment backfill + `'*'` redesign) re-opens step 4. The note states this so the trigger is explicit, not lost.
- **`'*'` sentinel** — even after a hypothetical backfill of real-customer operators, the `'*'` platform-admin branch likely keeps the legacy read (assignment-inexpressible). The note frames cleanup as "retire the legacy read for non-`'*'` operators", not a total removal.

# Failure Scenarios

- **Misread as a decision reversal** — a reviewer could think this relaxes D6. Mitigation: AC-3 keeps the § 2 D6 table byte-unchanged; the note is explicitly an execution-status disposition under the step-4 precondition the ADR already stated.
- **Stale cross-doc reference** — `docs/project-overview.md` § 9 still says "remaining = D6 cleanup" until the next reality-alignment. Mitigation: out-of-scope note here + the next reality-alignment (MONO cadence) reconciles it; the ADR is authoritative in the interim.
- **CI** — docs-only, markdown/ADR fast-lane (path-filter `changes` pass + code jobs skip).

---

분석=Opus 4.8 / 구현 권장=Opus 4.8 (ADR disposition under HARDSTOP-04 additive discipline — the framing "status record, not decision reversal" needs precision; doc-only but the judgment is the deliverable). 직접 수행(조사 결론을 이미 보유).
