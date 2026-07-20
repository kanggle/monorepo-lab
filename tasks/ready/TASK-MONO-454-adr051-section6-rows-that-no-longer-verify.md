# TASK-MONO-454 — ADR-MONO-051 §6 has two verification rows that no longer verify, and the ADR already documents one of them

**Status:** ready

**Type:** TASK-MONO
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (one citation repair is mechanical; the second row needs a wording judgment)

> Surfaced by [`TASK-MONO-447`](../done/TASK-MONO-447-adr051-project-count-and-verification-pointer.md)'s **AC-4**, which
> required re-running §6's checks row by row after fixing one of them. 447 fixed the `id map` row and reported these
> two as out of scope. Third ticket from the same 2026-07-20 ADR-MONO-051 audit, after 446 (canon qualifier) and 447.

---

## Goal

`ADR-MONO-051` §6 is a table of verification checks, closed by an instruction (`:198`):

> Re-run these before citing this ADR as current. Per repo practice, a prior count is a hypothesis, not a source —
> recount rather than inherit.

Two of its seven rows fail when a reader actually follows that instruction.

### ① The extraction row cites a `TEMPLATE.md` heading that does not exist — and the ADR says so elsewhere in the same file

`:196`:

> \| A hub would break extraction \| per-contract "Standalone-publish degradation" clauses; `TEMPLATE.md` § Discovery → Distribution \|

`§1.4` (`:65`) cites the same non-existent section a second time.

