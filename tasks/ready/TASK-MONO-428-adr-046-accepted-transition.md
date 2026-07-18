# Task ID

TASK-MONO-428

# Title

ADR-MONO-046 PROPOSED → ACCEPTED transition (doc-only governance flip) + § 4 execution roadmap UNPAUSE + roadmap task spawn (BE-519 → BE-520 → PC-FE-250)

# Status

ready

# Owner

architecture

# Task Tags

- docs
- adr

---

# Dependency Markers

- **prerequisite (선행)**: `docs/adr/ADR-MONO-046-operator-group-model.md` PROPOSED record (2026-07-08), already on main.
- **gated by (전제)**: user-explicit intent gate (`platform/architecture-decision-rule.md § The ACCEPTED Gate`). Satisfied 2026-07-19 by the exact-form, ADR-naming intent **"ADR-046 ACCEPT + 에픽 착수"**. Self-ACCEPT is prohibited; an agent may not author-and-accept this on its own initiative.
- **unpauses (후속)**: `docs/adr/ADR-MONO-046-operator-group-model.md` § 4 execution roadmap → spawns `TASK-BE-519` → `TASK-BE-520` → `TASK-PC-FE-250`.

---

# Goal

Flip `docs/adr/ADR-MONO-046-operator-group-model.md` from `PROPOSED` to `ACCEPTED` — **finalise, do not re-decide** — so its § 4 execution roadmap becomes UNPAUSED and the operator-group model can be implemented from a dependency-correct ACCEPTED main.

Simultaneously **spawn the roadmap's execution tasks** into their owning projects' `ready/` queues so implementation is task-backed from the first commit (HARDSTOP-05): the roadmap names three steps — spec (iam-platform), backend (iam-platform, admin-service owns `admin_operators` therefore `operator_group`), frontend (platform-console).

**Doc-only.** No schema, no code, no seed change in this task.

---

# Scope

## In Scope

- `docs/adr/ADR-MONO-046-operator-group-model.md`:
  - `**Status:** PROPOSED` → `**Status:** ACCEPTED`
  - `**History:**` — append an ` · ACCEPTED 2026-07-19 (TASK-MONO-428 …)` clause naming the user-explicit intent phrase. The PROPOSED clause stays byte-unchanged.
  - Top staged banner — `PROPOSED (staged…)` → `ACCEPTED 2026-07-19 (TASK-MONO-428; staged…)`, roadmap `PAUSED` → `UNPAUSED`, explicit "was NOT a self-ACCEPT" note.
  - § 4 heading `(PAUSED until ACCEPT)` → `(UNPAUSED — ACCEPTED 2026-07-19, TASK-MONO-428)`; annotate each roadmap step with its allocated task ID (BE-519 / BE-520 / PC-FE-250).
  - § 5 closing line — replace the "no implementation / self-ACCEPT prohibited" sentence with the ACCEPTED finalisation note.
- Spawn roadmap task files (status `ready`):
  - `projects/iam-platform/tasks/ready/TASK-BE-519-adr046-operator-group-specs-contracts.md` (step 1, doc-only)
  - `projects/iam-platform/tasks/ready/TASK-BE-520-adr046-operator-group-backend.md` (step 2)
  - `projects/platform-console/tasks/ready/TASK-PC-FE-250-adr046-operator-groups-console.md` (step 3)
- INDEX rows: root `tasks/INDEX.md`, `projects/iam-platform/tasks/INDEX.md`, `projects/platform-console/tasks/INDEX.md`.

## Out of Scope

- **Any re-decision of D1–D6.** ACCEPTED finalises the CHOSEN-PROPOSED direction byte-unchanged (HARDSTOP-04: a decision-table edit here would be an unrecorded amendment).
- Any DDL / entity / controller / seed / UI change (that is BE-519 … PC-FE-250).
- `docs/adr/INDEX.md` backfill — pre-existing staleness (stops at ADR-012a); an unrelated backfill would bury the governance diff.

---

# Acceptance Criteria

- [ ] **AC-1**: ADR-046 `**Status:** ACCEPTED`; `**History:**` carries both the original PROPOSED clause (byte-unchanged) and an appended ACCEPTED clause naming TASK-MONO-428 + the user-explicit intent phrase "ADR-046 ACCEPT + 에픽 착수".
- [ ] **AC-2**: Decision tables D1–D6 and §§ 1/2/3/5 decision content are **byte-identical** to the merged PROPOSED record (diff shows changes ONLY in Status / History / banner / § 4 roadmap framing + task-ID annotations / § 5 closing sentence).
- [ ] **AC-3**: Top banner reads ACCEPTED, roadmap reads UNPAUSED, and states explicitly that the gate was user-opened (not a self-ACCEPT).
- [ ] **AC-4**: § 4 names the allocated task IDs per step (BE-519 / BE-520 / PC-FE-250).
- [ ] **AC-5**: All three roadmap task files exist in the correct project's `ready/` with `Status: ready` and the full required section set (Goal / Scope / Acceptance Criteria / Related Specs / Related Contracts / Edge Cases / Failure Scenarios).
- [ ] **AC-6**: Three INDEX files carry a row for each spawned task. Doc-only diff — `git diff --stat` touches no `.java`, `.sql`, `.ts`, `.tsx`.

---

# Related Specs

- `docs/adr/ADR-MONO-046-operator-group-model.md` (the subject)
- `platform/architecture-decision-rule.md § The ACCEPTED Gate` (ADR status lifecycle + what lifts a PAUSE)
- `tasks/INDEX.md` § "When to Use Root vs Project Tasks" (why the ACCEPT flip is a root task: it touches `docs/adr/` + two projects' task queues)

# Related Contracts

- None (doc-only).

---

# Edge Cases

- **ACCEPTED must not re-open a decision.** If, while flipping, any D1–D6 row looks wrong, STOP and raise a fresh amending ADR — do not edit the table (HARDSTOP-04).
- **Self-ACCEPT.** Without the user's exact-form, ADR-naming intent this task must not be executed. The intent is recorded verbatim in the ADR History clause so the gate is auditable after the fact.
- **Task spawn without ACCEPT** would be a HARDSTOP-05 inversion (implementing a PAUSED roadmap). The spawn and the flip must land in the same commit.

---

# Failure Scenarios

- A decision table drifts by even one character → HARDSTOP-04 (unrecorded amendment of an ACCEPTED ADR). Guard: AC-2 byte-identical decision content.
- The roadmap is unpaused but no task exists for a step → the implementer either invents a task mid-flight or implements task-less (HARDSTOP-05). Guard: AC-5.
- A spawned task lands in the wrong lifecycle (`backlog/` or straight to `done/`) → the implement gate silently opens/closes. Guard: AC-5 asserts `ready/` + `Status: ready`.
- The ACCEPTED clause omits the intent phrase → a future audit cannot distinguish a user-gated ACCEPT from a self-ACCEPT. Guard: AC-1/AC-3.
