# Task ID

TASK-MONO-340

# Title

ADR-MONO-047 PROPOSED → ACCEPTED transition (doc-only governance flip) + § 4 execution roadmap UNPAUSE + roadmap task spawn (BE-490/491/492/493 + PC-FE-237)

# Status

ready

# Owner

architecture

# Task Tags

- docs
- adr

---

# Dependency Markers

- **prerequisite (선행)**: `tasks/done/TASK-MONO-337-adr-047-org-node-tenant-hierarchy-proposed.md` — the PROPOSED record, merged as PR #2359 squash `ff649b7a8`, closed by #2360.
- **gated by (전제)**: user-explicit intent gate. Satisfied 2026-07-10 by the exact-form intent **"ADR-047 ACCEPTED + 구현까지"**. Self-ACCEPT is prohibited; this task may not be authored-and-accepted by an agent on its own initiative.
- **unpauses (후속)**: `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` § 4 execution roadmap → spawns `TASK-BE-490` → `TASK-BE-491` → `TASK-BE-492` → `TASK-PC-FE-237` → `TASK-BE-493`.

---

# Goal

Flip `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` from `PROPOSED` to `ACCEPTED` — **finalise, do not re-decide** — so that its § 4 execution roadmap becomes UNPAUSED and the org-node hierarchy can be implemented from a dependency-correct ACCEPTED main.

Simultaneously **spawn the roadmap's execution tasks** into their owning projects' `ready/` queues so that implementation is task-backed from the first commit (HARDSTOP-05): the roadmap named four steps, and step 2 splits across two services (account-service owns `tenants`, therefore `org_node`; admin-service owns the RBAC/admin plane).

**Doc-only.** No schema, no code, no seed change in this task.

---

# Scope

## In Scope

- `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md`:
  - `**Status:** PROPOSED` → `**Status:** ACCEPTED`
  - `**History:**` — append an ` · ACCEPTED 2026-07-10 (TASK-MONO-340 …)` clause. The PROPOSED clause stays byte-unchanged.
  - Top staged banner — `PROPOSED (staged…)` → `ACCEPTED 2026-07-10 (TASK-MONO-340; staged…)`, roadmap `PAUSED` → `UNPAUSED`, explicit "was NOT a self-ACCEPT" note.
  - § 4 heading `(PAUSED until ACCEPT)` → `(UNPAUSED — ACCEPTED 2026-07-10)`; annotate each roadmap step with its allocated task ID.
  - § 4 step 2 — record the **D6 seam placement** as execution *mechanics* (ceiling intersected once at the account-service source; auth-service unchanged; `derive(E ∩ C) = derive(E) ∩ derive(C)` since derivation is per-domain). This documents *where* the already-decided intersection is applied; it does not alter D6.
  - § 5 closing line — replace the "no implementation / self-ACCEPT prohibited" sentence with the ACCEPTED finalisation note.
- Spawn roadmap task files (status `ready`):
  - `projects/iam-platform/tasks/ready/TASK-BE-490-adr047-org-node-specs-contracts.md` (step 1, doc-only)
  - `projects/iam-platform/tasks/ready/TASK-BE-491-adr047-org-node-account-service.md` (step 2a)
  - `projects/iam-platform/tasks/ready/TASK-BE-492-adr047-org-admin-scope-admin-service.md` (step 2b)
  - `projects/platform-console/tasks/ready/TASK-PC-FE-237-adr047-org-hierarchy-console.md` (step 3)
  - `projects/iam-platform/tasks/ready/TASK-BE-493-adr047-org-node-backfill-migration.md` (step 4)
- INDEX rows: root `tasks/INDEX.md`, `projects/iam-platform/tasks/INDEX.md`, `projects/platform-console/tasks/INDEX.md`.

## Out of Scope

- **Any re-decision of D1–D7.** ACCEPTED finalises the CHOSEN-PROPOSED direction byte-unchanged (HARDSTOP-04: a decision-table edit here would be an unrecorded amendment).
- Any DDL / entity / controller / seed / UI change (that is BE-490…PC-FE-237).
- `docs/adr/INDEX.md` backfill — that file is stale from ADR-012a onward (013–047 all unlisted); backfilling 35 rows is a separate doc-gardening concern, not this transition's business.
- `ADR-MONO-003a` § 3 audit-trail row — that table records **D4 OVERRIDE** PRs, and has recorded no ADR status transition since row #18 (2026-05-19). Recent ADR accepts (043/044/045) appended nothing. Not applicable.

---

# Acceptance Criteria

- [ ] **AC-1**: ADR-047 `**Status:** ACCEPTED`; `**History:**` carries both the original PROPOSED clause (byte-unchanged) and an appended ACCEPTED clause naming TASK-MONO-340 + the user-explicit intent phrase.
- [ ] **AC-2**: Decision tables D1–D7 and §§ 1/2/3/5 decision content are **byte-identical** to the merged PROPOSED record (`git diff ff649b7a8 -- docs/adr/ADR-MONO-047-*.md` shows changes ONLY in Status / History / banner / § 4 roadmap framing + task-ID annotations / § 5 closing sentence).
- [ ] **AC-3**: Top banner reads ACCEPTED, roadmap reads UNPAUSED, and states explicitly that the gate was user-opened (not a self-ACCEPT).
- [ ] **AC-4**: § 4 names the allocated task IDs per step (BE-490 / BE-491+BE-492 / PC-FE-237 / BE-493) and records the D6 seam-placement mechanics note.
- [ ] **AC-5**: All five roadmap task files exist in the correct project's `ready/` with `Status: ready` and the full required section set (Goal / Scope / Acceptance Criteria / Related Specs / Related Contracts / Edge Cases / Failure Scenarios).
- [ ] **AC-6**: Three INDEX files carry a row for each spawned task. Doc-only diff — `git diff --stat` touches no `.java`, `.sql`, `.ts`, `.tsx`.

---

# Related Specs

- `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` (the subject)
- `platform/architecture-decision-rule.md` (ADR status lifecycle)
- `tasks/INDEX.md` § "When to Use Root vs Project Tasks" (why the ACCEPT flip is a root task: it touches `docs/adr/` + three projects' task queues)

# Related Contracts

- None (doc-only).

---

# Edge Cases

- **ACCEPTED must not re-open a decision.** If, while flipping, any D1–D7 row looks wrong, STOP and raise a fresh amending ADR — do not edit the table (HARDSTOP-04).
- **Self-ACCEPT.** Without the user's exact-form intent this task must not be executed. The intent is recorded verbatim in the ADR History clause so the gate is auditable after the fact.
- **`docs/adr/INDEX.md` staleness** is pre-existing (stops at 012a); do not "fix" it here — an unrelated 35-row backfill would bury the governance diff.
- **Task spawn without ACCEPT** would be a HARDSTOP-05 inversion (implementing a PAUSED roadmap). The spawn and the flip must land in the same commit.

---

# Failure Scenarios

- A decision table drifts by even one character → HARDSTOP-04 (unrecorded amendment of an ACCEPTED ADR). Guard: AC-2's explicit `git diff` against `ff649b7a8`.
- The roadmap is unpaused but no task exists for a step → the implementer either invents a task mid-flight or implements task-less (HARDSTOP-05). Guard: AC-5.
- A spawned task lands in the wrong lifecycle (`backlog/` or straight to `done/`) → the implement gate silently opens/closes. Guard: AC-5 asserts `ready/` + `Status: ready`.
- The ACCEPTED clause omits the intent phrase → a future audit cannot distinguish a user-gated ACCEPT from a self-ACCEPT. Guard: AC-1/AC-3.