Measured 2026-07-20 against `TEMPLATE.md` on `main` (`5bf255359`): **`## Discovery → Distribution` is not a heading.**
The string occurs twice — in the preamble (`:3`, prose describing the repo's strategy) and as the sub-step
`#### 4. Reconcile shared-layer duplicates (Discovery → Distribution)` (`:383`).

**This is worse than a dangling pointer.** A reader following the name literally lands on `:383`, which is a real
section about reconciling duplicates during a standalone-repo *import* — not about architectural proposals at all.
The citation resolves to a plausible, confident, wrong destination.

**And the ADR already knows.** Two places in the same file record it:

- `§7` (`:213`) — "The section this ADR originally named — § Discovery → Distribution — **does not exist as a heading**
  in `TEMPLATE.md` … TASK-MONO-437 selected a concrete anchor and recorded the reasoning rather than following the
  name literally into the wrong subsection."
- the `**Related:**` header line (`:8`) — corrected to `§ Cross-Project Runtime Coupling (Extraction Constraint)`, and
  it explicitly notes that "§1.4 and §6 below still cite the section by the name this ADR originally assumed, which
  never existed as a heading — see §7".

So the defect is not that nobody noticed. It is that **noticing was recorded instead of fixed**, in a table whose own
closing line tells readers to re-run it. This is the repo's "a rule that lives in one place is a rule that isn't
enforced" shape, inverted: the correction lives in three places as prose and in zero places as the citation itself.

The real destination exists: `TEMPLATE.md` `## Cross-Project Runtime Coupling (Extraction Constraint)` (`:75`), where
`TASK-MONO-437` promoted D6.

### ② The `mdm` row's literal check no longer returns 0 — decision required

`:190`:

> \| No MDM component exists \| repo-wide search for `mdm` / `master data management` → 0 hits \|

Measured 2026-07-20: the search returns **15 hits**, none of which is an MDM component.

- 4 are substring false positives inside `createNetworkCmdModifier` (Testcontainers) — `Cmd` + `Modifier` spans `mdm`.
- The rest are documents naming *this ADR* and its tasks (`docs/adr/INDEX.md`, root `tasks/INDEX.md`,
  `tasks/done/TASK-MONO-433`, `TASK-MONO-434`).

**The claim is still true; the check that was supposed to demonstrate it is not.** A reader following `:198` gets a
failure and must decide for themselves whether the decision drifted — which is exactly the state §6 exists to prevent.
The row is self-defeating in a specific way: **writing the ADR is what broke its own check**, and it will keep breaking
it, because every future ticket that cites the ADR adds another hit.

## Scope

**In scope:**

1. `docs/adr/ADR-MONO-051-master-data-stays-federated.md` `:196` and `:65` — repoint both to the section that exists.
2. Same file `:190` — make the `mdm` row's check express what it actually verifies, or record why a literal failure is
   acceptable. Either outcome is fine; leaving it as-is with no note is not (AC-3).

**Out of scope:**

- `TEMPLATE.md`. The heading it has is the correct one; the ADR is the stale side. Do not rename a heading to match a
  stale citation — that is the inversion `TASK-MONO-447` § F3 guarded against, and renaming would break `:8` and `:211`,
  which already cite the real name.
- Re-opening D6 or any other decision. Both items are citation/verification hygiene.
- The other five §6 rows. 447 re-ran them and all five passed; AC-1 re-runs them again rather than inheriting that.
- Auditing other ADRs for stale §-name citations. Its own task with its own population count if wanted.

## Acceptance Criteria

- **AC-0 (gate — re-measure; the file wins)** — Do not inherit this ticket's line numbers or its "15 hits". Re-read
  `TEMPLATE.md`'s heading list and confirm `Discovery → Distribution` is still not a heading; re-run the `mdm` search
  and report what you get. Sanity-check that search against a known-positive term so a changed count is not a broken
  pattern. **If either has already been fixed, STOP and report.**
- **AC-1 (all occurrences, and re-run the rest)** — Grep the whole ADR for `Discovery → Distribution`; fix **every**
  citation occurrence, not only `:196` and `:65`. Leave `§7` `:213` and `**Related:**` `:8` intact — those *describe*
  the wrong name deliberately and are correct as written. Then re-run all seven §6 rows and report each; a row that
  passed for 447 is a hypothesis, not a source.
- **AC-2 (the destination must exist)** — After editing, **read the cited heading in `TEMPLATE.md`** and confirm the
  text is there. 447's §6 row was repointed by reading the destination; the defect being fixed here was created by not
  doing that.
- **AC-3 (the `mdm` row must end in a state a reader can act on)** — Either (a) reword the check so it distinguishes an
  MDM *component* from documents *mentioning* one (e.g. scope it to source/config paths, or state the expected non-zero
  hits and what they are), or (b) keep the literal check and add a one-line note recording the known benign hits and
  why. State which you chose and why in the PR body. **A silent leave-as-is fails this AC** — the row's whole purpose
  is to be re-run.
- **AC-4** — `docs/adr/INDEX.md`'s ADR-MONO-051 row still matches the file header and
  `scripts/check-adr-index-drift.sh` still passes. Status and Date must not change (this is not a decision change).
- **AC-5** — No `projects/**`, no `platform/**`, and no `TEMPLATE.md` modification.

## Related Specs

- `docs/adr/ADR-MONO-051-master-data-stays-federated.md` §1.4, §6, §7 (the file being corrected)
- `TEMPLATE.md` § Cross-Project Runtime Coupling (Extraction Constraint) (the real destination; read-only here)
- `tasks/done/TASK-MONO-447-adr051-project-count-and-verification-pointer.md` (the AC-4 re-run that surfaced both items)
- `tasks/done/TASK-MONO-437-*` (the D6 promotion that chose the real anchor and recorded why)

## Related Contracts

- None.

## Edge Cases

- **`§7` and `**Related:**` must keep the old name.** Both quote it deliberately — one to explain that it never
  existed, the other to warn that §1.4/§6 still use it. A mechanical find-and-replace across the file would destroy the
  record of *why* the anchor was chosen, which is the most useful thing in §7. Fix citations, not descriptions.
- **`:8`'s warning becomes stale the moment this lands.** It says "§1.4 and §6 below still cite the section by the name
  this ADR originally assumed". After the fix that sentence is false. Update it in the same commit or the ticket has
  moved the stale sentence rather than removed it — the same failure mode `TASK-BE-532` § F3 names.
- **The `mdm` count will keep growing.** Any future ticket citing this ADR adds a hit. Option (b) in AC-3 therefore
  buys less than it looks: a note listing today's benign hits is itself a count that rots. Prefer (a) unless there is a
  reason not to.

## Failure Scenarios

- **F1 — renaming the `TEMPLATE.md` heading to match the stale citation.** Canon is the correct side. Guarded by
  § Scope and AC-5.
- **F2 — repointing to another wrong anchor.** Editing a citation without opening the destination is precisely how both
  this defect and the one 447 fixed were created. Guarded by AC-2.
- **F3 — fixing the citation and leaving `:8`'s now-false warning.** Guarded by § Edge Cases.
- **F4 — treating the `mdm` row as cosmetic and skipping it.** A verification table whose closing line orders a re-run
  is not decoration; a row that always fails trains readers to skip the table. Guarded by AC-3.
