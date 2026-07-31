# Task ID

TASK-MONO-495

# Title

Track ADR-MONO-058 (fleet-wide shared technical scaffolding consolidation) pending owner ACCEPT

# Status

done

<!-- ADR-MONO-058 ACCEPTED 2026-07-30 (exact-form owner instruction). Split into per-decision/per-project tasks 2026-07-31 per this task's own Acceptance Criteria — see Implementation Notes for the full list. This task produces no code of its own and closes by superseding. -->

# Owner

backend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

This task exists only to hold a place in the root task queue for `ADR-MONO-058` (filed `PROPOSED` alongside this task, `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md`) — a fleet-wide consolidation of technical scaffolding duplicated across all 8 projects, found by an 8-project parallel commonality/naming audit run 2026-07-29. Per `platform/shared-library-policy.md § Review Rule`/`§ Change Rule`, none of the ADR's D1–D8 decisions may be implemented until a human accepts the ADR with the exact-form instruction (`"ADR-MONO-058 ACCEPTED"`) — an agent may not self-accept. **This task is not implementable as written and must not be picked up from `ready/` until that happens.**

---

# Scope

## In Scope

- Once `ADR-MONO-058` is ACCEPTED: split the ADR's § 6 suggested sequence into individual per-decision, per-project tasks (a root task per shared-library promotion — D1/D4/D5/D6 — plus per-service adoption tasks for D2/D3/D7/D8 in each affected project's own `tasks/ready/`), per this repo's shared-vs-project task-filing boundary.
- Until ACCEPT: nothing. This task's only job is to be a visible, queryable placeholder so the ADR doesn't silently fall out of anyone's view the way a `PROPOSED` record sitting only in `docs/adr/` can.

## Out of Scope

- Any actual code change from D1–D8 — all of that is genuinely out of scope until ACCEPT, not just deferred.
- Deciding the ADR's open questions (D2's `details`-field/status-code conflict, D1's per-project role-set threading) — those are implementation-time decisions for whoever picks up the post-ACCEPT tasks, not this placeholder.

---

# Acceptance Criteria

- [x] Before this task is worked, `docs/adr/ADR-MONO-058-...md`'s `Status` field reads `ACCEPTED` (verify by reading the file directly, not by inference from this task's own prose — an ADR status can go stale, per this repo's own documented history of `ADR-MONO-049`'s bracket going stale three times). Confirmed `ACCEPTED` 2026-07-31 by reading the file directly.
- [x] Once ACCEPTED: this task closes by being superseded — split into the per-decision/per-project tasks described in Scope, referencing this task and the ADR as their origin, then this task moves `ready → done` with a note pointing at the split tasks (it produces no code of its own). See "Split completed 2026-07-31" above.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` (the decision record this task tracks)
- `platform/shared-library-policy.md` (the gate this ADR satisfies)
- `platform/architecture-decision-rule.md` (the ACCEPT-gate procedure)

---

# Related Contracts

None — this task itself makes no code or contract change.

---

# Target Service

- Monorepo-level — no single service; the ADR spans `libs/java-security`, `libs/java-security-servlet`, and 8 projects' `apps/`.

---

# Architecture

N/A — tracking task only.

---

# Implementation Notes

Filed alongside `ADR-MONO-058` in the same PR, per this repo's convention of a PROPOSED ADR needing a visible queue entry (precedent: `ADR-MONO-057` was proposed via its own PR, `#2988`, before later acceptance).

---

## Split completed 2026-07-31

`fan-platform`'s D1/D2/D5 were already implemented and DONE before this split (`TASK-FAN-BE-038/039/040`, landed prior to this task being picked up) — not re-listed below. Every other decision confirmed applicable by the ADR's § 1.1 table was re-verified against **current code**, not just the audit snapshot, before filing; several of the ADR's findings turned out stale and were deliberately **not** filed as tasks (noted below) rather than carried forward blindly.

**Root shared-library promotions** (`tasks/ready/`):
- `TASK-MONO-500` — D4 security-chain assembly builder → `libs/java-security-servlet`
- `TASK-MONO-501` — D6 canonical `IamClientCredentialsTokenProvider` → `libs/java-security`

(D1's actor/JWT cluster and D5's `PublicPathSet` were already promoted to `libs/java-security-servlet` via `TASK-FAN-BE-040`/`039` — no new root task needed for those.)

