# Task ID

TASK-MONO-443

# TASK-MONO-443 — the task-ID allocation rule has no mechanical check, and two collisions have landed

- **Type**: TASK-MONO (CI guard)
- **Status**: ready
- **Scope**: `scripts/`, `.github/workflows/ci.yml`, `tasks/INDEX.md`
- **Analysis model**: Opus 4.8 · **Impl model**: Sonnet (small script + one CI job; the judgement is in the predicate, and it is specified below)

## Goal

`tasks/INDEX.md` § Task ID Allocation is a well-written optimistic scheme that even names this
failure and prescribes the remedy (rule 4: *"the branch already merged to `main` holds the number;
the not-yet-merged one renumbers before merge"*). **It is enforced by nothing.** Rule 4 only works
if somebody notices before the second branch merges — and twice, nobody did.

Add the cheap mechanical check the scheme was always missing: fail CI when two task files in the
same ID namespace declare the same ID.

## Evidence (measured 2026-07-20, at `main` = `32f476d2d`)

Stated precisely, because the distinction matters for whether this is worth building:

| | what happened | caught? |
|---|---|---|
| `TASK-MONO-415` | two unrelated tasks (`shared-exception-handler-turns-404-into-500`, `the-decision-site-that-does-not-exist`), both declaring `TASK-MONO-415` in-body, **both in `tasks/done/`** | ❌ landed, still present |
| `TASK-MONO-435` | `promote-d2-cross-project-code-identity` (done, PR #2734) vs `guard-u-single-project-reachability` (filed by PR #2731) | ❌ landed; renumbered to **442** afterwards by TASK-MONO-443's own PR |
| `TASK-PC-FE-250` | a second claim on an ID already held by a merged task referenced from ~30 files | ✅ **caught before merge** and renumbered to 251 — rule 4 working as designed |

⇒ **2 landed, 1 near-miss.** The near-miss is the important row: it shows the discipline *can*
work, and that the gap is detection latency, not the rule.

The deferral note under § Task ID Allocation says a hard fix is *"deferred unless the collision
rate justifies it."* **That is the trigger, and it has now fired.** Note this task does **not**
propose the hard fix that note contemplates (a reservation registry, or non-sequential
timestamp/random IDs) — those trade away the readable sequential convention. A detector is enough:
it converts a silent landing into a red check, after which existing rule 4 handles it.

## Scope / Acceptance Criteria

- **AC-0 (recount before building)** — re-derive the collision list from the tree at implementation
  time; do **not** inherit the table above. It was measured at one commit and the tree moves. If
  the count has changed, say so and re-judge whether the guard is still warranted.

- **AC-1 (predicate)** — the check groups task files by `(namespace, number)` parsed from the
  **in-body `# Task ID` declaration**, not the filename, and fails when a group has ≥ 2 members.
  - The body is authoritative because the filename is a slug that drifts; a rename would otherwise
    hide a collision from the guard that exists to find it.
  - **Suffixed sub-tasks are legitimate and must not fail**: `TASK-MONO-023a` … `023e`,
    `TASK-MONO-044a` … `044f`, `TASK-MONO-046-1` … `046-8a` are real families in `done/`. The
    namespace+number key must treat `023a` as distinct from `023`.
  - **Filename cross-references must not fail**: `TASK-MONO-016-fix-TASK-MONO-015.md` names another
    ticket in its own filename. A filename-based predicate reports this as a duplicate `015`; a
    body-based one does not. This is the concrete reason for the AC-1 first bullet.
  - Cover the root namespace (`MONO`) **and** each project namespace (`PC-FE`, `BE`, `FIN-BE`,
    `ERP-BE`, `SCM-BE`, `FAN-BE`, …). Derive the namespace list from the tree, do not hardcode it
    (§ G7).

- **AC-2 (pre-existing collisions)** — `TASK-MONO-415`'s two files are both in `done/` and are
  referenced by other documents. **Do not rename historical `done/` files to make the guard pass** —
  that edits closed history to satisfy a new check, and breaks live references. Choose one and state
  why in the PR:
  - (a) an explicitly enumerated, dated allowlist entry for `MONO-415` with the reason (§ G2:
    allowlists are for deliberate deviations — record *why*, and note that this one does not "go
    away", it is frozen history); **or**
  - (b) scope the guard to `ready/` + `review/` only, and state plainly that `done/` is unguarded
    and why that is acceptable (§ G8 — write down what the guard does not cover).

- **AC-3 (trigger reachability, § G1)** — this defect **arrives as a markdown-only diff.** A new
  task file is `.md`, and nothing else. Therefore the job must **not** be ANDed with the
  `code-changed` filter — that is precisely the MONO-389 failure mode, where a guard was gated off
  on exactly the change it policed. Path-trigger on `tasks/**` and `projects/*/tasks/**`.
  **Verify this on the runner, not by reading the YAML**: a task-file-only PR must actually run the
  job. Confirm from the run, since a skipped job reports green.

- **AC-4 (prove it bites, § G3)** — mutation: add a temporary second file declaring an existing ID,
  confirm RED; remove it, confirm GREEN. **Print the mutation's effect before reading the result** —
  a silently unapplied mutation is indistinguishable from a guard that does not bite. Test
  symmetrically: also confirm the suffixed families and the `016-fix-TASK-MONO-015` filename stay
  GREEN, so the fix is not just "no false positives" achieved by switching the guard off.

- **AC-5 (point the rule at its enforcement)** — add one line to `tasks/INDEX.md` § Task ID
  Allocation naming the script, so a reader of the rule learns it is now checked. Do not restate the
  predicate there.

## Edge Cases / Failure Scenarios

- **The guard cannot see an unpushed claim.** It closes the *post-merge* hole only — two sessions
  can still both pick 444 and the second finds out at PR time instead of never. That is the intended
  scope and AC-2(b)/§ G8 should say so; do not oversell it as making collisions impossible.
- **A collision between a `ready/` file and a `done/` file is the dangerous direction** (that is
  both 415 and 435), so a guard scoped to `ready/` + `review/` *alone* must still read `done/` as
  part of the population — it just does not have to fail on two `done/` files colliding with each
  other. State which of the two AC-2 options this implies.
- **Do not renumber anything as part of this task.** 435→442 is already done. If AC-0's recount
  surfaces a *new* collision, file it separately — a guard task that also mutates the population it
  guards cannot demonstrate that it found anything.

## Related

- `tasks/INDEX.md` § Task ID Allocation (concurrency-safe) — rules 1–4 and the deferral note this
  task's trigger comes from.
- `platform/testing-strategy.md` § CI Guards / Drift Detectors — G1 (arrival path), G2 (false
  positives / allowlists), G3 (mutation), G7 (derive the population), G8 (write down the hole).
- Precedent for the renumber remedy: MONO-170 → 172; TASK-PC-FE-250 → 251.
