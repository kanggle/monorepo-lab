# Task ID

TASK-BE-316

# Title

admin-events.md L57 BE-257 stale prose follow-up — `ACCOUNT_BULK_CREATE` actionCode reality alignment

# Status

done

# Owner

backend

# Task Tags

- code
- event

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

# Goal

Resolve the F-T1-1 finding from `/refactor-spec all` (2026-05-26) — `specs/contracts/events/admin-events.md:L57` carries stale prose written **before TASK-BE-257** finalised its actual emission decision.

**Stale state (current)**:

```
- `ACCOUNT_BULK_CREATE` (TASK-BE-257): account-service가 bulk provisioning API
  호출 시 발행 예정. 현재는 account-service의 `account_status_history`에만
  기록됨. admin-service가 provisioning 감사 emission 패턴을 확립한 후 이
  이벤트를 실제 발행한다 (TODO).
```

**Reality (post-BE-257 done)**:

- TASK-BE-257 (`projects/global-account-platform/tasks/done/TASK-BE-257-bulk-provisioning-api.md` Goal §4) finalised: "outbox 이벤트는 **individual `account.created` 이벤트 N 건 발행** (downstream consumer는 단건 처리)" — i.e. account-service publishes per-row `account.created` envelopes, NOT a single bulk audit envelope on `admin.action.performed`.
- `admin-events.md:L26` still lists `ACCOUNT_BULK_CREATE` in the `actionCode` enum of `admin.action.performed`, with no producer in admin-service code (`apps/admin-service/src/main/java/com/example/admin/application/AdminActionAuditWriter.java` does not emit this code).
- The trailing `(TODO)` marker is therefore double-stale: (a) BE-257 is `done/`, so the future-tense prose has no live driver; (b) the `(TODO)` violates `platform/coding-rules.md:L75` ("No `TODO` comments without a linked task ID") — the linked task already exists (BE-257), but the marker promises further work that BE-257 explicitly de-scoped.

This task picks the right disposition for the prose **and** the enum constant, and lands a single-step spec correction.

## Disposition Options (3-option weighing per refactor-spec Tier 2 closure pattern)

| Option | Action | Pros | Cons |
|---|---|---|---|
| **A** (recommended) | Rewrite L57 prose: `ACCOUNT_BULK_CREATE` is defined in the actionCode enum but **currently has no emitter** by deliberate BE-257 design (bulk provisioning emits per-row `account.created` instead). Future emission would require a separate task — drop the `(TODO)` marker. Keep L26 enum entry unchanged (forward-compatible per L56 `forward-compatible` rule). | Behavior-neutral (no consumer code, no event payload field changes). Preserves the L56 forward-compatible spirit (unknown codes are logged + processed). Removes the misleading `(TODO)` + stale future-tense. Mechanical. | L26 enum keeps an entry with no producer — slight cognitive drag for new readers. |
| **B** | Remove BOTH L57 prose AND L26 `ACCOUNT_BULK_CREATE` enum entry. | Cleanest reality alignment — spec exactly matches admin-service code. | **Contract change**: shrinks the actionCode enum. External SIEM consumers reading the spec lose the documented possibility of this code being added later. If a future BE-NNN restores bulk audit emission, this enum addition surfaces as a "new code" change (the L56 forward-compatible rule covers it, but the spec churn is higher). |
| **C** | Strip the `(TODO)` from L57 only; leave the future-tense prose otherwise intact. | Mechanically minimal. | Does not fix the underlying staleness: BE-257 is `done/` and explicitly chose a different emission channel. Reader still sees "예정" prose with no live owning task — silent re-introduction of the same finding next audit cycle. |

**Recommendation**: **Option A** — surgical L57 prose rewrite, L26 enum preserved. Behavior-neutral, mechanical, addresses BOTH the BE-257 reality-misalignment AND the `(TODO)` coding-rules violation, without contracting the actionCode enum.

If WI selects Option B, also update the `actionCode` field type definition string at L26 to match the reduced enum (`"ACCOUNT_LOCK | ACCOUNT_UNLOCK | ACCOUNT_DELETE | SESSION_REVOKE | AUDIT_QUERY"`) — single coherent edit.

---

# Scope

## In Scope

- `projects/global-account-platform/specs/contracts/events/admin-events.md`:
  - **L57** prose rewrite (Option A) OR L57 + L26 enum entry removal (Option B). Pick exactly one option in the impl PR and document the choice in the commit message body.
  - No other lines.

## Out of Scope