**Per-project adoption tasks filed**:
- `erp-platform`: `TASK-ERP-BE-037` (D1+D4 bundled, blocked on `TASK-MONO-500`), `038` (D2), `039` (D3), `040` (D5)
- `scm-platform`: `TASK-SCM-BE-054` (D1+D4 bundled, blocked on `TASK-MONO-500`), `055` (D2), `056` (D3), `057` (D5)
- `iam-platform`: `TASK-BE-568` (D6, blocked on `TASK-MONO-501`), `TASK-BE-569` (D7/ResilienceClientFactory). **D1 and D2 re-verified as not applicable**: iam has no `ActorContext`/`@CurrentActor` pattern anywhere (it's the JWT issuer, not a claims-consuming resource server) and all 4 servlet services already `extends CommonGlobalExceptionHandler` — both were stale audit findings, no task filed.
- `wms-platform`: `TASK-BE-567` (D2), `568` (D3), `569` (D4, blocked on `TASK-MONO-500`), `570` (D5 — wms has no standalone `PublicPaths` class at all, task scopes introducing one), `571` (D7/EventDedupePort only)
- `ecommerce-microservices-platform`: `TASK-BE-566` (D2), `567` (D3), `568` (D6, blocked on `TASK-MONO-501`), `569` (D7/EventDedupePort), `570` (D7/ResilienceClientFactory), `571` (D8). **D8 flagged as not a mechanical swap**: `TenantClaimEnforcer` requires a local JWT resource-server chain that ecommerce services don't have (they trust gateway-forwarded headers) — the task requires an explicit architectural decision, not a silent filter swap. The ADR's cited prerequisite `TASK-BE-557` doesn't exist under that ID; the equivalent work is `TASK-BE-564` (done) — referenced instead.
- `finance-platform`: `TASK-FIN-BE-065` (D1), `066` (D2 — supersedes the earlier `TASK-FIN-BE-058` WONTFIX on the 422-vs-400 conflict, since `CommonGlobalExceptionHandler` now has the override hook that WONTFIX was waiting on; distinct from the already-done `ADR-003`/`TASK-FIN-BE-064` Money/Currency work), `067` (D3)
- `fan-platform` (remaining items only): `TASK-FAN-BE-041` (D6, blocked on `TASK-MONO-501`), `042` (D7/EventDedupePort), `043` (D3), `044` (D4, blocked on `TASK-MONO-500`, filed standalone since D1 already landed separately)
- `platform-console`: `TASK-PC-BE-016` (D2, generic exception-handler tail only). **D7/ResilienceClientFactory re-verified as already adopted**: `TASK-PC-BE-015` (2026-07-22, predates the audit) already wired `Resilience4jLegResilienceAdapter` from `libs/java-common.ResilienceClientFactory` with explicit timeouts — stale finding, no task filed.

Total: 2 root + 29 per-project adoption tasks across 8 projects, all `Status: ready`, no code implemented by this split.

---

# Edge Cases

- If a future session finds this task sitting in `ready/` and is tempted to "just start" because the ADR feels obviously correct — it is explicitly not authorized without the owner's exact-form ACCEPT. Re-read `platform/architecture-decision-rule.md § The ACCEPTED Gate` before assuming otherwise.

---

# Failure Scenarios

- Implementing any of D1–D8 before ACCEPT would violate `shared-library-policy.md § Change Rule` and this repo's ADR-ACCEPT-gate convention — treat any such attempt as a Hard Stop, not a judgment call.
- Letting this task sit stale after the ADR is actually accepted (i.e. nobody splits it into real work) reproduces the exact "declaration outlives the truth" failure class this repo has already documented three times for `ADR-MONO-049`'s own status bracket — if you find the ADR is ACCEPTED but this task hasn't been split yet, split it before doing anything else in this area.

---

# Test Requirements

- None — tracking task only.

---

# Definition of Done

- [x] ADR-MONO-058 status confirmed (ACCEPTED or still PROPOSED) before any action
- [x] If ACCEPTED: split into per-decision/per-project tasks, this task moved to done referencing them
- [ ] ~~If still PROPOSED: no action, task remains in ready/ as a placeholder~~ (N/A — ADR is ACCEPTED)