- Any change to `apps/admin-service/` production code (no emitter exists for `ACCOUNT_BULK_CREATE`; this task does not introduce one — that would be a separate BE-NNN if/when business demand surfaces).
- Any change to `apps/account-service/` bulk provisioning code (BE-257 is `done/` and authoritative on per-row `account.created` emission).
- `admin-api.md` HTTP envelope (unchanged regardless of option).
- Re-litigating BE-257's per-row decision (accepted in BE-257 Goal §4).

---

# Acceptance Criteria

- [ ] `admin-events.md:L57` no longer contains `(TODO)` literal.
- [ ] `admin-events.md:L57` no longer asserts future-tense "발행 예정 ... 실제 발행한다" — prose either states "no emitter (by BE-257 design)" (Option A) or the line is removed entirely (Option B).
- [ ] (Option B only) `admin-events.md:L26` `actionCode` field type string drops `| ACCOUNT_BULK_CREATE`.
- [ ] grep for `\bTODO\b` in `projects/global-account-platform/specs/contracts/events/` returns 0 results.
- [ ] grep for `발행 예정` in the same directory returns 0 results.
- [ ] Commit message body explicitly states which option (A / B) was applied + reason.
- [ ] No other files modified except the task lifecycle move (`ready/` → `review/`) and `tasks/INDEX.md`.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — `PROJECT.md` (domain=saas, traits=[transactional, regulated, audit-heavy, integration-heavy, multi-tenant]), `rules/common.md`, `rules/domains/saas.md`, 5 trait files.

- `projects/global-account-platform/specs/contracts/events/admin-events.md` — target file.
- `projects/global-account-platform/tasks/done/TASK-BE-257-bulk-provisioning-api.md` — authoritative for "individual `account.created` N건" decision (Goal §4).
- `platform/coding-rules.md:L75` — `TODO` marker rule.
- `rules/traits/audit-heavy.md` — admin audit event emission constraints.

# Related Skills

- `.claude/skills/review-checklist/SKILL.md`

---

# Related Contracts

- `projects/global-account-platform/specs/contracts/events/admin-events.md` (the spec being polished).
- `projects/global-account-platform/specs/contracts/http/internal/account-internal-provisioning.md` — BE-257 endpoint contract (unchanged regardless of option).

---

# Target Service

- (spec-only — no service code is modified)

Optional verification touchpoint: `admin-service` (confirm no `ACCOUNT_BULK_CREATE` emitter regression introduced — should remain absent).

---

# Architecture

No architecture change. Spec text polish only.

---

# Implementation Notes

1. Pick Option A or Option B in the impl PR. Recommended: **Option A** (smaller blast radius, contract-neutral).
2. Single-file `Edit` is sufficient. No script needed.
3. Verify with two greps after the edit:
   ```
   grep -rn "TODO" projects/global-account-platform/specs/contracts/events/
   grep -rn "발행 예정" projects/global-account-platform/specs/contracts/events/
   ```
   Both must return 0.
4. Branch name MUST NOT contain the substring `master` (CLAUDE.md Local Network section / repeated incident across BE-052, BE-161). Suggested: `task/be-316-admin-events-bulk-create-stale-prose-fix`.

---

# Edge Cases

- If Option B is chosen and a future task restores `ACCOUNT_BULK_CREATE`, L56's forward-compatible rule already covers re-introduction (unknown codes are logged + processed by existing consumers). No retroactive consumer break.
- If Option A is chosen, the L26 enum entry remains as a documented-but-unused code — a future emitter task can adopt it directly without spec churn.

---

# Failure Scenarios

- Wrong option applied without commit-message rationale → next audit re-flags the same line. Mitigation: AC requires the choice be stated in the commit body.
- Edit touches L26 unnecessarily under Option A → behavior-neutrality lost, contract drift introduced. Mitigation: scope explicitly excludes L26 under Option A.
- TODO marker re-introduced elsewhere in the same PR → grep AC catches it.

---

# Test Requirements

- No code tests required (spec-only).
- grep AC (2 patterns, expected 0 results each) is the verification.
- No `./gradlew` invocation needed; if the optional admin-service verification touchpoint is exercised, `./gradlew :projects:global-account-platform:apps:admin-service:test` (CI authoritative for Testcontainers IT per `project_testcontainers_docker_desktop_blocker`).

---

# Definition of Done

- [ ] Option A or B applied; commit body states which.
- [ ] Both grep ACs return 0.
- [ ] Branch: `task/be-316-admin-events-bulk-create-stale-prose-fix` (substring `master` 금지).
- [ ] PR: `docs(gap-admin-events):` single-file spec polish.
- [ ] Lifecycle: `ready/` → `review/` → `done/` per standard.
- [ ] (분석=Opus 4.7 / 구현 권장=Sonnet 4.6 — 단순 spec text fix, mechanical, low risk)
